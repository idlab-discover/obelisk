package idlab.obelisk.utils.service.instrumentation

import io.micrometer.core.instrument.Tag

fun tags(vararg pairs: Pair<String, String>): Iterable<Tag> {
    return pairs.map { Tag.of(it.first, it.second) }
}

data class TagTemplate(val tagKeys: List<String>) {
    constructor(vararg keys: String) : this(keys.toList())

    fun instantiate(vararg tagValues: String): Iterable<Tag> {
        return tagKeys.zip(tagValues) { k, v ->
            Tag.of(k, v)
        }
    }
}