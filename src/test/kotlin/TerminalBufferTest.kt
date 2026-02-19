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
        Assertions.assertEquals(CellInfo(), buffer[Position(2, 2)])
        Assertions.assertEquals(CellInfo(), buffer[2, 0])
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
        buffer.cursorLeft()
        Assertions.assertEquals(Position(1, 3), buffer.cursor)
        buffer.cursorHome()
        Assertions.assertEquals(Position(0, 3), buffer.cursor)
        buffer.cursorEnd()
        Assertions.assertEquals(Position(4, 3), buffer.cursor)

        buffer.cursor = Position(-1, -1)
        Assertions.assertEquals(Position(0, 0), buffer.cursor)
    }

    @Test
    fun `should be editable`() {
        buffer.write("Hello world!")
        Assertions.assertEquals("l", buffer[2, 0].content)
        Assertions.assertEquals(Attributes(), buffer[1, 1].attributes)
        Assertions.assertEquals("!", buffer[1, 2].content)
        Assertions.assertEquals(CellInfo(), buffer[2, 3])
        Assertions.assertEquals(Position(2, 2), buffer.cursor)

        buffer.attributes = Attributes(Color.RED, Color.GREEN, Style(italic = true))
        buffer.write("ABC")
        Assertions.assertEquals("A", buffer[2, 2].content)
        Assertions.assertEquals(Color.RED, buffer[2, 2].attributes.fgColor)

        buffer.attributes = Attributes(bgColor = Color.BLUE)
        buffer.cursor = Position(1, 2)
        buffer.write("X")
        Assertions.assertEquals(Color.BLUE, buffer[1, 2].attributes.bgColor)
        Assertions.assertEquals("d", buffer[0, 2].content)
        Assertions.assertEquals("A", buffer[2, 2].content)
        Assertions.assertEquals(Color.RED, buffer[2, 2].attributes.fgColor)
    }

    @Test
    fun `should read lines correctly`() {
        buffer.write("Hello world!")
        Assertions.assertEquals("Hello\n worl\nd!\n\n\n", buffer.getScreen())
        Assertions.assertEquals("Hello\n worl\nd!\n\n\n", buffer.getAll())
        Assertions.assertEquals("Hello", buffer.getLine(0))
        Assertions.assertEquals("", buffer.getLine(4))

        buffer.addEmptyLine()
        Assertions.assertEquals(" worl\nd!\n\n\n\n", buffer.getScreen())
        Assertions.assertEquals("Hello\n worl\nd!\n\n\n\n", buffer.getAll())
        Assertions.assertEquals("Hello", buffer.getLine(-1))
    }

    @Test
    fun `line should be filled`() {
        buffer.write("Hello world!")
        buffer.fillLine('X', 1)
        Assertions.assertEquals("Hello\nXXXXX\nd!\n\n\n", buffer.getScreen())

        buffer.clearScreen()
        Assertions.assertEquals("\n\n\n\n\n", buffer.getScreen())

        buffer.cursor = Position(0, 0)
        buffer.write("Hello world!")
        Assertions.assertEquals("Hello", buffer.getLine(0))
        Assertions.assertEquals("", buffer.getLine(3))

        buffer.cursor = Position(0, 4)
        buffer.write("AAAAA")
        Assertions.assertEquals("AAAAA", buffer.getLine(4))
        buffer.addEmptyLine()
        Assertions.assertEquals(Position(4, 3), buffer.cursor)
        Assertions.assertEquals("AAAAA", buffer.getLine(3))
        Assertions.assertEquals("", buffer.getLine(4))

        Assertions.assertEquals("Hello", buffer.getLine(-1))
        buffer.addEmptyLine(false)
        Assertions.assertEquals(Position(4, 3), buffer.cursor)
        buffer.clearAll()
        Assertions.assertEquals("\n\n\n\n\n", buffer.getAll())
    }

    @Test
    fun `should insert simple text`() {
        buffer.insert("Hello world!")
        buffer.attributes = Attributes(fgColor = Color.RED)
        buffer.fillLine('X', 4)
        Assertions.assertEquals("Hello\n worl\nd!\n\nXXXXX\n", buffer.getScreen())

        buffer.cursor = Position(2, 0)
        buffer.insert("ABC")
        Assertions.assertEquals("HeABC\nllo w\norld!\n\nXXXXX\n", buffer.getScreen())
        Assertions.assertEquals(Color.RED, buffer[3, 0].attributes.fgColor)
        Assertions.assertEquals(Color.DEFAULT, buffer[0, 1].attributes.fgColor)

        buffer.clearScreen()
        buffer.cursor = Position(0, 0)
        buffer.write("AAAAA")
        buffer.write("BBBBB")
        buffer.write("CCCCC")
        buffer.write("DDDDD")
        buffer.write("EEEEE")
        buffer.cursor = Position(0, 2)

        buffer.insert("12345")
        Assertions.assertEquals("BBBBB\n12345\nCCCCC\nDDDDD\nEEEEE\n", buffer.getScreen())
        Assertions.assertEquals("AAAAA\nBBBBB\n12345\nCCCCC\nDDDDD\nEEEEE\n", buffer.getAll())
    }

    @Test
    fun `should resize scrollback`() {
        buffer.fillLine('X', 0)
        buffer.addEmptyLine()

        Assertions.assertEquals("XXXXX\n\n\n\n\n\n", buffer.getAll())
        buffer.scrollback = 5
        repeat(4) { buffer.addEmptyLine() }
        Assertions.assertEquals("XXXXX\n\n\n\n\n\n\n\n\n\n", buffer.getAll())
        buffer.scrollback = 2
        Assertions.assertEquals("\n\n\n\n\n\n\n", buffer.getAll())
    }

    @Test
    fun `should resize screen`() {
        buffer.fillLine('X', 0)

        Assertions.assertEquals("XXXXX\n\n\n\n\n", buffer.getScreen())
        buffer.height = 7
        Assertions.assertEquals("XXXXX\n\n\n\n\n\n\n", buffer.getScreen())
        buffer.height = 6
        Assertions.assertEquals("\n\n\n\n\n\n", buffer.getScreen())
        Assertions.assertEquals("XXXXX\n\n\n\n\n\n\n", buffer.getAll())
        buffer.height = 7
        Assertions.assertEquals("\n\n\n\n\n\n\n", buffer.getScreen())

        buffer.width = 10
        buffer.fillLine('A', 2)
        Assertions.assertEquals("XXXXX\n\n\nAAAAAAAAAA\n\n\n\n\n", buffer.getAll())
        buffer.width = 2
        Assertions.assertEquals("XX\n\n\nAA\n\n\n\n\n", buffer.getAll())
    }

    @Test
    fun `should handle grapheme clusters`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        val scotland = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F"

        @Suppress("LocalVariableName")
        val a_ = "\u0104"

        @Suppress("LocalVariableName")
        val a_2 = "A\u0308"
        // ĄÄ<family emoji>
        buffer.write("$a_$a_2$family")
        Assertions.assertEquals("$a_$a_2$family", buffer.getLine(0))
        Assertions.assertEquals(Position(3, 0), buffer.cursor)

        buffer.cursorLeft(2)
        buffer.insert(scotland)
        Assertions.assertEquals("$a_$scotland$a_2$family", buffer.getLine(0))
    }

    @Test
    fun `should handle wide characters`() {
        buffer.write("\uFF21AAA")
        Assertions.assertEquals("\uFF21AAA", buffer.getLine(0))
        Assertions.assertEquals(Position(0, 1), buffer.cursor)

        buffer.attributes = Attributes(bgColor = Color.RED)
        buffer.cursor = Position(0, 0)
        buffer.insert("\u3042xx\u30A2")
        Assertions.assertEquals("\u3042xx", buffer.getLine(0))
        Assertions.assertEquals(Color.RED, buffer[4, 0].attributes.bgColor)
        Assertions.assertEquals("\u30A2\uFF21A", buffer.getLine(1))

        buffer.fillLine('\uFF21', 4)
        Assertions.assertEquals("\uFF21\uFF21", buffer.getLine(4))
    }

    @Test
    fun `should abort insertion if screen too narrow`() {
        buffer.width = 1
        buffer.insert("A\uFF21")
        Assertions.assertEquals("A\n\n\n\n\n", buffer.getScreen())
    }

    @Test
    fun `should reconstruct`() {
        buffer.scrollback = 1
        buffer.write("HellA\u0308 world!")
        buffer.addEmptyLine()
        Assertions.assertEquals("HellA\u0308\n worl\nd!\n\n\n\n", buffer.getAll())
        buffer.cursor = Position(0, 4)
        buffer.write("e\u0301e\u0301")
        Assertions.assertEquals(2, buffer.internedCount())
        buffer.addEmptyLine()
        Assertions.assertEquals(2, buffer.internedCount())
        buffer.reconstruct()
        Assertions.assertEquals(" worl\nd!\n\n\ne\u0301e\u0301\n\n", buffer.getAll())
        Assertions.assertEquals(1, buffer.internedCount())
    }

    @Test
    fun `line feed should go to next line`() {
        buffer.write("Hello\nworld!", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hello\n\nworld\n!\n\n", buffer.getScreen())
        buffer.cursor = Position(0, 4)
        buffer.write("AA\nAA", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hello\n\nworld\n!\nAA\n", buffer.getScreen())
        buffer.cursor = Position(1, 0)
        buffer.write("\n", ControlCharBehavior.PRINT)
        Assertions.assertEquals("H\nllo\n\nworld\n!\nAA\n", buffer.getScreen())
        buffer.write("\r\n", ControlCharBehavior.IGNORE)
        Assertions.assertEquals("H\nllo\n\nworld\n!\nAA\n", buffer.getScreen())

        buffer.clearAll()
        buffer.cursor = Position(0, 0)

        buffer.write("Hello")
        buffer.insert("A\nA", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hello\nA\nA\n\n\n", buffer.getScreen())
        buffer.cursor = Position(2, 0)
        buffer.insert("\n", ControlCharBehavior.PRINT)
        Assertions.assertEquals("He\nll\noA\nA\n\n\n", buffer.getScreen())
        buffer.cursor = Position(2, 0)
        // Interpreting should only affect control chars in the input
        buffer.insert("eee", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Heeee\n\nlloA\nA\n\n\n", buffer.getScreen())
        buffer.cursor = Position(2, 0)
        buffer.insert("\nx", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Heeee\nx\nllo\nAA\n\n\n", buffer.getScreen())
    }

    @Test
    fun `carriage return should go to start of line`() {
        buffer.write("Hell\rworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("world\n\n\n\n\n", buffer.getScreen())
    }

    @Test
    fun `should go left on backspace`() {
        buffer.write("Hello\bworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hellw\norld\n\n\n\n", buffer.getScreen())

        buffer.clearAll()
        buffer.cursor = Position(0, 0)

        buffer.insert("Hello\bworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hellw\norldo\n\n\n\n", buffer.getScreen())
    }

    @Test
    fun `should clear on form feed`() {
        buffer.write("Hello\u000Cworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("world\n\n\n\n\n", buffer.getAll())

        buffer.clearAll()
        buffer.scrollback = 5
        buffer.cursor = Position(0, 0)

        buffer.insert("Hello\u000Cworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hello\n\n\n\n\nworld\n\n\n\n\n", buffer.getAll())
    }

    @Test
    fun `should move cursor on tab`() {
        buffer.write("Hello\tworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("Hello\n\nworld\n\n\n", buffer.getAll())

        buffer.clearAll()
        buffer.cursor = Position(0, 0)
        buffer.width = 10
        buffer.write("Hello\tworld", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("w", buffer[8, 0].content)

        buffer.cursor = Position(0, 0)
        buffer.insert("\ta", ControlCharBehavior.INTERPRET)
        Assertions.assertEquals("a", buffer[8, 0].content)
        Assertions.assertEquals("w", buffer[9, 0].content)
        Assertions.assertEquals("Helloaw", buffer.getLine(0))
    }

    @Test
    fun `motion should respect character width`() {
        buffer.write("A\uFF21")
        buffer.cursor = Position(0, 0)
        buffer.cursorRight(2)
        Assertions.assertEquals(Position(3, 0), buffer.cursor)
        buffer.cursor = Position(2, 0)
        Assertions.assertEquals(Position(1, 0), buffer.cursor)
    }
}