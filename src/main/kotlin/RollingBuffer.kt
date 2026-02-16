class RollingBuffer<T>(size: Int, val maxSize: Int, init: (Int) -> T) {
    private val content = MutableList(size) { init(it) }
    private var offset = 0
    val size get() = content.size

    operator fun get(index: Int): T {
        // Error check necessary, as it's theoretically possible to wrap around in a "valid" way
        if (index !in 0..<size) throw IndexOutOfBoundsException()

        val realIndex = index + offset

        return if (realIndex >= size) content[realIndex - size] else content[realIndex]
    }
    operator fun set(index: Int, value: T) {
        // Error check necessary, as it's theoretically possible to wrap around in a "valid" way
        if (index !in 0..<size) throw IndexOutOfBoundsException()

        var realIndex = index + offset
        if (realIndex >= size) realIndex -= size

        content[realIndex] = value
    }

    fun clear() {
        content.clear()
        offset = 0
    }

    fun joinToString(separator: String = "", transform: (T) -> String): String {
        var result = ""

        for (i in offset..<size) {
            result += transform(content[i])
        }

        for (i in 0..<offset) {
            result += transform(content[i])
        }

        return result
    }
}