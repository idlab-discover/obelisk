package idlab.obelisk.annotations.api

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class OblxType(
    /**
     * Allows specifying if the Type is a root type. For root types, CRUD operations and Update variants will also be generated. For non-root types, only field describer enums are generated.
     */
    val rootType: Boolean = true,
    val uniqueFields: Array<String> = [], // zou combo's moeten ondersteunen..
    /**
     * Allows specifying the default page size (limit) for the query function that is to be generated.
     */
    val defaultPageSize: Int = 25
)

annotation class GenerateStubsFor(val oblxTypes: Array<KClass<*>>)
