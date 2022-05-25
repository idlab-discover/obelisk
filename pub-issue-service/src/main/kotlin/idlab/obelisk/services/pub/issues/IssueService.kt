package idlab.obelisk.services.pub.issues

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.services.pub.issues.impl.MongoIssueTracker
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.*
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import org.codejargon.feather.Provides
import javax.inject.Inject
import javax.inject.Singleton

const val HTTP_BASE_PATH = "/issues"

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        MongoDBMetaStoreModule(),
        BasicAccessManagerModule()
    ).bootstrap(IssueService::class.java)
}

@Singleton
class IssueService @Inject constructor(
    private val config: OblxConfig,
    private val router: Router,
    private val accessManager: AccessManager,
    private val issueTracker: MongoIssueTracker
) : OblxService {

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        setupPersonalEndpoints(basePath)
        setupAdminEndpoints(basePath)
        return Completable.complete()
    }

    private fun setupPersonalEndpoints(basePath: String) {
        val personalPath = "$basePath/personal"

        /* PERSONAL ISSUES API */
        router.postWithBody(personalPath).handler { ctx ->
            accessManager.getToken(ctx.request())
                .flatMap { token ->
                    issueTracker.openIssue(
                        reporter = token.user.id!!,
                        input = ctx.bodyAsJson.mapTo(CreateIssueInput::class.java)
                    )
                }
                .subscribeBy(onSuccess = { ctx.response().setStatusCode(201).end() }, onError = writeHttpError(ctx))
        }

        router.get(personalPath).handler { ctx ->
            accessManager.getToken(ctx.request())
                .flatMap { token ->
                    issueTracker.queryIssues(
                        filter = And(
                            Eq(Field(IssueFields.REPORTER, UserReferenceFields.ID), token.user.id!!),
                            parseFilter(ctx)
                        ),
                        limit = parseLimit(ctx),
                        cursor = ctx.request().getParam("cursor"),
                        sort = parseSortKey(ctx)
                    )
                }
                .subscribeBy(onSuccess = writeHttpResponse(ctx), onError = writeHttpError(ctx))
        }

        router.get("$personalPath/:issueId").handler { ctx ->
            accessManager.getToken(ctx.request())
                .flatMap { token -> getPersonalIssue(ctx.pathParam("issueId"), token) }
                .subscribeBy(onSuccess = writeHttpResponse(ctx), onError = writeHttpError(ctx))
        }

        router.putWithBody("$personalPath/:issueId").handler { ctx ->
            accessManager.getToken(ctx.request())
                // Find the personal issue, to check if the User is the reporter (and thus can update the issue)
                .flatMap { token -> getPersonalIssue(ctx.pathParam("issueId"), token) }
                .flatMapCompletable { issue ->
                    val update = ctx.bodyAsJson.mapTo(UpdateIssueInput::class.java)
                    // User cannot assign people, so as protection always set this field to null
                    issueTracker.updateIssue(
                        issue.id!!,
                        update.copy(assignee = null, modifiedAt = System.currentTimeMillis())
                    )
                }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }

        /* PERSONAL ISSUE COMMENTS API */
        router.postWithBody("$personalPath/:issueId/activity").handler { ctx ->
            accessManager.getToken(ctx.request())
                // Find the personal issue, to check if the User is the reporter (and thus can update the issue)
                .flatMap { token ->
                    getPersonalIssue(ctx.pathParam("issueId"), token)
                        .flatMap { issue ->
                            val activity = IssueActivity(
                                parentIssueId = issue.id!!,
                                author = UserReference(id = token.user.id!!),
                                changeState = ctx.request().getParam("close")?.takeIf { it.toBoolean() }
                                    ?.let { IssueState.RESOLVED },
                                comment = ctx.bodyAsJson.getString("comment")
                            )
                            issueTracker.addActivity(activity)
                        }
                }
                .subscribeBy(onSuccess = { ctx.response().setStatusCode(201).end() }, onError = writeHttpError(ctx))
        }

        router.get("$personalPath/:issueId/activity").handler { ctx ->
            accessManager.getToken(ctx.request())
                // Find the personal issue, to check if the User is the reporter (and thus can read the activity)
                .flatMap { token -> getPersonalIssue(ctx.pathParam("issueId"), token) }
                .flatMap { issue ->
                    issueTracker.loadActivity(issue.id!!).filter { !it.internal }.toList()
                }
                .subscribeBy(onSuccess = writeHttpResponse(ctx), onError = writeHttpError(ctx))
        }

        router.putWithBody("$personalPath/:issueId/activity/:activityId").handler { ctx ->
            accessManager.getToken(ctx.request())
                .flatMap { token ->
                    // Try to get the activity to check if the user was the author
                    issueTracker.getActivity(ctx.pathParam("activityId"))
                        .filter { it.author.id == token.user.id && it.parentIssueId == ctx.pathParam("issueId") }
                        .toSingle()
                }
                .flatMapCompletable { activity ->
                    val update = ctx.bodyAsJson.mapTo(UpdateActivityInput::class.java)
                    issueTracker.updateActivity(
                        activity.id!!,
                        // A regular user change internal field, modifiedAt is always overwritten with current system time
                        update.copy(internal = null, modifiedAt = System.currentTimeMillis())
                    )
                }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }

        router.delete("$personalPath/:issueId/activity/:activityId").handler { ctx ->
            accessManager.getToken(ctx.request())
                .flatMap { token ->
                    // Try to get the activity to check if the user was the author
                    issueTracker.getActivity(ctx.pathParam("activityId"))
                        .filter { it.author.id == token.user.id && it.parentIssueId == ctx.pathParam("issueId") }
                        .toSingle()
                }
                .flatMapCompletable { activity ->
                    issueTracker.removeActivity(activity.id!!)
                }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }
    }

    private fun setupAdminEndpoints(basePath: String) {
        val adminPath = "$basePath/all"

        router.get(adminPath).handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMap {
                    issueTracker.queryIssues(
                        parseFilter(ctx),
                        parseLimit(ctx),
                        ctx.request().getParam("cursor"),
                        parseSortKey(ctx)
                    )
                }
                .subscribeBy(onSuccess = writeHttpResponse(ctx), onError = writeHttpError(ctx))
        }

        router.get("$adminPath/:issueId").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMap {
                    issueTracker.getIssue(ctx.pathParam("issueId")).toSingle()
                }
                .subscribeBy(onSuccess = writeHttpResponse(ctx), onError = writeHttpError(ctx))
        }

        router.putWithBody("$adminPath/:issueId").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMap {
                    issueTracker.getIssue(ctx.pathParam("issueId")).toSingle()
                }
                .flatMapCompletable { issue ->
                    val update = ctx.bodyAsJson.let { body ->
                        if (body.containsKey("assignee") && body.getValue("assignee") != null) {
                            body.put("assignee", JsonObject().put("id", body.getString("assignee")))
                        }
                        body.mapTo(UpdateIssueInput::class.java)
                    }
                    val nullFields =
                        if (ctx.bodyAsJson.containsKey("assignee") && ctx.bodyAsJson.getValue("assignee") == null) setOf(
                            IssueFields.ASSIGNEE
                        ) else emptySet()
                    issueTracker.updateIssue(
                        issue.id!!,
                        update.copy(modifiedAt = System.currentTimeMillis()),
                        nullFields
                    )
                }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }

        router.delete("$adminPath/:issueId").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMapCompletable { issueTracker.removeIssue(ctx.pathParam("issueId")) }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }

        router.postWithBody("$adminPath/:issueId/activity").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMapCompletable { token ->
                    issueTracker.getIssue(ctx.pathParam("issueId")).toSingle()
                        .flatMapCompletable { issue ->
                            val input = ctx.bodyAsJson.mapTo(CreateActivityInput::class.java)
                            val activity = IssueActivity(
                                parentIssueId = issue.id!!,
                                internal = input.internal,
                                changeState = input.changeState,
                                comment = input.comment,
                                author = UserReference(id = token.user.id!!)
                            )
                            issueTracker.addActivity(activity).ignoreElement()
                        }
                }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(201).end() }, onError = writeHttpError(ctx))
        }

        router.get("$adminPath/:issueId/activity").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMap { issueTracker.loadActivity(ctx.pathParam("issueId")).toList() }
                .subscribeBy(onSuccess = writeHttpResponse(ctx), onError = writeHttpError(ctx))
        }

        router.putWithBody("$adminPath/:issueId/activity/:activityId").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMap { issueTracker.getActivity(ctx.pathParam("activityId")).toSingle() }
                .flatMapCompletable { activity ->
                    val update = ctx.bodyAsJson.mapTo(UpdateActivityInput::class.java)
                    val nullFields =
                        ctx.bodyAsJson.map.filter { it.value == null }.keys.map { IssueActivityFields.valueOf(it) }
                            .filter {
                                // Extend this if there are other possible null fields later
                                it == IssueActivityFields.COMMENT || it == IssueActivityFields.INTERNAL
                            }
                            .toSet()
                    issueTracker.updateActivity(
                        activity.id!!,
                        // modifiedAt is always overwritten with current system time
                        update.copy(modifiedAt = System.currentTimeMillis()),
                        nullFields
                    )
                }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }

        router.delete("$adminPath/:issueId/activity/:activityId").handler { ctx ->
            getTokenIfAdmin(ctx)
                .flatMapCompletable { issueTracker.removeActivity(ctx.pathParam("activityId")) }
                .subscribeBy(onComplete = { ctx.response().setStatusCode(204).end() }, onError = writeHttpError(ctx))
        }
    }

    private fun parseFilter(ctx: RoutingContext): FilterExpression {
        val filters = listOfNotNull(
            ctx.request().getParam("status")?.let { Eq(IssueFields.STATE, IssueState.valueOf(it.uppercase())) },
            ctx.request().getParam("hideClosed")?.takeIf { it.toBoolean() }?.let {
                In(
                    IssueFields.STATE,
                    setOf(
                        IssueState.WAITING_FOR_SUPPORT,
                        IssueState.WAITING_FOR_REPORTER,
                        IssueState.IN_PROGRESS
                    )
                )
            },
            ctx.request().getParam("containsText")?.let {
                Or(
                    RegexMatches(IssueFields.SUMMARY, it, "i"),
                    RegexMatches(IssueFields.DESCRIPTION, it, "i")
                )
            }
        )
        return if (filters.isNotEmpty()) And(filters) else SELECT_ALL
    }

    private fun parseSortKey(ctx: RoutingContext): Map<Enum<*>, Ordering>? {
        return ctx.request().getParam("sort")?.let { SortOptions.valueOf(it.uppercase()).sortKey }
    }

    private fun parseLimit(ctx: RoutingContext): Int {
        return ctx.request().getParam("limit")?.let { minOf(it.toInt(), MAX_LIMIT) } ?: DEFAULT_LIMIT
    }

    private fun getPersonalIssue(issueId: String, token: Token): Single<Issue> {
        return issueTracker.getIssue(issueId).filter { it.reporter.id == token.user.id }.toSingle()
    }

    private fun getTokenIfAdmin(ctx: RoutingContext): Single<Token> {
        return accessManager.getToken(ctx.request())
            .flatMap { token ->
                if (token.user.platformManager) Single.just(token) else Single.error(
                    AuthorizationException("This endpoint requires Platform Manager permissions!")
                )
            }
    }

}