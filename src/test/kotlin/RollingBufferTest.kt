import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RollingBufferTest {
    lateinit var buffer: RollingBuffer<Int>

    @BeforeEach
    fun setup() {
        buffer = RollingBuffer(3, 5) { it }
    }

    @Test
    fun `should wrap around`() {
        Assertions.assertEquals(0, buffer[0])
        Assertions.assertEquals(listOf(0, 1, 2), buffer.toList())
        Assertions.assertEquals(3, buffer.size)

        repeat(3) { buffer.push(it) }

        Assertions.assertEquals(listOf(1, 2, 0, 1, 2), buffer.toList())
        Assertions.assertEquals(5, buffer.size)

        repeat(50) { buffer.push(it) }
        Assertions.assertEquals(listOf(45, 46, 47, 48, 49), buffer.toList())
    }

    @Test
    fun `should clear`() {
        repeat(50) { buffer.push(it) }
        buffer.clear()
        Assertions.assertEquals(0, buffer.size)
    }

    @Test
    fun `should join to string`() {
        repeat(5) { buffer.push(it) }
        Assertions.assertEquals("01234", buffer.joinToString())
        Assertions.assertEquals("0, 3, 6, 9, 12", buffer.joinToString(", ") { (it * 3).toString() })
    }

    @Test
    fun `should resize`() {
        repeat(5) { buffer.push(it) }
        Assertions.assertEquals(5, buffer.size)

        // Upsize
        buffer.setMaxSize(10)
        Assertions.assertEquals(5, buffer.size)
        Assertions.assertEquals(listOf(0, 1, 2, 3, 4), buffer.toList())
        repeat(10) { buffer.push(it) }

        // Downsize
        val removed = buffer.setMaxSize(3)
        Assertions.assertEquals(3, buffer.size)
        Assertions.assertEquals(listOf(7, 8, 9), buffer.toList())
        Assertions.assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), removed)
    }
}