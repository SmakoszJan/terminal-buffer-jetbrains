import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TerminalBufferTest {
    lateinit var buffer: TerminalBuffer

    @BeforeEach
    fun setup() {
        buffer = TerminalBuffer(5, 5, 3)
    }

    @Test
    fun `should start empty`() {
        Assertions.assertEquals(Cell(), buffer[Position(2, 2)])
        Assertions.assertEquals(Cell(), buffer[Position(2, -1)])
    }

    @Test
    fun `cursor should stay in bounds`() {
        buffer.cursor = Position(5, 5)
        Assertions.assertEquals(Position(4, 4), buffer.cursor)
        buffer.cursorLeft()
        Assertions.assertEquals(Position(3, 4), buffer.cursor)
        buffer.cursorDown()
        Assertions.assertEquals(Position(3, 4), buffer.cursor)
        buffer.cursorUp()
        Assertions.assertEquals(Position(3, 3), buffer.cursor)
        repeat(10) { buffer.cursorRight() }
        Assertions.assertEquals(Position(4, 3), buffer.cursor)

        buffer.cursor = Position(-1, -1)
        Assertions.assertEquals(Position(0, 0), buffer.cursor)
    }
}