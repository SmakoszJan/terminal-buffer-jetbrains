enum class Color {
    DEFAULT, BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
}

data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

data class Cell(val content: Char?, val fgColor: Color, val bgColor: Color, val style: Style)

class TerminalBuffer(val width: Int, val height: Int, scrollback: Int) {
    // Arrays used for performance
    private val screen = Array(width * height) { Cell(null, Color.DEFAULT, Color.DEFAULT, Style()) }
    private val scrollback = Array(scrollback) { "" }
}