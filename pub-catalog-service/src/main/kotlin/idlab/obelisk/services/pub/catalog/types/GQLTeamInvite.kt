package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.services.pub.catalog.impl.Invite
import idlab.obelisk.services.pub.catalog.impl.TeamInvite
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getTeamInvites
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("TeamInvite")
class GQLTeamInvite @Inject constructor(accessManager: AccessManager, private val redis: RedissonClient) : Operations(accessManager) {

    @GQLFetcher
    fun expiresInMs(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val invite = env.getSource<TeamInvite>()
            redis.getTeamInvites()
                .remainTimeToLive(redisInviteKeyOrPattern(datasetId = invite.teamId, inviteId = invite.id))
                .to(RxJavaBridge.toV2Single())
        }
    }

}