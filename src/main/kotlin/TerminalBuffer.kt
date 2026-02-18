enum class Color {
    DEFAULT, BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
}

data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

data class Attributes(
    val fgColor: Color = Color.DEFAULT,
    val bgColor: Color = Color.DEFAULT,
    val style: Style = Style()
)

data class CellInfo(val content: String = "", val attributes: Attributes = Attributes())

/** 32 - codepoint; 3 - size (0 = empty, 1 = normal, 2 = wide, 3 = extension after wide);
 * 13 - style (3 used); 8 - fg color (4 used); 8 - bg color (4 used) */
@JvmInline
private value class Cell(val data: Long) {
    companion object {
        val EMPTY = Cell(0)
        const val SIZE_MASK = 0b11L shl 29
        const val FG_COLOR_MASK = 0xFFL shl 8
        const val BG_COLOR_MASK = 0xFFL shl 0
        const val BOLD_BIT = 1L shl 28
        const val ITALIC_BIT = 1L shl 27
        const val UNDERLINE_BIT = 1L shl 26
    }

    // TODO: Handle graphemes
    constructor(content: Char?, attributes: Attributes) : this(
        ((content?.code ?: 0L).toLong() shl 32)
                or (if (content == null) 0 else 1L shl 29)
                or (if (attributes.style.bold) BOLD_BIT else 0L)
                or (if (attributes.style.italic) ITALIC_BIT else 0L)
                or (if (attributes.style.underline) UNDERLINE_BIT else 0L)
                or (attributes.fgColor.ordinal.toLong() shl 8)
                or attributes.bgColor.ordinal.toLong()
    )

    val size get() = ((data and SIZE_MASK) shr 29).toInt()
    val codepoint get() = (data shr 32).toInt()
    val fgColor get() = Color.entries[(data and FG_COLOR_MASK shr 8).toInt()]
    val bgColor get() = Color.entries[(data and BG_COLOR_MASK).toInt()]
    val isBold get() = (data and BOLD_BIT) != 0L
    val isItalic get() = (data and ITALIC_BIT) != 0L
    val isUnderline get() = (data and UNDERLINE_BIT) != 0L

    // This is temporary. This method will require access to the grapheme arena
    // and as such will not be an override
    override fun toString() = when(size) {
        0, 3 -> ""
        1 -> String(Character.toChars(codepoint))
        2 -> TODO("Wide characters")
        else -> error("Invalid cell size $size")
    }

    fun info() = CellInfo(toString(), Attributes(fgColor, bgColor, Style(isBold, isItalic, isUnderline)))
}

@JvmInline
private value class CellArray(private val data: LongArray) {
    constructor(size: Int, init: (Int) -> Cell) : this(LongArray(size) { init(it).data })

    operator fun get(index: Int) = Cell(data[index])
    operator fun set(index: Int, cell: Cell) { data[index] = cell.data }

    /** Pads with empty cells */
    fun copyOf(size: Int) = CellArray(data.copyOf(size))

    fun joinToString(separator: String = ", ", transform: (Cell) -> String) = data.joinToString(separator) { transform(Cell(it)) }
}

// Negative line number refers to scrollback when reading
data class Position(val col: Int, val ln: Int)

private class RectBuffer(width: Int, height: Int, initSize: Int) {
    private var buffer = RollingBuffer(initSize, height) { CellArray(width) { Cell.EMPTY } }
    var _width = width
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

    fun getLine(ln: Int) = buffer[ln].joinToString("") { it.toString() }

    fun getString() =
        buffer.joinToString("") { line -> line.joinToString("") { it.toString() } + "\n" }
}

class TerminalBuffer(width: Int, height: Int, scrollback: Int) {
    // Arrays used for performance
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
    var scrollback get() = scrollbackBuffer.height
        set(value) { scrollbackBuffer.setHeight(value, false) }
    var cursor = Position(0, 0)
        set(value) {
            field = Position(value.col.coerceIn(0..<width), value.ln.coerceIn(0..<height))
        }
    var attributes = Attributes()
    val endOfScreen get() = Position(width - 1, height - 1)

    operator fun get(pos: Position) = if (pos.ln >= 0) {
        screen[pos].info()
    } else {
        scrollbackBuffer[Position(pos.col, scrollbackBuffer.lines + pos.ln)].info()
    }

    operator fun get(col: Int, ln: Int) = get(Position(col, ln))

    fun getScreen() = screen.getString()

    fun getAll() = scrollbackBuffer.getString() + screen.getString()

    fun getLine(ln: Int) = if (ln >= 0) screen.getLine(ln) else scrollbackBuffer.getLine(ln + scrollbackBuffer.lines)

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

    // TODO: Handle graphemes and wide characters
    fun write(text: String) {
        for (char in text) {
            screen[cursor] = Cell(char, attributes)

            if (cursor == endOfScreen) break
            cursorRight()
        }
    }

    // TODO: Handle graphemes and wide characters
    fun insert(text: String) {
        val chars = text
            .map { Cell(it, attributes) }
            .toCollection(ArrayDeque())

        while (chars.isNotEmpty()) {
            val char = chars.removeFirst()
            if (screen[cursor].size != 0) chars.addLast(screen[cursor])
            screen[cursor] = char

            if (cursor == endOfScreen && chars.isNotEmpty()) addEmptyLine(true)

            cursorRight()
        }
    }

    fun fillLine(char: Char, ln: Int) {
        for (col in 0..<width) {
            screen[Position(col, ln)] = Cell(char, attributes)
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
    }
}