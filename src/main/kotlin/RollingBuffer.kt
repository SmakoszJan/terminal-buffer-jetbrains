class RollingBuffer<T>(private val content: Array<T>) {
    private val offset = 0
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
}