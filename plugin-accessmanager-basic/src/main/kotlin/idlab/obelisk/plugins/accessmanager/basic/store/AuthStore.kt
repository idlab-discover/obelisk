package idlab.obelisk.plugins.accessmanager.basic.store

import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.plugins.accessmanager.basic.store.model.LoginState
import idlab.obelisk.plugins.accessmanager.basic.store.model.Session
import io.reactivex.Maybe
import io.reactivex.Single
import org.redisson.api.map.event.MapEntryListener

interface AuthStore : TokenModifyStore {

    override fun rxGetToken(id: String?): Maybe<Token>

    override fun rxReplaceToken(oldToken: Token, newToken: Token): Single<Boolean>

    fun rxSaveToken(token: Token): Single<Boolean>

    fun rxRemoveToken(opaqueToken: String?): Single<Boolean>

    fun rxGetLogin(id: String?): Maybe<LoginState>

    fun rxSaveLogin(key: String?, login: LoginState): Single<Boolean>

    fun rxRemoveLogin(key: String?): Single<Boolean>

    fun rxReplaceLogin(key: String?, oldLogin: LoginState, newLogin: LoginState): Single<Boolean>

    /**
     * Get session by a generated sid.
     *
     * This is an indirect retrieval via sid->sub map, to sessions.get(sub).
     *
     * If sub key does not exist in sessions, the entry of sid->sub is removed.
     */
    fun rxGetSessionBySid(sid: String?): Maybe<Session>

    /**
     * Saves the current session in a sub->session map.
     */
    fun rxSaveSession(session: Session): Single<Boolean>

    /**
     * Generates a new SessionId to be passed to the outside world.
     *
     * It will be added to the sid->sub map.
     * Expiration of this entry follows the same TTLs of Session.
     */
    fun rxGetNewSessionSid(session: Session): Single<String>

    /**
     * Get session by sub
     *
     * This is a direct retrieval of sub->session     */
    fun rxGetSession(sub: String?): Maybe<Session>

    fun rxRemoveSessionBySub(sub: String?): Single<Boolean>

}