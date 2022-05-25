package idlab.obelisk.services.pub.catalog.impl

import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLObjectType
import idlab.obelisk.definitions.catalog.Client
import idlab.obelisk.definitions.catalog.User

fun originProducerResolver(env: TypeResolutionEnvironment): GraphQLObjectType {
    val origin = env.getObject<Any>()
    return when (origin) {
        is User -> env.schema.getObjectType("User")
        is Client -> env.schema.getObjectType("Client")
        else -> throw IllegalArgumentException("${origin::class.java.name} is not a known Origin type!")
    }
}