package idlab.obelisk.services.pub.catalog.impl

import graphql.language.ArrayValue
import graphql.language.EnumValue
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment

internal class RestrictedDirective : SchemaDirectiveWiring {

    enum class ClearanceLevel(val prettyString: String) {
        PLATFORM_MANAGER("Platform Manager"), DATASET_MANAGER("Dataset Manager"), DATASET_MEMBER("Dataset Member"), ME("User her/himself"), TEAM_MANAGER(
            "Team Manager"
        ),
        TEAM_MEMBER("Team Member")
    }

    companion object {
        const val NAME = "restricted"
        const val MIN_CLEARANCE_ATTR = "level"

        fun fromFieldDef(field: GraphQLFieldDefinition): Set<ClearanceLevel> {
            return field.getDirective(NAME)?.getArgument(MIN_CLEARANCE_ATTR)?.argumentValue?.let { value ->
                (value.value as ArrayValue).values.map {
                    ClearanceLevel.valueOf(
                        (it as EnumValue).name
                    )
                }.toSet()
            } ?: emptySet()
        }

        fun fromInputFieldDef(field: GraphQLInputObjectField): Set<ClearanceLevel> {
            return field.getDirective(NAME)?.getArgument(MIN_CLEARANCE_ATTR)?.argumentValue?.let { value ->
                (value.value as ArrayValue).values.map {
                    ClearanceLevel.valueOf(
                        (it as EnumValue).name
                    )
                }.toSet()
            } ?: emptySet()
        }
    }

    override fun onField(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>?): GraphQLFieldDefinition {
        val field = environment!!.element
        val clearance = fromFieldDef(field)
        return field.transform { builder ->
            builder.description(
                "${field.description}${
                    if (clearance.isNotEmpty()) " [Restricted to: ${
                        clearance.joinToString(
                            ", "
                        )
                    }]" else ""
                }"
            )
        }
    }

}
