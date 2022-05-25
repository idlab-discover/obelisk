package idlab.obelisk.plugins.accessmanager.basic.store

import idlab.obelisk.definitions.catalog.Token
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single

interface TokenModifyStore {

    /**
     * Return a token by its id, which is the opaque access token.
     */
    fun rxGetToken(id: String?): Maybe<Token>

    /**
     * Replace an old token with a new token, for the same key
     */
    fun rxReplaceToken(oldToken: Token, newToken: Token): Single<Boolean>

    /**
     * Save a batch of access token -> Token entries, possibly overwriting entries.
     */
    fun rxBatchPut(newTokenEntries: MutableMap<String, Token>): Completable

    /**
     * Return a map of subs that have a session active to their access tokens (opaque tokens).
     *
     * @return A Map from sub->access token?
     */
    fun rxGetAccessTokensBySubs(subs: Set<String>?): Single<Map<String,String?>>

    /**
     * Return a set of Tokens for the subs that have a session active.
     *
     * @return A Set of Tokens
     */
    fun rxGetTokensBySubs(subs: Set<String>?): Single<Set<Token>>
}