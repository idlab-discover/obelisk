package idlab.obelisk.services.pub.catalog.impl

import graphql.Scalars
import graphql.language.BooleanValue
import graphql.schema.*
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import hu.akarnokd.rxjava2.interop.SingleInterop

internal class PagedDirective : SchemaDirectiveWiring {

    companion object {
        const val PAGED = "paged"
        const val EXPOSE_COUNT_ATTR = "exposeCount"

        private val generatedPageTypes = mutableMapOf<String, GraphQLObjectType>()
        val visitedObjectTypes = mutableMapOf<String, GraphQLObjectType>()
    }

    override fun onField(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>?): GraphQLFieldDefinition {
        val field = environment!!.element
        try {
            val type = ((field.type as GraphQLList).wrappedType) as GraphQLObjectType
            visitedObjectTypes.putIfAbsent(type.name, type)
            return field.transform { builder ->
                builder
                    .argument(
                        GraphQLArgument.newArgument().name("cursor")
                            .description("When paging through results, supply the cursor returned from the previous Page here.")
                            .type(Scalars.GraphQLString)
                    )
                    .argument(
                        GraphQLArgument.newArgument().name("limit")
                            .description("Limit the number of returned items to the specified amount (Page size).")
                            .type(Scalars.GraphQLInt)
                    )
                    .type(GraphQLNonNull.nonNull(generatedPageTypes.computeIfAbsent("${type.name}Page") { key ->
                        val tmp = GraphQLObjectType.newObject()
                            .name(key)
                            .description("Utility type to model paged results for ${type.name}.")
                            .field(
                                GraphQLFieldDefinition.newFieldDefinition().name("items")
                                    .description("Instances of type ${type.name} contained in this Page.")
                                    .type(
                                        GraphQLNonNull.nonNull(
                                            GraphQLList.list(
                                                GraphQLNonNull.nonNull(
                                                    GraphQLTypeReference.typeRef(type.name)
                                                )
                                            )
                                        )
                                    )
                            )
                            .field(
                                GraphQLFieldDefinition.newFieldDefinition()
                                    .description("The cursor pointing to the next Page or null if the current Page was the last Page of the result.")
                                    .name("cursor").type(Scalars.GraphQLString)
                            )

                        if (field.getDirective(PAGED)
                                ?.getArgument(EXPOSE_COUNT_ATTR)?.argumentValue?.value?.let { (it as BooleanValue).isValue } != false
                        ) {
                            tmp.field(
                                GraphQLFieldDefinition.newFieldDefinition().name("count")
                                    .description("The total amount of ${type.name} instances in the result (across pages), matching the filter conditions.")
                                    .type(Scalars.GraphQLInt)
                                    .dataFetcher { env ->
                                        val source = env.getSource<GraphQLPage<*>>()
                                        source.countSupplier
                                            .invoke()
                                            .to(SingleInterop.get())
                                    }
                            )
                        }

                        tmp.build()
                    }))
            }
        } catch (t: Throwable) {
            throw IllegalArgumentException(
                "Error while generating Page type for ${environment.fieldsContainer.name}->${environment.fieldDefinition.name}",
                t
            )
        }
    }

}
