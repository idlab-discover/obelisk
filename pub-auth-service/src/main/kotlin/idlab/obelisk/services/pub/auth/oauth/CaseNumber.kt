package idlab.obelisk.services.pub.auth.oauth

enum class CaseNumber {
    /**
     * User via a client-side client
     */
    CLIENT_SIDE_AS_USER,

    /**
     * User via a server-side client
     */
    SERVER_SIDE_AS_USER,

    /**
     * Client-side client as itself
     */
    CLIENT_SIDE,

    /**
     * Server-side client as itself (a.k.a. a service)
     */
    SERVER_SIDE;

    /**
     * Client code is publicly available
     */
    open fun isClientSide(): Boolean {
        return this == CLIENT_SIDE || this == CLIENT_SIDE_AS_USER
    }

    /**
     * Client code is private (confidential)
     */
    open fun isServerSide(): Boolean {
        return this == SERVER_SIDE || this == SERVER_SIDE_AS_USER
    }

    /**
     * Client authenticates as itself
     */
    open fun asClient(): Boolean {
        return this == CLIENT_SIDE || this == SERVER_SIDE
    }

    /**
     * Client authenticates on behalf of user
     */
    open fun asUser(): Boolean {
        return this == CLIENT_SIDE_AS_USER || this == SERVER_SIDE_AS_USER
    }
}