package idlab.obelisk.services.pub.ngsi

import io.vertx.core.json.JsonObject

object Constants {
    const val OBLX_INSTANCE_BASE_URI = "urn:obelisk:attributes:"
    const val OBLX_GENERIC_TYPE_URN = "urn:obelisk:types:Any"
    const val OBLX_NOTIFICATION_URN_BASE = "urn:obelisk:notifications:"
    const val OBLX_TYPES_LIST_URN_BASE = "urn:obelisk:entity-type-lists:"
    const val OBLX_ATTRIBUTE_LIST_URN_BASE = "urn:obelisk:attribute-lists:"

    const val PULSAR_METRICS_SUBSCRIPTION = "ngsi_ld_subscription_matcher"
    const val PULSAR_DATASTREAM_CONTROL_SUBSCRIPTION = "${PULSAR_METRICS_SUBSCRIPTION}_control"
    const val PULSAR_NOTIFICATION_TOPIC = "ngsi_ld_notifications_queue"
    const val PULSAR_NOTIFIER_SUBSCRIPTION = "ngsi_ld_notifier"

    const val JSON_LD_CONTENT_TYPE = "application/ld+json"
    const val TYPE_TAG_PREFIX = "_type="
    const val NGSI_MARKER_TAG = "_origin=ngsi-ld"
    const val MODIFIED_AT_TAG_PREFIX = "_modifiedAt="

    const val DEFAULT_LD_NAMESPACE = "https://uri.etsi.org/ngsi-ld/default-context"
    const val DEFAULT_LD_CONTEXT_URI = "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
    const val LD_CONTEXT = "@context"
    val DEFAULT_LD_CONTEXT = JsonObject().put(LD_CONTEXT, DEFAULT_LD_CONTEXT_URI)
    const val FQ_ENTITY_ID = "@id"
    const val FQ_ENTITY_TYPE = "@type"
    const val FQ_INSTANCE_ID = "https://uri.etsi.org/ngsi-ld/instanceId"
    const val FQ_VALUE = "@value"
    const val FQ_DATE_TIME = "https://uri.etsi.org/ngsi-ld/DateTime"
    const val FQ_HAS_VALUE = "https://uri.etsi.org/ngsi-ld/hasValue"
    const val FQ_HAS_OBJECT = "https://uri.etsi.org/ngsi-ld/hasObject"
    const val FQ_OBSERVED_AT = "https://uri.etsi.org/ngsi-ld/observedAt"
    const val FQ_CREATED_AT = "https://uri.etsi.org/ngsi-ld/createdAt"
    const val FQ_MODIFIED_AT = "https://uri.etsi.org/ngsi-ld/modifiedAt"
    const val FQ_PROPERTY_TYPE = "https://uri.etsi.org/ngsi-ld/Property"
    const val FQ_DATASET_ID = "https://uri.etsi.org/ngsi-ld/datasetId"
    const val FQ_SUBSCRIPTION_NOTIFICATION = "https://uri.etsi.org/ngsi-ld/notification"
    const val FQ_NOTIFICATION_ATTRIBUTES = "https://uri.etsi.org/ngsi-ld/attributes"

    const val HTTP_HEADER_RESULT_COUNT = "NGSILD-Results-Count"
    const val HTTP_HEADER_ATTRIBUTES_COUNT = "NGSILD-Attributes-Count"

    const val DEFAULT_LIMIT = 250
    const val MAX_LIMIT = 2500
    const val LAST_N_LIMIT = 100
}
