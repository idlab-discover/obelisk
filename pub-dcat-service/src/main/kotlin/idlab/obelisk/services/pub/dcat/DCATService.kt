package idlab.obelisk.services.pub.dcat

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.Dataset
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.catalog.codegen.DatasetField
import idlab.obelisk.definitions.catalog.codegen.DatasetMembershipField
import idlab.obelisk.definitions.catalog.codegen.RoleField
import idlab.obelisk.definitions.catalog.codegen.UserField
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.utils.Base64.encodeAsBase64
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.reactivex.ext.web.Router
import org.apache.commons.validator.routines.EmailValidator
import org.apache.commons.validator.routines.UrlValidator
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.sparql.vocabulary.FOAF
import org.apache.jena.vocabulary.*
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

const val CATALOG_TITLE = "CATALOG_TITLE"
const val CATALOG_TITLE_DEFAULT = "Obelisk"
const val CATALOG_DESCRIPTION = "CATALOG_DESCRIPTION"
const val CATALOG_DESCRIPTION_DEFAULT = "A catalog of datasets published by Obelisk."
const val CATALOG_PUBLISHER_NAME = "CATALOG_PUBLISHER_NAME"
const val CATALOG_PUBLISHER_NAME_DEFAULT = "IDLab"
const val CATALOG_PUBLISHER_URL = "CATALOG_PUBLISHER_URL"
const val CATALOG_PUBLISHER_URL_DEFAULT = "https://idlab.technology/"
const val LANG_TAG_ENG = "en"
const val CREATIVE_COMMONS_1_LICENSE = "https://data.vlaanderen.be/id/licentie/creative-commons-zero-verklaring/v1.0"
const val DATASET_LANDING_PAGE_BASEURI = "DATASET_LANDING_PAGE_BASEURI"
const val DATASET_LANDING_PAGE_BASEURI_DEFAULT = "http://localhost:8000/catalog/ds/"

const val DATASET_QUERY_PAGE_SIZE = 100
const val HTTP_BASE_PATH = "/catalog/dcat"

// Will be used for etag calculation (in general service reboot should invalidate the caches)
val startupTimestamp = System.currentTimeMillis()

fun main(args: Array<String>) {
    OblxLauncher.with(OblxBaseModule(), MongoDBMetaStoreModule()).bootstrap(DCATService::class.java)
}

@Singleton
class DCATService @Inject constructor(
    private val config: OblxConfig,
    private val router: Router,
    private val metaStore: MetaStore
) : OblxService {
    private val baseUri = config.authPublicUri
    private val catalogTitle = config.getString(CATALOG_TITLE, CATALOG_TITLE_DEFAULT)
    private val catalogDescription = config.getString(CATALOG_DESCRIPTION, CATALOG_DESCRIPTION_DEFAULT)
    private val catalogPublisherName = config.getString(CATALOG_PUBLISHER_NAME, CATALOG_PUBLISHER_NAME_DEFAULT)
    private val catalogPublisherUrl = config.getString(CATALOG_PUBLISHER_URL, CATALOG_PUBLISHER_URL_DEFAULT)
    private val datasetLandingPageBaseURI =
        config.getString(DATASET_LANDING_PAGE_BASEURI, DATASET_LANDING_PAGE_BASEURI_DEFAULT).trimEnd('/')
    private val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, "/catalog/dcat")
    private val catalogUri = "${baseUri}$basePath"
    private val mailAddressValidator = EmailValidator.getInstance()
    private val urlValidator = UrlValidator.getInstance()

    override fun start(): Completable {
        val httpPath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)

        router.head(httpPath).handler { ctx ->
            ctx.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "*").end()
        }

        router.get(httpPath).handler { ctx ->
            val model = ModelFactory.createDefaultModel()
                .addDefaultNamespaces()
                .addCatalog()

            // Fetch and convert all public Datasets
            unpage { cursor ->
                metaStore.queryDatasets(
                    And(
                        Eq(DatasetField.PUBLISHED, true),
                        Neq(DatasetField.LOCKED, true)
                    ), DATASET_QUERY_PAGE_SIZE, cursor
                )
            }
                .flatMapSingle { dataset ->
                    if (dataset.contactPoint != null && mailAddressValidator.isValid(dataset.contactPoint)) {
                        Single.just(DatasetWrapper(dataset))
                    } else {
                        unpage { cursor ->
                            metaStore.queryRoles(
                                And(
                                    Eq(RoleField.DATASET_ID, dataset.id!!),
                                    Eq(Field(RoleField.GRANT.toString(), "permissions"), Permission.MANAGE)
                                ),
                                cursor = cursor
                            )
                        }
                            .toList()
                            .flatMap { manageRoles ->
                                unpage { cursor ->
                                    metaStore.queryUsers(
                                        In(
                                            Field(
                                                UserField.DATASET_MEMBERSHIPS,
                                                DatasetMembershipField.ASSIGNED_ROLE_IDS
                                            ), manageRoles.map { it.id!! }.toSet()
                                        ),
                                        cursor = cursor
                                    )
                                }
                                    .map { it.email }
                                    .toList()
                            }
                            .map { DatasetWrapper(dataset, it) }
                    }
                }
                .toList()
                .subscribeBy(
                    onSuccess = { datasets ->
                        ctx.etag((startupTimestamp + datasets.hashCode()).toString().encodeAsBase64())
                        if (ctx.isFresh) {
                            // Client has an up-to-date DCAT file, send 304 NOT MODIFIED
                            ctx.response().setStatusCode(304).end()
                        } else {
                            // Client requests DCAT file for the first time, or has an outdated version, generate the file!
                            datasets.forEach { model.addDataset(it) }
                            ByteArrayOutputStream().use {
                                model.write(it, "N3")
                                ctx.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "*")
                                    .putHeader("Content-Type", "text/turtle").end(it.toString("UTF-8"))
                            }
                        }
                    },
                    onError = writeHttpError(ctx)
                )
        }

        return Completable.complete()
    }

    private fun Model.addDefaultNamespaces(): Model {
        this.setNsPrefix("dcat", DCAT.NS)
        this.setNsPrefix("dct", DCTerms.NS)
        this.setNsPrefix("foaf", FOAF.NS)
        this.setNsPrefix("rdfs", RDFS.uri)
        this.setNsPrefix("vcard", VCARD4.NS)
        return this
    }

    private fun Model.addCatalog(): Model {
        this.createResource(catalogUri)
            .addProperty(RDF.type, DCAT.Catalog)
            .addProperty(DCTerms.title, this.createLiteral(catalogTitle, LANG_TAG_ENG))
            .addProperty(DCTerms.description, this.createLiteral(catalogDescription, LANG_TAG_ENG))
            .addProperty(DCTerms.license, this.getResource(CREATIVE_COMMONS_1_LICENSE))
            .addProperty(
                DCTerms.publisher, this.createResource("$catalogUri/agents/$catalogPublisherName")
                    .addProperty(RDF.type, FOAF.Agent)
                    .addProperty(FOAF.name, this.createLiteral(catalogPublisherName, LANG_TAG_ENG))
                    .addProperty(FOAF.homepage, this.createResource(catalogPublisherUrl))
            )
        return this
    }

    private fun Model.addDataset(datasetWrapper: DatasetWrapper): Completable {
        val dataset = datasetWrapper.dataset
        val catalog = this.getResource(catalogUri)
        val contact = dataset.contactPoint ?: datasetWrapper.fallbackContactEmails?.first() ?: ""

        val distribution = this.createResource("$catalogUri/distributions/${dataset.id}")
            .addProperty(RDF.type, DCAT.Distribution)
            .addProperty(DCTerms.title, this.createLiteral("Obelisk Dataset", LANG_TAG_ENG))
            .addProperty(DCAT.accessURL, this.getResource("$datasetLandingPageBaseURI/${dataset.id}"))
        dataset.license?.takeIf { urlValidator.isValid(it) }
            ?.let { distribution.addProperty(DCTerms.license, this.getResource(it)) }

        val resource = this.createResource("$catalogUri/datasets/${dataset.id}")
            .addProperty(RDF.type, DCAT.Dataset)
            .addProperty(DCTerms.title, this.createLiteral(dataset.name, LANG_TAG_ENG))
            .addProperty(
                DCTerms.description,
                this.createLiteral(
                    dataset.description ?: "No description has been set for this dataset...",
                    LANG_TAG_ENG
                )
            )
            .addProperty(
                DCAT.contactPoint,
                this.createResource("$catalogUri/contacts/${URLEncoder.encode(contact, "UTF-8")}")
                    .addProperty(RDF.type, VCARD4.Kind)
                    .addProperty(VCARD4.hasEmail, this.getResource("mailto:$contact"))
            )
            .addProperty(
                DCTerms.accessRights,
                this.getResource("http://publications.europa.eu/resource/authority/access-right/PUBLIC")
            )
            .addProperty(
                DCAT.distribution, distribution
            )

        dataset.publisher?.let { publisher ->
            val agentResource = this.createResource("$catalogUri/agents/${publisher.name}")
                .addProperty(RDF.type, FOAF.Agent)
                .addProperty(FOAF.name, this.createLiteral(publisher.name, LANG_TAG_ENG))

            publisher.homepage?.takeIf { urlValidator.isValid(it) }
                ?.let { agentResource.addProperty(FOAF.homepage, this.createResource(it)) }

            resource.addProperty(DCTerms.publisher, agentResource)
        }
        dataset.keywords.forEach { resource.addProperty(DCAT.keyword, it) }

        catalog.addProperty(DCAT.dataset, resource)
        return Completable.complete()
    }
}

// Wraps additional info for a Dataset
data class DatasetWrapper(
    val dataset: Dataset,
    val fallbackContactEmails: List<String>? = null
)