enum class Color {
    DEFAULT, BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
}

data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

data class Attributes(
    val fgColor: Color = Color.DEFAULT,
    val bgColor: Color = Color.DEFAULT,
    val style: Style = Style()
)

data class Cell(val content: Char? = null, val attributes: Attributes = Attributes())

// Negative line number refers to scrollback when reading
data class Position(val col: Int, val ln: Int)

private class RectBuffer(width: Int, height: Int, initSize: Int) {
    private var buffer = RollingBuffer(initSize, height) { Array(width) { Cell() } }
    var _width = width
    var width
        get() = _width
    set(value) {
        _width = value.coerceAtLeast(0)
        buffer = buffer.map { it.copyOf(_width).map { item -> item ?: Cell() }.toTypedArray() }
    }
    val height get() = buffer.maxSize
    val lines get() = buffer.size

    operator fun get(pos: Position) = buffer[pos.ln][pos.col]
    operator fun set(pos: Position, cell: Cell) {
        buffer[pos.ln][pos.col] = cell
    }

    /** Returns lines cut off by this */
    fun setHeight(newHeight: Int, fillWithEmpty: Boolean): List<Array<Cell>> {
        val oldHeight = buffer.maxSize
        val ret = buffer.setMaxSize(newHeight)

        if (newHeight > oldHeight && fillWithEmpty) {
            repeat(buffer.maxSize - buffer.size) {
                buffer.push(Array(width) { Cell() })
            }
        }

        return ret
    }

    fun pushLine(line: Array<Cell>): Array<Cell>? = buffer.push(line)

    fun removeLines() {
        buffer.clear()
    }

    fun getLine(ln: Int) = buffer[ln].joinToString("") { cell -> cell.content?.toString() ?: "" }

    fun getString() =
        buffer.joinToString("") { line -> line.joinToString("") { cell -> cell.content?.toString() ?: "" } + "\n" }
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
        screen[pos]
    } else {
        scrollbackBuffer[Position(pos.col, scrollbackBuffer.lines + pos.ln)]
    }

    operator fun get(col: Int, ln: Int) = get(Position(col, ln))

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
        for (char in text) {
            screen[cursor] = Cell(char, attributes)

            if (cursor == endOfScreen) break
            cursorRight()
        }
    }

    fun insert(text: String) {
        val chars = text
            .map { Cell(it, attributes) }
            .toCollection(ArrayDeque())

        while (chars.isNotEmpty()) {
            val char = chars.removeFirst()
            if (screen[cursor].content != null) chars.addLast(screen[cursor])
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

    /// `moveCursor` determines whether the cursor should be moved up (follow the text) afterwards`
    fun addEmptyLine(moveCursor: Boolean = true) {
        screen.pushLine(Array(width) { Cell() })?.let {
            scrollbackBuffer.pushLine(it)
        }

        if (moveCursor) cursorUp()
    }

    fun clearScreen() {
        for (ln in 0..<height) {
            for (col in 0..<width) {
                screen[Position(col, ln)] = Cell()
            }
        }
    }

    fun clearAll() {
        clearScreen()
        scrollbackBuffer.removeLines()
    }

    fun getScreen() = screen.getString()

    fun getAll() = scrollbackBuffer.getString() + screen.getString()

    fun getLine(ln: Int) = if (ln >= 0) screen.getLine(ln) else scrollbackBuffer.getLine(ln + scrollbackBuffer.lines)
}