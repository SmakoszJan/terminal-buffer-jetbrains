class RollingBuffer<T>(size: Int, val maxSize: Int, init: (Int) -> T) {
    private val content = MutableList(size) { init(it) }
    private var offset = 0
        set(value) { field = value % maxSize }
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

    fun push(value: T): T? {
        if (size == maxSize) {
            val ret = content[offset]
            content[offset] = value
            offset++
            return ret
        } else {
            content.add(value)
            return null
        }
    }

    fun clear() {
        content.clear()
        offset = 0
    }

    fun joinToString(separator: String = "", transform: (T) -> String) =
        buildString {
            var first = true

            for (i in offset..<size) {
                if (!first) append(separator) else first = false
                append(transform(content[i]))
            }

            for (i in 0..<offset) {
                append(transform(content[i]))
            }
        }
}