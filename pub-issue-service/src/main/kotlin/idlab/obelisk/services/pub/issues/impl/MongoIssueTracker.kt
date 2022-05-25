package idlab.obelisk.services.pub.issues.impl

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.catalog.codegen.UserField
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.issues.*
import idlab.obelisk.utils.mongo.*
import idlab.obelisk.utils.mongo.query.fromFilter
import idlab.obelisk.utils.service.mail.MailService
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import javax.inject.Inject
import javax.inject.Singleton

private const val ISSUES_DB_NAME = "oblx-issues"
private const val COUNTERS_COL = "counters"
private const val ISSUES_COL = "issues"
private const val ACTIVITIES_COL = "activities"

@Singleton
class MongoIssueTracker @Inject constructor(
    vertx: Vertx,
    config: OblxConfig,
    private val metaStore: MetaStore,
    private val mailService: MailService
) : IssueTracker {

    private val mongoClient = MongoClient.createShared(
        vertx,
        JsonObject().put("connection_string", config.mongoConnectionUri).put("db_name", ISSUES_DB_NAME),
        ISSUES_DB_NAME
    )

    private val initSequences = Completable.mergeArray(initSequence(ISSUES_COL), initSequence(ACTIVITIES_COL)).cache()

    override fun openIssue(reporter: String, input: CreateIssueInput): Single<String> {
        return nextId(ISSUES_COL)
            .flatMap { id ->
                val issue = Issue(
                    id = id.toString(),
                    summary = input.summary,
                    reporter = UserReference(reporter),
                    description = input.description,
                )
                mongoClient.rxCreate(ISSUES_COL, issue)
                    .ignoreElement()
                    .flatMap {
                        // Mail notification to admins
                        unpage { cursor ->
                            metaStore.queryUsers(
                                filter = Eq(UserField.PLATFORM_MANAGER, true),
                                cursor = cursor
                            )
                        }
                            .toList()
                            .flatMapCompletable { admins ->
                                mailService.send(
                                    admins.toSet(),
                                    "[Obelisk] New issue: ${input.summary}",
                                    "A new issue was reported.",
                                    "/catalog/admin/tickets/$id"
                                )
                            }
                    }
                    .toSingleDefault(id.toString())
            }
    }

    override fun updateIssue(issueId: String, update: UpdateIssueInput, nullFields: Set<IssueFields>): Completable {
        return mongoClient.rxUpdate(ISSUES_COL, issueId, update, nullFields.map { it.toString() }.toSet())
    }

    override fun addActivity(activity: IssueActivity): Single<String> {
        return nextId(ACTIVITIES_COL)
            .flatMap { id ->
                mongoClient.rxCreate(ACTIVITIES_COL, activity.copy(id = id.toString()))
                    .ignoreElement()
                    .flatMap {
                        // Mail notification
                        getIssue(activity.parentIssueId)
                            .toSingle()
                            .flatMap { issue ->
                                loadUserContext(
                                    setOf(
                                        if (activity.author.id != issue.reporter.id && !activity.internal) issue.reporter.id else null,
                                        if (activity.author.id != issue.assignee?.id) issue.assignee?.id else null
                                    )
                                )
                            }
                            .flatMapCompletable { users ->
                                val title = "[Obelisk] New activity on issue #${activity.parentIssueId}"
                                val message = "There has been activity on an issue you're involved with."
                                Completable.mergeArray(
                                    mailService.send(
                                        users.values.filter { it.platformManager }.toSet(),
                                        title,
                                        message,
                                        "/catalog/admin/tickets/$id",
                                    ), mailService.send(
                                        users.values.filterNot { it.platformManager }.toSet(),
                                        title,
                                        message,
                                        "/catalog/my/tickets/$id",
                                    )
                                )
                            }
                    }
                    .toSingleDefault(id.toString())
            }
            .flatMap { id ->
                // Update state if necessary
                (if (activity.changeState != null) {
                    mongoClient.rxUpdate(
                        ISSUES_COL,
                        activity.parentIssueId,
                        StateChangeUpdateWrapper(activity.changeState),
                        emptySet()
                    )
                } else {
                    Completable.complete()
                }).toSingleDefault(id)
            }
    }

    override fun updateActivity(
        activityId: String,
        update: UpdateActivityInput,
        nullFields: Set<IssueActivityFields>
    ): Completable {
        return mongoClient.rxUpdate(ACTIVITIES_COL, activityId, update, nullFields.map { it.toString() }.toSet())
    }

    override fun removeIssue(issueId: String): Completable {
        return mongoClient.rxDeleteById(ISSUES_COL, issueId)
    }

    override fun removeActivity(activityId: String): Completable {
        return mongoClient.rxDeleteById(ACTIVITIES_COL, activityId)
    }

    override fun getIssue(issueId: String): Maybe<Issue> {
        return mongoClient.rxFindById(ISSUES_COL, issueId, Issue::class.java)
            .flatMap { issue ->
                loadUserContext(setOf(issue.assignee?.id, issue.reporter.id)).map { resolveIssueUserRefs(issue, it) }
                    .toMaybe()
            }
    }

    override fun getActivity(activityId: String): Maybe<IssueActivity> {
        return mongoClient.rxFindById(ACTIVITIES_COL, activityId, IssueActivity::class.java)
            .flatMap { activity ->
                loadUserContext(setOf(activity.author.id)).map { resolveActivityUserRefs(activity, it) }.toMaybe()
            }
    }

    override fun queryIssues(
        filter: FilterExpression,
        limit: Int,
        cursor: String?,
        sort: Map<Enum<*>, Ordering>?
    ): Single<PagedResult<Issue>> {
        return mongoClient.rxFindPaged(
            ISSUES_COL,
            fromFilter(filter),
            Issue::class.java,
            limit,
            cursor,
            sort?.let { fromSortMap(it) })
            .flatMap { result ->
                val userRefs = result.items.flatMap { setOf(it.assignee?.id, it.reporter.id) }.toSet()
                loadUserContext(userRefs).map { context ->
                    PagedResult(items = result.items.map { resolveIssueUserRefs(it, context) }, cursor = result.cursor)
                }
            }
    }

    override fun loadActivity(issueId: String): Flowable<IssueActivity> {
        return mongoClient.rxFind(
            ACTIVITIES_COL,
            fromFilter(Eq(IssueActivityFields.PARENT_ISSUE_ID, issueId)),
            IssueActivity::class.java
        )
            .toList()
            .flatMapPublisher { result ->
                val userRefs = result.map { it.author.id }.toSet()
                loadUserContext(userRefs).map { context ->
                    result.map { resolveActivityUserRefs(it, context) }
                }
                    .flattenAsFlowable { it }
            }
    }

    private fun nextId(collection: String): Single<Long> {
        return initSequences.flatMapSingle {
            mongoClient.rxFindOneAndUpdate(
                COUNTERS_COL,
                JsonObject().put("_id", collection),
                JsonObject().put("\$inc", JsonObject().put("seq", 1))
            )
                .toSingle()
                .map { it.getLong("seq") }
        }
    }

    private fun initSequence(collection: String): Completable {
        return mongoClient.rxFind(COUNTERS_COL, JsonObject().put("_id", collection))
            .flatMapCompletable { result ->
                if (result.isNotEmpty()) {
                    Completable.complete()
                } else {
                    mongoClient.rxSave(COUNTERS_COL, JsonObject().put("_id", collection).put("seq", 0)).ignoreElement()
                }
            }
    }

    private fun resolveIssueUserRefs(issue: Issue, userContext: Map<String, User>): Issue {
        return issue.copy(
            reporter = UserReference.fromUser(userContext[issue.reporter.id!!]!!),
            assignee = if (issue.assignee != null) UserReference.fromUser(userContext[issue.assignee.id!!]!!) else null
        )
    }

    private fun resolveActivityUserRefs(activity: IssueActivity, userContext: Map<String, User>): IssueActivity {
        return activity.copy(
            author = UserReference.fromUser(userContext[activity.author.id!!]!!)
        )
    }

    private fun loadUserContext(userIds: Set<String?>): Single<Map<String, User>> {
        return unpage { cursor ->
            metaStore.queryUsers(
                filter = In(UserField.ID, userIds.filterNotNull().toSet()),
                cursor = cursor
            )
        }
            .toList()
            .map { result -> result.associateBy { it.id!! } }
    }

}

data class StateChangeUpdateWrapper(val state: IssueState? = null)