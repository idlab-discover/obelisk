package idlab.obelisk.services.pub.issues

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import idlab.obelisk.definitions.FilterExpression
import idlab.obelisk.definitions.Ordering
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.SELECT_ALL
import idlab.obelisk.definitions.catalog.User
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single

const val DEFAULT_LIMIT = 25
const val MAX_LIMIT = 250

interface IssueTracker {

    // Create a new issue and return the Issue ID as a Single
    fun openIssue(reporter: String, input: CreateIssueInput): Single<String>

    // Update the specified issue
    fun updateIssue(issueId: String, update: UpdateIssueInput, nullFields: Set<IssueFields> = setOf()): Completable

    // Add activity to an issue and return the Activity ID as a Single
    fun addActivity(activity: IssueActivity): Single<String>

    // Update the comment for an activity
    fun updateActivity(
        activityId: String,
        update: UpdateActivityInput,
        nullFields: Set<IssueActivityFields> = setOf()
    ): Completable

    fun removeIssue(issueId: String): Completable

    // Remove an activity
    fun removeActivity(activityId: String): Completable

    // Find specific issue
    fun getIssue(issueId: String): Maybe<Issue>

    fun getActivity(activityId: String): Maybe<IssueActivity>

    // Query issues
    fun queryIssues(
        filter: FilterExpression = SELECT_ALL,
        limit: Int = DEFAULT_LIMIT,
        cursor: String? = null,
        sort: Map<Enum<*>, Ordering>? = null
    ): Single<PagedResult<Issue>>

    // Load activity for issue
    fun loadActivity(issueId: String): Flowable<IssueActivity>

}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Issue(
    @JsonProperty("_id")
    val id: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long? = null,
    val summary: String,
    val description: String,
    // Obelisk User ID
    val reporter: UserReference,
    // Obelisk User ID
    val assignee: UserReference? = null,
    val state: IssueState = IssueState.WAITING_FOR_SUPPORT
)

enum class IssueState {
    WAITING_FOR_SUPPORT, WAITING_FOR_REPORTER, IN_PROGRESS, CANCELLED, RESOLVED
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueActivity(
    @JsonProperty("_id")
    val id: String? = null,
    val parentIssueId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long? = null,
    // Don't show this activity to the reporter.
    val internal: Boolean = false,
    // Obelisk User ID
    val author: UserReference,
    // When posting an activity, the author can set the next state for the issue (the reporter can only close the issue, other state changes are not allowed)
    val changeState: IssueState? = null,
    // Comment could contain markdown?
    val comment: String? = null
)

enum class IssueFields(private val fieldName: String) {
    ID("_id"),
    CREATED_AT("createdAt"),
    MODIFIED_AT("modifiedAt"),
    SUMMARY("summary"),
    DESCRIPTION("description"),
    REPORTER("reporter"),
    ASSIGNEE("assignee"),
    STATE("state");

    public override fun toString(): String = fieldName
}

enum class IssueActivityFields(private val fieldName: String) {
    ID("_id"),
    PARENT_ISSUE_ID("parentIssueId"),
    CREATED_AT("createdAt"),
    MODIFIED_AT("modifiedAt"),
    INTERNAL("internal"),
    AUTHOR("author"),
    CHANGE_STATE("changeState"),
    COMMENT("comment");

    public override fun toString(): String = fieldName
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserReference(
    val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null
) {
    companion object {

        fun fromUser(user: User): UserReference {
            return UserReference(id = user.id, firstName = user.firstName, lastName = user.lastName, email = user.email)
        }

    }
}

enum class UserReferenceFields(private val fieldName: String) {
    ID("id"),
    FIRST_NAME("firstName"),
    LAST_NAME("lastName"),
    EMAIL("email");

    public override fun toString(): String = fieldName
}

enum class SortOptions(val sortKey: Map<Enum<*>, Ordering>) {
    CREATED_AT_ASC(mapOf(IssueFields.CREATED_AT to Ordering.asc)),
    CREATED_AT_DESC(mapOf(IssueFields.CREATED_AT to Ordering.desc)),
    MODIFIED_AT_ASC(mapOf(IssueFields.MODIFIED_AT to Ordering.asc)),
    MODIFIED_AT_DESC(mapOf(IssueFields.MODIFIED_AT to Ordering.desc))
}

data class CreateIssueInput(
    val summary: String,
    val description: String
)

data class CreateActivityInput(
    val comment: String? = null,
    val changeState: IssueState? = null,
    val internal: Boolean = false
)

data class UpdateIssueInput(
    val summary: String? = null,
    val description: String? = null,
    val assignee: UserReference? = null,
    val modifiedAt: Long? = null
)

data class UpdateActivityInput(
    val comment: String? = null,
    val internal: Boolean? = null,
    val modifiedAt: Long? = null
)