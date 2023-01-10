package idlab.obelisk.services.pub.catalog.impl

import graphql.Scalars
import graphql.language.ArrayValue
import graphql.language.ListType
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.schema.*
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import idlab.obelisk.definitions.*
import io.reactivex.Single
import java.security.InvalidParameterException
import java.util.concurrent.ConcurrentHashMap
import io.reactivex.rxkotlin.toFlowable

internal class FilterDirective : SchemaDirectiveWiring {

    companion object {

        const val SEARCHABLE = "searchable"
        const val ENABLE_FILTER = "enable_filter"
        const val PATH_ARG = "path"
        const val ID_CONTAINER_ARG = "idContainer"

        val generatedTypes: MutableMap<String, TypeFilter> = ConcurrentHashMap()

        fun generateFilterType(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>): GraphQLInputType {
            val field = environment!!.element
            val targetType = extractResultType(field)

            if (!generatedTypes.containsKey(targetType.name)) {
                generatedTypes[targetType.name] = TypeFilter(targetType)
            }
            return generatedTypes[targetType.name]!!.getType()
        }

        fun extractResultType(field: GraphQLFieldDefinition): GraphQLObjectType {
            try {
                return PagedDirective.visitedObjectTypes[((field.definition?.type as ListType).type as TypeName).name]!!
            } catch (t: Throwable) {
                throw RuntimeException("Cannot extract result type for field ${field.definition?.name}...", t)
            }
        }

    }

    override fun onField(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>?): GraphQLFieldDefinition {
        val field = environment!!.element
        try {
            return field.transform { builder ->
                val filter = generateFilterType(environment)
                builder.argument(
                    GraphQLArgument.newArgument().name("filter").type(filter).build()
                )
            }
        } catch (t: Throwable) {
            throw IllegalArgumentException(
                "Error while generating filter for ${environment.fieldsContainer.name}->${environment.fieldDefinition.name}",
                t
            )
        }
    }
}

typealias SubFilterResolverContext = Map<List<String>, (FilterExpression) -> Single<Set<String>>>

internal fun DataFetchingEnvironment.getFilter(): FilterExpression {
    // A getFilter operation without a subFilterResolverContext requires no I/O and can be executed synchronously
    return getFilter(emptyMap()).blockingGet()
}

internal fun DataFetchingEnvironment.getFilter(subFilterResolverContext: SubFilterResolverContext): Single<FilterExpression> {
    return if (this.containsArgument("filter")) {
        val typeFilter = TypeFilter(FilterDirective.extractResultType(this.fieldDefinition))
        typeFilter.createExpression(this.getArgument("filter"), subFilterResolverContext)
    } else {
        Single.just(SELECT_ALL)
    }
}

internal abstract class Filter<Q : Any>(val name: String, protected val description: String) {

    abstract fun getType(): GraphQLInputType

    abstract fun createExpression(
        query: Q,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String> = emptyList()
    ): Single<FilterExpression>

    fun toObjectField(overrideName: String? = null): GraphQLInputObjectField {
        return GraphQLInputObjectField.newInputObjectField()
            .name(overrideName ?: name)
            .description(description)
            .type(getType())
            .build()
    }

}

internal class TypeFilter(
    private val type: GraphQLObjectType,
    private val parent: TypeFilter? = null,
    private val idContainer: String? = null
) :
    Filter<Map<String, Any>>(
        if (parent != null) "${parent.type.name}_${type.name}Filter" else "${type.name}Filter",
        parent?.let { "Allows filtering based on properties of ${type.name} embedded in ${parent.type.name} instances." }
            ?: "Allows filtering instances of ${type.name} (based on a number of predefined searchable fields)."
    ) {

    private fun isScalarOrEnumList(type: GraphQLType): Boolean {
        return GraphQLTypeUtil.isList(type) && (type as GraphQLList).let { list ->
            val elementType = unwrapNonNullableType(list.originalWrappedType)
            GraphQLTypeUtil.isScalar(elementType) || GraphQLTypeUtil.isEnum(elementType)
        }
    }

    private val filters: Map<String, Filter<*>> =
        listOf(
            AndFilter(this::createExpression, GraphQLTypeReference.typeRef(name)),
            OrFilter(this::createExpression, GraphQLTypeReference.typeRef(name)),
            NotFilter(this::createExpression, GraphQLTypeReference.typeRef(name))
        )
            .map { Pair(it.name, it) }
            .plus(getSearchableFields()
                .filter {
                    val unwrappedType = GraphQLTypeUtil.unwrapAll(it.type)
                    GraphQLTypeUtil.isScalar(unwrappedType) || GraphQLTypeUtil.isEnum(unwrappedType) || isScalarOrEnumList(
                        unwrappedType
                    ) || (/*parent == null && */(unwrappedType is GraphQLObjectType || unwrappedType is GraphQLTypeReference))
                    // ^ only allow nested filters one level deep!
                }
                .flatMap { field ->
                    val argType = GraphQLTypeUtil.unwrapAll(field.type)
                    val argTypeOneUnwrap = unwrapNonNullableType(field.type)
                    val path: List<String>? =
                        field.getDirective(FilterDirective.SEARCHABLE)
                            .getArgument(FilterDirective.PATH_ARG)?.argumentValue?.value?.let { (it as ArrayValue).values.map { e -> (e as StringValue).value } }
                    val idContainer = field.getDirective(FilterDirective.SEARCHABLE)
                        ?.getArgument(FilterDirective.ID_CONTAINER_ARG)?.argumentValue?.value?.let { (it as StringValue).value }
                    if (argType is GraphQLTypeReference || argType is GraphQLObjectType) {
                        listOf(loadSubTypeFilter(argType, idContainer)).map { Pair(field.name, it) }
                    } else {
                        // Normal filter
                        argType as GraphQLInputType
                        val basicFilters = listOf(
                            EqFilter(field.name, path, argType),
                            InFilter(field.name, path, argType)
                        )
                        when {
                            GraphQLTypeUtil.isList(argTypeOneUnwrap) -> listOf(
                                EqFilter(field.name, path, argTypeOneUnwrap as GraphQLInputType),
                                ArrayContainsFilter(
                                    field.name,
                                    path,
                                    argType as GraphQLInputType
                                )
                            )
                            argType == Scalars.GraphQLBoolean || GraphQLTypeUtil.isEnum(argType) -> listOf(
                                EqFilter(
                                    field.name,
                                    path,
                                    argType
                                )
                            )
                            argType == Scalars.GraphQLString -> basicFilters.plus(
                                listOf(
                                    RegexFilter(field.name, path),
                                    CaseInsensitiveRegexFilter(field.name, path)
                                )
                            )
                            else -> basicFilters.plus(
                                listOf(
                                    LteFilter(field.name, path, argType),
                                    LtFilter(field.name, path, argType),
                                    GtFilter(field.name, path, argType),
                                    GteFilter(field.name, path, argType)
                                )
                            )
                        }.map { Pair(it.name, it) }
                    }
                }).toMap()

    private fun loadSubTypeFilter(subType: GraphQLNamedType, idContainer: String?): TypeFilter {
        val key = "${type.name}_${subType.name}Filter"
        val actualType =
            (if (subType is GraphQLTypeReference) FilterDirective.generatedTypes[subType.name]!! else subType) as GraphQLObjectType
        return FilterDirective.generatedTypes.getOrPopulate(key) {
            TypeFilter(actualType, this, idContainer)
        }
    }

    private fun getSearchableFields(): List<GraphQLFieldDefinition> {
        return type.fieldDefinitions.filter { it.directivesByName.containsKey(FilterDirective.SEARCHABLE) }
    }

    private fun unwrapNonNullableType(type: GraphQLType): GraphQLType {
        return if (GraphQLTypeUtil.isNonNull(type)) {
            (type as GraphQLNonNull).originalWrappedType
        } else {
            type
        }
    }

    private var typeUsed = false
    override fun getType(): GraphQLInputType {
        return if (typeUsed) {
            GraphQLTypeReference.typeRef(name)
        } else {
            typeUsed = true
            GraphQLInputObjectType.newInputObject()
                .name(name)
                .description(description)
                .fields(filters.map { it.value.toObjectField(it.key) })
                .build()
        }
    }

    override fun createExpression(
        query: Map<String, Any>,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return if (query.isEmpty()) Single.just(SELECT_ALL) else query.entries.toFlowable().flatMapSingle {
            filters[it.key]?.let { filter ->
                if (filter is TypeFilter) {
                    val path = parentPath.plus(it.key)
                    filter.createExpression(it.value as Map<String, Any>, subFilterResolverContext, path)
                        .flatMap { subFilter ->
                            subFilterResolverContext[path]?.invoke(subFilter)
                                ?: Single.error { IllegalArgumentException("Nested query '$path' is currently not supported by this data fetcher!") }
                        }
                        .map { matchingIds ->
                            In(
                                filter.idContainer ?: "id",
                                matchingIds
                            )
                        }
                } else {
                    (filter as Filter<Any>).createExpression(it.value, subFilterResolverContext, parentPath)
                }
            } ?: Single.error { InvalidParameterException("Filter ${it.key} not found!") }
        }.toList().map { And(it) }
    }
}


internal class AndFilter<Q : Any>(
    private val expressionBuilder: (Q, SubFilterResolverContext, List<String>) -> Single<FilterExpression>,
    private val filterTypeRef: GraphQLTypeReference
) :
    Filter<List<Q>>("_and", "Matches if all of the provided child filters match.") {
    override fun getType(): GraphQLInputType {
        return GraphQLList.list(filterTypeRef)
    }

    override fun createExpression(
        query: List<Q>,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        if (query.isEmpty()) {
            throw InvalidParameterException("$name filter cannot be empty!")
        }
        return query.toFlowable().flatMapSingle { expressionBuilder.invoke(it, subFilterResolverContext, parentPath) }
            .toList()
            .map { And(it) }
    }

}

internal class OrFilter<Q : Any>(
    private val expressionBuilder: (Q, SubFilterResolverContext, List<String>) -> Single<FilterExpression>,
    private val filterTypeRef: GraphQLTypeReference
) :
    Filter<List<Q>>("_or", "Matches if one of the provided child filters match.") {
    override fun getType(): GraphQLInputType {
        return GraphQLList.list(filterTypeRef)
    }

    override fun createExpression(
        query: List<Q>,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        if (query.isEmpty()) {
            throw InvalidParameterException("$name filter cannot be empty!")
        }
        return query.toFlowable().flatMapSingle { expressionBuilder.invoke(it, subFilterResolverContext, parentPath) }
            .toList()
            .map { Or(it) }
    }

}

internal class NotFilter<Q : Any>(
    private val expressionBuilder: (Q, SubFilterResolverContext, List<String>) -> Single<FilterExpression>,
    private val filterTypeRef: GraphQLTypeReference
) :
    Filter<Q>("_not", "Matches if the enclosed filter does not match.") {
    override fun getType(): GraphQLInputType {
        return filterTypeRef
    }

    override fun createExpression(
        query: Q,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return expressionBuilder.invoke(query, subFilterResolverContext, parentPath).map { Not(it) }
    }

}

internal class EqFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<Any>(field, "Matches if '$field' equals the provided value.") {
    override fun getType(): GraphQLInputType {
        return argumentType
    }

    override fun createExpression(
        query: Any,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(Eq(Field(path ?: listOf(field)), query))
    }

}

internal class ArrayContainsFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<Any>("${field}_contains", "Matches if '$field' array contains the provided value.") {
    override fun getType(): GraphQLInputType {
        return argumentType
    }

    override fun createExpression(
        query: Any,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(Eq(Field(path ?: listOf(field)), query))
    }

}

internal class LtFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<Any>("${field}_lt", "Matches if '$field' is less than the provided value.") {
    override fun getType(): GraphQLInputType {
        return argumentType
    }

    override fun createExpression(
        query: Any,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(Lt(Field(path ?: listOf(field)), query))
    }

}

internal class LteFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<Any>("${field}_lte", "Matches if '$field' is less than or equals to the provided value.") {
    override fun getType(): GraphQLInputType {
        return argumentType
    }

    override fun createExpression(
        query: Any,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(Lte(Field(path ?: listOf(field)), query))
    }

}

internal class GtFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<Any>("${field}_gt", "Matches if '$field' is greater than the provided value.") {
    override fun getType(): GraphQLInputType {
        return argumentType
    }

    override fun createExpression(
        query: Any,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(Gt(Field(path ?: listOf(field)), query))
    }

}

internal class GteFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<Any>("${field}_gte", "Matches if '$field' is greater than or equals to the provided value.") {
    override fun getType(): GraphQLInputType {
        return argumentType
    }

    override fun createExpression(
        query: Any,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(Gte(Field(path ?: listOf(field)), query))
    }

}

internal class InFilter(
    private val field: String,
    private val path: List<String>? = null,
    private val argumentType: GraphQLInputType
) :
    Filter<List<Any>>("${field}_in", "Matches if '$field' equals one of the provided values.") {
    override fun getType(): GraphQLInputType {
        return GraphQLList.list(argumentType)
    }

    override fun createExpression(
        query: List<Any>,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(In(Field(path ?: listOf(field)), query.toSet()))
    }

}

internal class RegexFilter(private val field: String, private val path: List<String>? = null) :
    Filter<String>("${field}_regex", "Matches if '$field' matches the provided regex.") {
    override fun getType(): GraphQLInputType {
        return Scalars.GraphQLString
    }

    override fun createExpression(
        query: String,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(RegexMatches(Field(path ?: listOf(field)), query))
    }
}

internal class CaseInsensitiveRegexFilter(private val field: String, private val path: List<String>? = null) :
    Filter<String>("${field}_regex_i", "Matches if '$field' matches the provided regex (case-insensitive).") {
    override fun getType(): GraphQLInputType {
        return Scalars.GraphQLString
    }

    override fun createExpression(
        query: String,
        subFilterResolverContext: SubFilterResolverContext,
        parentPath: List<String>
    ): Single<FilterExpression> {
        return Single.just(RegexMatches(Field(path ?: listOf(field)), query, "i"))
    }
}

// Atomic computeIfAbsent doesn't work for Map in the use-case here, so we've added a custom implementation
private fun <K, V> MutableMap<K, V>.getOrPopulate(key: K, initializer: (K) -> V): V {
    if (!this.containsKey(key)) {
        this[key] = initializer.invoke(key)
    }
    return this[key]!!
}
