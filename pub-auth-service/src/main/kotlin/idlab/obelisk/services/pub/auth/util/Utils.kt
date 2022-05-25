package idlab.obelisk.services.pub.auth.util

import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.ClientUpdate
import idlab.obelisk.definitions.catalog.codegen.DatasetField
import idlab.obelisk.definitions.catalog.codegen.RoleField
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.utils.service.reactive.firstOrEmpty
import io.reactivex.Completable
import io.reactivex.CompletableSource
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.reactivex.ext.auth.jwt.JWTAuth
import java.util.*

private const val OBELISK_WEB_CLIENTS_ID = "0" // magically set to 0 because everybody hates strings
private const val OBELISK_WEB_CLIENTS_NAME = "obelisk-web-clients"

object Utils {
    private var JWT_AUTH_PROVIDER: JWTAuth? = null;

    fun generateRandomString(): String {
        val leftLimit = 48 // numeral '0'
        val rightLimit = 122 // letter 'z'
        val targetStringLength = 16
        val random = Random()
        return random.ints(leftLimit, rightLimit + 1)
            .filter { (it <= 57 || it >= 65) && (it <= 90 || it >= 97) }
            .limit(targetStringLength.toLong())
            .collect(
                { StringBuilder() },
                { obj, codePoint -> obj.appendCodePoint(codePoint) }) { obj, s -> obj.append(s) }
            .toString()
    }

    fun redirectUriMatches(registeredRedirectUris: Collection<String>, redirectUri: String): Boolean {
        return registeredRedirectUris.any {
            if (it == redirectUri) {
                true
            } else if (it.endsWith("/*")) {
                val pattern = it.substring(0, it.length - 2)
                redirectUri.startsWith(pattern)
            } else {
                false
            }
        }
    }

    fun fromToken(token: Token): String {
        return Json.encode(token)
    }

    /**
     * Sets up the Obelisk Web Client.
     *
     * @param metaStore
     * @return
     */
    fun obeliskWebClientSetup(metaStore: MetaStore, config: OblxConfig): Completable? {
        return metaStore.getClient(OBELISK_WEB_CLIENTS_ID)
            .toSingle()
            .flatMapCompletable { (id, userId) ->
                metaStore.updateClient(
                    id!!,
                    ClientUpdate(
                        name = OBELISK_WEB_CLIENTS_NAME,
                        confidential = false,
                        onBehalfOfUser = true,
                        scope = setOf(Permission.READ, Permission.WRITE, Permission.MANAGE),
                        redirectURIs = listOf(
                            "http://localhost:8000/*",
                            "http://localhost:4200/*",
                            "http://localhost:8080/api/v3/auth/*",
                            "${config.authPublicUri}/catalog/*",
                            "${config.authPublicUri}/apiconsole/*",
                            // TODO: After migration, these next to hardcoded lines can be removed
                            "https://rc.obelisk.ilabt.imec.be/catalog/*",
                            "https://rc.obelisk.ilabt.imec.be/apiconsole/*",
                        )
                    )
                )
            }
    }

    fun testMethod(metaStore: MetaStore): CompletableSource? {
        return if ("true" == System.getenv("POPULATE_DEMO_DATA")) {
            Observable.range(1, 10)
                .map { i: Int -> "test.dataset." + i.toString().padStart(3, '0') }
                .concatMapSingle { datasetName: String ->
                    metaStore.queryDatasets(Eq(DatasetField.NAME, datasetName)).firstOrEmpty()
                        .toSingle()
                        .map<String>(Dataset::id)
                        .onErrorResumeNext { err: Throwable? ->
                            metaStore
                                .createDataset(
                                    Dataset(
                                        name = datasetName,
                                        description = "This is the description of $datasetName , which is just one of the many different test datasets that have been temporarily created to help with UI design",
                                        published = true,
                                        keywords = setOf("test", "generated"),
                                        license = "MIT",
                                        contactPoint = "thomas.dupont@ugent.be"
                                    )
                                )
                                .flatMap { datasetId ->
                                    metaStore
                                        .createRole(
                                            Role(
                                                name = "dataset_owner",
                                                datasetId = datasetId,
                                                grant = Grant(Permission.all())
                                            )
                                        )
                                        .onErrorResumeNext(metaStore.queryRoles(
                                            Eq(RoleField.DATASET_ID, datasetId),
                                            5000
                                        )
                                            .map { page -> page.items.find { r -> r.name == "dataset_owner" }!!.id })
                                        .flatMap { roleId -> Single.just(Pair(datasetId, roleId)) }
                                        .flatMap { pair ->
                                            metaStore
                                                .getUser("0")
                                                .toSingle()
                                                .flatMap { admin ->
                                                    metaStore.updateUser(
                                                        admin.id!!, UserUpdate(
                                                            datasetMemberships = admin.datasetMemberships.plus(
                                                                listOf(
                                                                    DatasetMembership(
                                                                        pair.first,
                                                                        setOf(pair.second)
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ).toSingleDefault(admin.id!!)
                                                }
                                        }
                                }
                        }
                }
                .ignoreElements()
        } else {
            Completable.complete()
        }
    }
}