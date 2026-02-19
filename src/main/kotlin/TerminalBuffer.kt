import java.text.BreakIterator

enum class Color {
    DEFAULT, BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
}

private enum class CharSize {
    @Suppress("unused")
    EMPTY, NORMAL, WIDE, EXTENSION
}

data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

data class Attributes(
    val fgColor: Color = Color.DEFAULT,
    val bgColor: Color = Color.DEFAULT,
    val style: Style = Style()
)

data class CellInfo(val content: String = "", val attributes: Attributes = Attributes())

/** 32 - codepoint; 1 - direct/indirect; 2 - size (0 = empty, 1 = normal, 2 = wide, 3 = extension after wide);
 * 14 - style (3 used); 8 - fg color (4 used); 8 - bg color (4 used) */
@JvmInline
private value class Cell(val data: Long) {
    companion object {
        val EMPTY = Cell(0)
        const val INDIRECT_BIT = 0b1L shl 31
        const val SIZE_MASK = 0b11L shl 29
        const val FG_COLOR_MASK = 0xFFL shl 8
        const val BG_COLOR_MASK = 0xFFL shl 0
        const val BOLD_BIT = 1L shl 28
        const val ITALIC_BIT = 1L shl 27
        const val UNDERLINE_BIT = 1L shl 26
    }

    constructor(content: Int, indirect: Boolean, size: CharSize, attributes: Attributes) : this(
        (content.toLong() shl 32)
                or (if (indirect) INDIRECT_BIT else 0L)
                or (size.ordinal.toLong() shl 29)
                or (if (attributes.style.bold) BOLD_BIT else 0L)
                or (if (attributes.style.italic) ITALIC_BIT else 0L)
                or (if (attributes.style.underline) UNDERLINE_BIT else 0L)
                or (attributes.fgColor.ordinal.toLong() shl 8)
                or attributes.bgColor.ordinal.toLong()
    )

    val isDirect get() = (data and INDIRECT_BIT) == 0L
    val size get() = CharSize.entries[((data and SIZE_MASK) shr 29).toInt()]
    val codepoint get() = (data shr 32).toInt()
    val fgColor get() = Color.entries[(data and FG_COLOR_MASK shr 8).toInt()]
    val bgColor get() = Color.entries[(data and BG_COLOR_MASK).toInt()]
    val isBold get() = (data and BOLD_BIT) != 0L
    val isItalic get() = (data and ITALIC_BIT) != 0L
    val isUnderline get() = (data and UNDERLINE_BIT) != 0L

    fun getString(graphemes: GraphemeArena, printExtension: Boolean) = if (isDirect) when (size) {
        CharSize.EXTENSION if printExtension -> String(Character.toChars(codepoint))
        CharSize.EMPTY, CharSize.EXTENSION -> ""
        CharSize.NORMAL, CharSize.WIDE -> String(Character.toChars(codepoint))
    } else {
        graphemes[codepoint]
    }

    fun info(graphemes: GraphemeArena) =
        CellInfo(getString(graphemes, true), Attributes(fgColor, bgColor, Style(isBold, isItalic, isUnderline)))
}

@JvmInline
private value class CellArray(private val data: LongArray) {
    constructor(size: Int, init: (Int) -> Cell) : this(LongArray(size) { init(it).data })

    operator fun get(index: Int) = Cell(data[index])
    operator fun set(index: Int, cell: Cell) {
        data[index] = cell.data
    }

    /** Pads with empty cells */
    fun copyOf(size: Int) = CellArray(data.copyOf(size))

    fun joinToString(separator: String = ", ", transform: (Cell) -> String) =
        data.joinToString(separator) { transform(Cell(it)) }
}

// Negative line number refers to scrollback when reading
data class Position(val col: Int, val ln: Int)

private class GraphemeArena {
    private var data = mutableListOf<String>()
    private var strings = mutableMapOf<String, Int>()

    val size get() = data.size

    operator fun get(id: Int) = data[id]

    fun insert(char: String) =
        strings.getOrPut(char) {
            val id = data.size
            data.add(char)
            id
        }

    fun clear() {
        data.clear()
        strings.clear()
    }
}

private class RectBuffer(width: Int, height: Int, initSize: Int) {
    private var buffer = RollingBuffer(initSize, height) { CellArray(width) { Cell.EMPTY } }
    private var _width = width
    var width
        get() = _width
        set(value) {
            _width = value.coerceAtLeast(0)
            buffer = buffer.map { it.copyOf(_width) }
        }
    val height get() = buffer.maxSize
    val lines get() = buffer.size

    operator fun get(pos: Position) = buffer[pos.ln][pos.col]
    operator fun set(pos: Position, cell: Cell) {
        buffer[pos.ln][pos.col] = cell
    }

    /** Returns lines cut off by this */
    fun setHeight(newHeight: Int, fillWithEmpty: Boolean): List<CellArray> {
        val oldHeight = buffer.maxSize
        val ret = buffer.setMaxSize(newHeight)

        if (newHeight > oldHeight && fillWithEmpty) {
            repeat(buffer.maxSize - buffer.size) {
                buffer.push(CellArray(width) { Cell.EMPTY })
            }
        }

        return ret
    }

    fun pushLine(line: CellArray): CellArray? = buffer.push(line)

    fun removeLines() {
        buffer.clear()
    }

    fun getLine(ln: Int, graphemes: GraphemeArena) = buffer[ln].joinToString("") { it.getString(graphemes, false) }

    fun getString(graphemes: GraphemeArena) =
        buffer.joinToString("") { line -> line.joinToString("") { it.getString(graphemes, false) } + "\n" }
}

fun String.graphemes(): Iterator<String> = iterator {
    val boundary = BreakIterator.getCharacterInstance()
    boundary.setText(this@graphemes)

    var start = boundary.first()
    var end = boundary.next()

    while (end != BreakIterator.DONE) {
        yield(substring(start, end))
        start = end
        end = boundary.next()
    }
}

// This is taken almost directly from gemini. It's an approximation of which characters take up two cells
// in terminals. For a proper solution, I believe an external library should be used.
private fun isWide(codepoint: Int): Boolean {
    return (
            (codepoint in 0x1100..0x115F) ||   // Hangul Jamo
                    (codepoint == 0x2329) ||           // Left Angle Bracket
                    (codepoint == 0x232A) ||           // Right Angle Bracket
                    (codepoint in 0x2E80..0x303E) ||   // CJK Radicals / Punctuation
                    (codepoint in 0x3040..0xA4CF) ||   // Hiragana, Katakana, Han, Yi
                    (codepoint in 0xAC00..0xD7A3) ||   // Hangul Syllables
                    (codepoint in 0xF900..0xFAFF) ||   // CJK Compatibility
                    (codepoint in 0xFE10..0xFE19) ||   // Vertical Forms
                    (codepoint in 0xFE30..0xFE6F) ||   // CJK Compatibility Forms
                    (codepoint in 0xFF00..0xFF60) ||   // Fullwidth Forms (The "Fat" Latin chars)
                    (codepoint in 0xFFE0..0xFFE6) ||   // Fullwidth Currency
                    (codepoint in 0x1F300..0x1F64F) || // Emojis (Misc Symbols & Pictographs)
                    (codepoint in 0x1F900..0x1F9FF)    // Supplemental Symbols
            )
}

class TerminalBuffer(width: Int, height: Int, scrollback: Int) {
    private var graphemes = GraphemeArena()
    private val screen = RectBuffer(width, height, height)
    private val scrollbackBuffer = RectBuffer(width, scrollback, 0)
    var width
        get() = screen.width
        set(value) {
            screen.width = value
            scrollbackBuffer.width = value
        }
    var height
        get() = screen.height
        set(value) {
            for (line in screen.setHeight(value, true)) {
                scrollbackBuffer.pushLine(line)
            }
        }
    var scrollback
        get() = scrollbackBuffer.height
        set(value) {
            scrollbackBuffer.setHeight(value, false)
        }
    var cursor = Position(0, 0)
        set(value) {
            field = Position(value.col.coerceIn(0..<width), value.ln.coerceIn(0..<height))
        }
    var attributes = Attributes()
    val endOfScreen get() = Position(width - 1, height - 1)

    operator fun get(pos: Position) = if (pos.ln >= 0) {
        screen[pos].info(graphemes)
    } else {
        scrollbackBuffer[Position(pos.col, scrollbackBuffer.lines + pos.ln)].info(graphemes)
    }

    operator fun get(col: Int, ln: Int) = get(Position(col, ln))

    fun internedCount() = graphemes.size

    fun getScreen() = screen.getString(graphemes)

    fun getAll() = scrollbackBuffer.getString(graphemes) + screen.getString(graphemes)

    fun getLine(ln: Int) =
        if (ln >= 0) screen.getLine(ln, graphemes) else scrollbackBuffer.getLine(ln + scrollbackBuffer.lines, graphemes)

    // Wraps around to the previous line
    fun cursorLeft(by: Int = 1) {
        val newCol = cursor.col - by
        val newLn = cursor.ln + (newCol / width)
        cursor = Position(newCol % width, newLn)
    }

    // Wraps around to the previous line
    fun cursorRight(by: Int = 1) {
        val newCol = cursor.col + by
        val newLn = cursor.ln + (newCol / width)
        cursor = Position(newCol % width, newLn)
    }

    fun cursorUp(by: Int = 1) {
        cursor = Position(cursor.col, cursor.ln - by)
    }

    fun cursorDown(by: Int = 1) {
        cursor = Position(cursor.col, cursor.ln + by)
    }

    /// Move the cursor to the beginning of the current line
    fun cursorHome() {
        cursor = Position(0, cursor.ln)
    }

    /// Move the cursor to the end of the current line
    fun cursorEnd() {
        cursor = Position(endOfScreen.col, cursor.ln)
    }

    fun write(text: String) {
        for (char in text.graphemes()) {
            val codepoints = char.codePoints().toArray()

            if (codepoints.size == 1) {
                val size = if (isWide(codepoints[0])) {
                    while (cursor.col == endOfScreen.col) {
                        screen[cursor] = Cell(0, false, CharSize.EMPTY, attributes)
                        if (cursor == endOfScreen) break
                        cursorRight()
                    }

                    screen[cursor] = Cell(codepoints[0], false, CharSize.WIDE, attributes)
                    cursorRight()
                    CharSize.EXTENSION
                } else CharSize.NORMAL
                screen[cursor] = Cell(codepoints[0], false, size, attributes)
            } else {
                val id = graphemes.insert(char)
                screen[cursor] = Cell(id, true, CharSize.NORMAL, attributes)
            }

            if (cursor == endOfScreen) break
            cursorRight()
        }
    }

    fun insert(text: String) {
        val chars = text.graphemes().asSequence()
            .map {
                val codepoints = it.codePoints().toArray()
                if (codepoints.size == 1) {
                    Cell(
                        codepoints[0],
                        false,
                        if (isWide(codepoints[0])) CharSize.WIDE else CharSize.NORMAL,
                        attributes
                    )
                } else {
                    val id = graphemes.insert(it)
                    Cell(id, true, CharSize.NORMAL, attributes)
                }
            }
            .toCollection(ArrayDeque())

        while (chars.isNotEmpty()) {
            val char = chars.removeFirst()
            if (screen[cursor].size != CharSize.EMPTY && screen[cursor].size != CharSize.EXTENSION) chars.addLast(screen[cursor])
            if (char.size == CharSize.WIDE) {
                if (width == 1) return
                if (cursor == endOfScreen) addEmptyLine(false)
                if (cursor.col == endOfScreen.col) {
                    screen[cursor] = Cell(0, false, CharSize.EMPTY, attributes)
                    cursorRight()
                }

                chars.addFirst(Cell(char.codepoint, false, CharSize.EXTENSION, attributes))
            }
            screen[cursor] = char

            if (cursor == endOfScreen && chars.isNotEmpty()) addEmptyLine(true)

            cursorRight()
        }
    }

    fun fillLine(char: Char, ln: Int) {
        val codepoint = char.code
        if (isWide(codepoint)) {
            for (col in 0..<width step 2) {
                if (col + 1 == width) break
                screen[Position(col, ln)] = Cell(codepoint, false, CharSize.WIDE, attributes)
                screen[Position(col + 1, ln)] = Cell(codepoint, false, CharSize.EXTENSION, attributes)
            }
        } else {
            for (col in 0..<width) {
                screen[Position(col, ln)] = Cell(char.code, false, CharSize.NORMAL, attributes)
            }
        }
    }

    /** `moveCursor` determines whether the cursor should be moved up (follow the text) afterward */
    fun addEmptyLine(moveCursor: Boolean = true) {
        screen.pushLine(CellArray(width) { Cell.EMPTY })?.let {
            scrollbackBuffer.pushLine(it)
        }

        if (moveCursor) cursorUp()
    }

    fun clearScreen() {
        for (ln in 0..<height) {
            for (col in 0..<width) {
                screen[Position(col, ln)] = Cell.EMPTY
            }
        }
    }

    fun clearAll() {
        clearScreen()
        scrollbackBuffer.removeLines()
        graphemes.clear()
    }

    fun reconstruct() {
        val newGraphemes = GraphemeArena()

        for (ln in 0..<screen.height) {
            for (col in 0..<screen.width) {
                val cell = screen[Position(col, ln)]
                if (cell.isDirect) {
                    screen[Position(col, ln)] = cell
                } else {
                    val id = newGraphemes.insert(graphemes[cell.codepoint])
                    screen[Position(col, ln)] = Cell((id.toLong() shl 32) or (cell.data and 0xFFFFFFFF))
                }
            }
        }

        for (ln in 0..<scrollbackBuffer.lines) {
            for (col in 0..<scrollbackBuffer.width) {
                val cell = scrollbackBuffer[Position(col, ln)]
                if (cell.isDirect) {
                    scrollbackBuffer[Position(col, ln)] = cell
                } else {
                    val id = newGraphemes.insert(graphemes[cell.codepoint])
                    scrollbackBuffer[Position(col, ln)] = Cell((id.toLong() shl 32) or (cell.data and 0xFFFFFFFF))
                }
            }
        }

        graphemes = newGraphemes
    }
}