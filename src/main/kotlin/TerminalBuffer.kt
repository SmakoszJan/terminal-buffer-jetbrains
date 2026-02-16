enum class Color {
    DEFAULT, BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
}

data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

data class Cell(val content: Char? = null, val fgColor: Color = Color.DEFAULT, val bgColor: Color = Color.DEFAULT, val style: Style = Style())

// Negative line number refers to scrollback when reading
data class Position(val col: Int, val ln: Int)

private class RectBuffer(val width: Int, val height: Int) {
    private val buffer = RollingBuffer(Array(width * height) { Cell() })

    operator fun get(pos: Position) = buffer[pos.ln * width + pos.col]
    operator fun set(pos: Position, cell: Cell) { buffer[pos.ln * width + pos.col] = cell }
}

class TerminalBuffer(val width: Int, val height: Int, val scrollback: Int) {
    // Arrays used for performance
    private val screen = RectBuffer(width, height)
    private val scrollbackBuffer = RectBuffer(width, scrollback)

    operator fun get(pos: Position) = if (pos.ln >= 0) {
        screen[pos]
    } else {
        scrollbackBuffer[Position(pos.col, scrollback + pos.ln)]
    }
    operator fun get(col: Int, ln: Int) = get(Position(col, ln))
}