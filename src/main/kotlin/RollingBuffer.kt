class RollingBuffer<T>(size: Int, maxSize: Int, init: (Int) -> T) {
    private var content = MutableList(size) { init(it) }
    private var offset = 0
        set(value) {
            field = value % maxSize
        }
    val size get() = content.size
    private var _maxSize = maxSize
    val maxSize: Int
        get() = _maxSize

    operator fun get(index: Int): T {
        // Error check necessary, as it's theoretically possible to wrap around in a "valid" way
        if (index !in 0..<size) throw IndexOutOfBoundsException()

        val realIndex = index + offset

        return if (realIndex >= size) content[realIndex - size] else content[realIndex]
    }

    fun toList() = content.subList(offset, size) + content.subList(0, offset)

    fun setMaxSize(value: Int): List<T> {
        val newMax = value.coerceAtLeast(0)
        if (newMax < _maxSize) {
            val list = toList()
            content = list.takeLast(newMax).toMutableList()
            offset = 0

            _maxSize = newMax
            return list.take(list.size - content.size)
        } else if (newMax > _maxSize) {
            content = toList().toMutableList()
            offset = 0
            _maxSize = newMax
        }

        return emptyList()
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

    fun <U> map(transform: (T) -> U) = RollingBuffer(size, maxSize) { transform(get(it)) }

    fun joinToString(separator: String = "", transform: (T) -> String = { it.toString() }) =
        buildString {
            var first = true

            for (i in offset..<size) {
                if (!first) append(separator) else first = false
                append(transform(content[i]))
            }

            for (i in 0..<offset) {
                append(separator)
                append(transform(content[i]))
            }
        }
}