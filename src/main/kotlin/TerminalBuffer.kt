class TerminalBuffer(val width: Int, val height: Int, scrollback: Int) {
    private val buffer = CharArray(width * height) { ' ' }
    private val scrollback = CharArray(scrollback) { ' ' }
}