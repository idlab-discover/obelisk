package idlab.obelisk.utils.service.utils

fun Map<String, Any>.copyWithUpdate(path: List<String>, newValue: Any): Map<String, Any> {
    return path.firstOrNull()?.let { firstPathEntry ->
        val restOfPath = path.drop(1)
        val updatedValue = if (restOfPath.isNotEmpty()) (this[firstPathEntry] as Map<String, Any>?)?.copyWithUpdate(
            restOfPath,
            newValue
        ) ?: nestedDefaultMap(restOfPath, newValue) else newValue
        this.minus(firstPathEntry).plus(firstPathEntry to updatedValue)
    } ?: this.toMap()
}

private fun nestedDefaultMap(path: List<String>, newValue: Any): Map<String, Any> {
    var result = mapOf(path.last() to newValue)
    for (p in path.dropLast(1).reversed()) {
        result = mapOf(p to result)
    }
    return result
}

fun Map<String, Any>.remove(path: List<String>): Map<String, Any> {
    return path.firstOrNull()?.let { firstPathEntry ->
        val restOfPath = path.drop(1)
        if (restOfPath.isNotEmpty()) {
            val updatedValue = (this[firstPathEntry] as Map<String, Any>?)?.remove(restOfPath)
            if (updatedValue != null) this.copyWithUpdate(listOf(firstPathEntry), updatedValue) else this
        } else {
            this.minus(firstPathEntry)
        }
    } ?: this
}