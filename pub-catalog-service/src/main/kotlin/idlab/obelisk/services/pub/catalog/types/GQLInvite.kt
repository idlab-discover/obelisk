package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.In
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Role
import idlab.obelisk.definitions.catalog.codegen.RoleField
import idlab.obelisk.services.pub.catalog.impl.Invite
import idlab.obelisk.services.pub.catalog.impl.LOAD_PAGE_SIZE
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getInvites
import idlab.obelisk.utils.service.utils.unpage
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Invite")
class GQLInvite @Inject constructor(
    private val metaStore: MetaStore,
    private val redis: RedissonClient,
    accessManager: AccessManager
) : Operations(accessManager) {


    @GQLFetcher
    fun roles(env: DataFetchingEnvironment): CompletionStage<List<Role>> {
        return withAccess(env) {
            val invite = env.getSource<Invite>()
            unpage { cursor ->
                metaStore.queryRoles(
                    filter = In(RoleField.ID, invite.roleIds),
                    cursor = cursor,
                    limit = LOAD_PAGE_SIZE
                )
            }.toList()
                .map { result -> result.filter { it.datasetId == invite.datasetId } }
        }
    }

    @GQLFetcher
    fun expiresInMs(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val invite = env.getSource<Invite>()
            redis.getInvites()
                .remainTimeToLive(redisInviteKeyOrPattern(datasetId = invite.datasetId, inviteId = invite.id))
                .to(RxJavaBridge.toV2Single())
        }
    }

}