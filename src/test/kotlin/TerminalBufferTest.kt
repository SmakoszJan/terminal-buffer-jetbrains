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
        Assertions.assertEquals(Cell(), buffer[2, 0])
    }

    @Test
    fun `cursor should stay in bounds`() {
        buffer.cursor = Position(5, 5)
        Assertions.assertEquals(Position(4, 4), buffer.cursor)
        buffer.cursorLeft(2)
        Assertions.assertEquals(Position(2, 4), buffer.cursor)
        buffer.cursorDown()
        Assertions.assertEquals(Position(2, 4), buffer.cursor)
        buffer.cursorUp(3)
        Assertions.assertEquals(Position(2, 1), buffer.cursor)
        buffer.cursorRight(10)
        Assertions.assertEquals(Position(2, 3), buffer.cursor)

        buffer.cursor = Position(-1, -1)
        Assertions.assertEquals(Position(0, 0), buffer.cursor)
    }

    @Test
    fun `should be editable`() {
        buffer.write("Hello world!")
        Assertions.assertEquals('l', buffer[2, 0].content)
        Assertions.assertEquals(Attributes(), buffer[1, 1].attributes)
        Assertions.assertEquals('!', buffer[1, 2].content)
        Assertions.assertEquals(Cell(), buffer[2, 3])
        Assertions.assertEquals(Position(2, 2), buffer.cursor)

        buffer.attributes = Attributes(Color.RED, Color.GREEN, Style(italic = true))
        buffer.write("ABC")
        Assertions.assertEquals('A', buffer[2, 2].content)
        Assertions.assertEquals(Color.RED, buffer[2, 2].attributes.fgColor)

        buffer.attributes = Attributes(bgColor = Color.BLUE)
        buffer.cursor = Position(1, 2)
        buffer.write("X")
        Assertions.assertEquals(Color.BLUE, buffer[1, 2].attributes.bgColor)
        Assertions.assertEquals('d', buffer[0, 2].content)
        Assertions.assertEquals('A', buffer[2, 2].content)
        Assertions.assertEquals(Color.RED, buffer[2, 2].attributes.fgColor)
    }

    @Test
    fun `should read lines correctly`() {
        buffer.write("Hello world!")
        Assertions.assertEquals("Hello\n worl\nd!\n\n\n", buffer.getScreen())
        Assertions.assertEquals("Hello\n worl\nd!\n\n\n", buffer.getAll())
        Assertions.assertEquals("Hello", buffer.getLine(0))
        Assertions.assertEquals("", buffer.getLine(4))
    }

    @Test
    fun `line should be filled`() {
        buffer.write("Hello world!")
        buffer.fillLine('X', 1)
        Assertions.assertEquals("Hello\nXXXXX\nd!\n\n\n", buffer.getScreen())
    }
}