import idlab.obelisk.definitions.FilterExpression
import idlab.obelisk.definitions.Ordering
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.definitions.framework.OblxModule
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import org.codejargon.feather.Provides
import java.util.*
import javax.inject.Singleton
import kotlin.math.exp

class MockMetaStoreModule : OblxModule {

    @Provides
    @Singleton
    fun metaStore(): MetaStore {
        return MockMetaStore()
    }

}

class MockMetaStore : MetaStore {
    private val exports = mutableMapOf<String, DataExport>()
    override fun getRolesForUser(userId: String, datasetId: String): Maybe<List<Role>> {
        TODO("Not yet implemented")
    }

    override fun getRolesForTeam(teamId: String, datasetId: String): Maybe<List<Role>> {
        TODO("Not yet implemented")
    }

    override fun getDefaultUsagePlan(): Single<UsagePlan> {
        TODO("Not yet implemented")
    }

    override fun getDefaultUsageLimit(): Single<UsageLimit> {
        TODO("Not yet implemented")
    }

    override fun getAggregatedUsageLimits(user: User, client: Client?): Single<UsageLimit> {
        TODO("Not yet implemented")
    }

    override fun getAggregatedGrantsForUser(userId: String, datasetId: String?): Single<Map<String, Grant>> {
        TODO("Not yet implemented")
    }

    override fun getAggregatedDatasetMembershipsForUser(
        userId: String,
        datasetId: String?
    ): Single<Map<String, DatasetMembership>> {
        TODO("Not yet implemented")
    }

    override fun getAggregatedGrantsForTeam(teamId: String, datasetId: String?): Single<Map<String, Grant>> {
        TODO("Not yet implemented")
    }

    override fun createAccessRequest(accessRequest: AccessRequest): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateAccessRequest(
        accessRequestId: String,
        update: AccessRequestUpdate,
        nullableFields: Set<AccessRequestNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getAccessRequest(accessRequestId: String): Maybe<AccessRequest> {
        TODO("Not yet implemented")
    }

    override fun queryAccessRequests(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<AccessRequest>> {
        TODO("Not yet implemented")
    }

    override fun countAccessRequests(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeAccessRequest(accessRequestId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createAnnouncement(announcement: Announcement): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateAnnouncement(
        announcementId: String,
        update: AnnouncementUpdate,
        nullableFields: Set<AnnouncementNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getAnnouncement(announcementId: String): Maybe<Announcement> {
        TODO("Not yet implemented")
    }

    override fun queryAnnouncements(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Announcement>> {
        TODO("Not yet implemented")
    }

    override fun countAnnouncements(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeAnnouncement(announcementId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createClient(client: Client): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateClient(
        clientId: String,
        update: ClientUpdate,
        nullableFields: Set<ClientNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getClient(clientId: String): Maybe<Client> {
        TODO("Not yet implemented")
    }

    override fun queryClients(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Client>> {
        TODO("Not yet implemented")
    }

    override fun countClients(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeClient(clientId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createDataExport(export: DataExport): Single<String> {
        val instance = if (export.id == null) {
            export.copy(id = UUID.randomUUID().toString())
        } else {
            export
        }
        exports[instance.id!!] = instance
        return Single.just(instance.id!!)
    }

    override fun updateDataExport(
        dataExportId: String,
        update: DataExportUpdate,
        nullableFields: Set<DataExportNullableField>
    ): Completable {
        return getDataExport(dataExportId)
            .flatMapCompletable { export ->
                var updatedExport = export
                if (update.status != null)
                    updatedExport = updatedExport.copy(status = update.status!!)
                if (update.result != null)
                    updatedExport = updatedExport.copy(result = update.result!!)
                exports[dataExportId] = updatedExport
                Completable.complete()
            }
    }

    override fun getDataExport(exportId: String): Maybe<DataExport> {
        return exports[exportId]?.let { Maybe.just(it) } ?: Maybe.empty()
    }

    override fun queryDataExports(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<DataExport>> {
        return Single.just(PagedResult(items = exports.values.toList()))
    }

    override fun countDataExports(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeDataExport(dataExportId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createDataStream(dataStream: DataStream): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateDataStream(
        dataStreamId: String,
        update: DataStreamUpdate,
        nullableFields: Set<DataStreamNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getDataStream(dataStreamId: String): Maybe<DataStream> {
        TODO("Not yet implemented")
    }

    override fun queryDataStreams(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<DataStream>> {
        TODO("Not yet implemented")
    }

    override fun countDataStreams(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeDataStream(dataStreamId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createDataset(dataset: Dataset): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateDataset(
        datasetId: String,
        update: DatasetUpdate,
        nullableFields: Set<DatasetNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getDataset(datasetId: String): Maybe<Dataset> {
        TODO("Not yet implemented")
    }

    override fun queryDatasets(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Dataset>> {
        TODO("Not yet implemented")
    }

    override fun countDatasets(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeDataset(datasetId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createMetric(metric: Metric): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateMetric(
        metricId: String,
        update: MetricUpdate,
        nullableFields: Set<MetricNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getMetric(metricId: String): Maybe<Metric> {
        TODO("Not yet implemented")
    }

    override fun queryMetrics(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Metric>> {
        TODO("Not yet implemented")
    }

    override fun countMetrics(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeMetric(metricId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createRole(role: Role): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateRole(roleId: String, update: RoleUpdate, nullableFields: Set<RoleNullableField>): Completable {
        TODO("Not yet implemented")
    }

    override fun getRole(roleId: String): Maybe<Role> {
        TODO("Not yet implemented")
    }

    override fun queryRoles(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Role>> {
        TODO("Not yet implemented")
    }

    override fun countRoles(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeRole(roleId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createTeam(team: Team): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateTeam(teamId: String, update: TeamUpdate, nullableFields: Set<TeamNullableField>): Completable {
        TODO("Not yet implemented")
    }

    override fun getTeam(teamId: String): Maybe<Team> {
        TODO("Not yet implemented")
    }

    override fun queryTeams(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Team>> {
        TODO("Not yet implemented")
    }

    override fun countTeams(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeTeam(teamId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createThing(thing: Thing): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateThing(
        thingId: String,
        update: ThingUpdate,
        nullableFields: Set<ThingNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getThing(thingId: String): Maybe<Thing> {
        TODO("Not yet implemented")
    }

    override fun queryThings(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Thing>> {
        TODO("Not yet implemented")
    }

    override fun countThings(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeThing(thingId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createUsageLimit(usageLimit: UsageLimit): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateUsageLimit(
        usageLimitId: String,
        update: UsageLimitUpdate,
        nullableFields: Set<UsageLimitNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getUsageLimit(usageLimitId: String): Maybe<UsageLimit> {
        TODO("Not yet implemented")
    }

    override fun queryUsageLimits(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<UsageLimit>> {
        TODO("Not yet implemented")
    }

    override fun countUsageLimits(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeUsageLimit(usageLimitId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createUsagePlan(usagePlan: UsagePlan): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateUsagePlan(
        usagePlanId: String,
        update: UsagePlanUpdate,
        nullableFields: Set<UsagePlanNullableField>
    ): Completable {
        TODO("Not yet implemented")
    }

    override fun getUsagePlan(usagePlanId: String): Maybe<UsagePlan> {
        TODO("Not yet implemented")
    }

    override fun queryUsagePlans(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<UsagePlan>> {
        TODO("Not yet implemented")
    }

    override fun countUsagePlans(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeUsagePlan(usagePlanId: String): Completable {
        TODO("Not yet implemented")
    }

    override fun createUser(user: User): Single<String> {
        TODO("Not yet implemented")
    }

    override fun updateUser(userId: String, update: UserUpdate, nullableFields: Set<UserNullableField>): Completable {
        TODO("Not yet implemented")
    }

    override fun getUser(userId: String): Maybe<User> {
        TODO("Not yet implemented")
    }

    override fun queryUsers(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<User>> {
        TODO("Not yet implemented")
    }

    override fun countUsers(filter: FilterExpression): Single<Long> {
        TODO("Not yet implemented")
    }

    override fun removeUser(userId: String): Completable {
        TODO("Not yet implemented")
    }

}
