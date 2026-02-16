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

private class RectBuffer(val width: Int, val height: Int) {
    private val buffer = RollingBuffer(Array(height) { Array(width) { Cell() } })

    operator fun get(pos: Position) = buffer[pos.ln][pos.col]
    operator fun set(pos: Position, cell: Cell) {
        buffer[pos.ln][pos.col] = cell
    }
}

class TerminalBuffer(val width: Int, val height: Int, val scrollback: Int) {
    // Arrays used for performance
    private val screen = RectBuffer(width, height)
    private val scrollbackBuffer = RectBuffer(width, scrollback)
    var cursor = Position(0, 0)
        set(value) {
            field = Position(value.col.coerceIn(0..<width), value.ln.coerceIn(0..<height))
        }
    var attributes = Attributes()
    val endOfScreen get() = Position(width - 1, height - 1)

    operator fun get(pos: Position) = if (pos.ln >= 0) {
        screen[pos]
    } else {
        scrollbackBuffer[Position(pos.col, scrollback + pos.ln)]
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
            cursorRight()

            if (cursor == endOfScreen) break
        }
    }
}