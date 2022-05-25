import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.idl.RuntimeWiring
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.services.pub.catalog.CatalogService
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties

class ValidateGraphQL {

    companion object {

        private lateinit var catalog: CatalogService
        private val definitionPackages = listOf(
            "idlab.obelisk.definitions",
            "idlab.obelisk.definitions.catalog",
            "idlab.obelisk.definitions.data",
            "idlab.obelisk.services.pub.catalog.impl"
        )
        private const val CONTINUE_ON_ERROR = true
        private val logger = KotlinLogging.logger { }

        @JvmStatic
        @BeforeAll
        fun init() {
            val launcher = OblxLauncher
                .with(
                    OblxBaseModule(),
                    ClickhouseDataStoreModule(),
                    MongoDBMetaStoreModule(),
                    BasicAccessManagerModule(),
                    PulsarModule(),
                    GubernatorRateLimiterModule()
                )

            catalog = launcher.getInstance(CatalogService::class.java)
        }

    }

    @Test
    fun validate() {
        val runtimeWiring = catalog.buildRuntimeWiring()
        val schema = catalog.buildSchema(runtimeWiring)

        val valid = schema.allTypesAsList.filterNot { it.name.startsWith("__") }.map { type ->
            when (type) {
                is GraphQLObjectType -> validateObjectType(type, runtimeWiring)
                is GraphQLInputObjectType -> validateInputObjectType(type, runtimeWiring)
                else -> true
            }
        }.all { it }

        Assertions.assertTrue(valid, "The GraphQL schema is not valid!")
    }

    private fun validateObjectType(type: GraphQLObjectType, runtimeWiring: RuntimeWiring): Boolean {
        return listOf(
            Check(runtimeWiring.dataFetchers.containsKey(type.name), "Type ${type.name} has no resolver!"),
            *type.fieldDefinitions.map {
                Check(
                    checkObjectTypeField(type, runtimeWiring, it.name),
                    "No datafetcher for field ${it.name} in Type ${type.name}"
                )
            }.toTypedArray()
        ).map { it.condition }.all { it }
    }

    private fun checkObjectTypeField(
        type: GraphQLObjectType,
        runtimeWiring: RuntimeWiring,
        fieldName: String
    ): Boolean {
        return runtimeWiring.dataFetchers[type.name]?.containsKey(fieldName) ?: false || getDefinitionClass(type)?.declaredMemberProperties?.any { it.name == fieldName } ?: false
    }

    private fun getDefinitionClass(type: GraphQLObjectType): KClass<*>? {
        return if (type.name.endsWith("Page")) {
            PagedResult::class
        } else if (type.name.endsWith("Response")) {
            Response::class
        } else {
            definitionPackages.mapNotNull { packageName ->
                try {
                    val clazz = Class.forName("${packageName}.${type.name}").kotlin
                    clazz
                } catch (t: Throwable) {
                    null
                }
            }.firstOrNull()
        }
    }

    private fun validateInputObjectType(type: GraphQLInputObjectType, runtimeWiring: RuntimeWiring): Boolean {
        val className = "idlab.obelisk.services.pub.catalog.impl.${type.name}"
        return try {
            val clazz = Class.forName(className).kotlin
            type.fields.map {
                Check(
                    clazz.memberProperties.any { field -> it.name == field.name },
                    "Missing field ${it.name} for InputType ${type.name}!"
                )
            }
                .map { it.condition }.all { it }
        } catch (err: Throwable) {
            logger.warn { "A model class $className for the InputType ${type.name} does not exist (or could not be loaded)!" }
            false
        }
    }

    class Check(val condition: Boolean, private val message: String) {

        init {
            if (!condition) {
                logger.warn { message }
            }
        }
    }

}