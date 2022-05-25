# Standards Compatibility

## DCAT-AP
The DCAT Application Profile for data portals in Europe (DCAT-AP) is a specification based on the Data Catalogue Vocabulary (DCAT) developed by W3C. It provides a standard for describing public sector datasets in Europe to enable the exchange of descriptions of datasets among data portals. DCAT-AP allows:

* **Data catalogues** to describe their dataset collections using a standardised description, while keeping their own system for documenting and storing them.
* **Content aggregators**, such as the [European Data Portal](https://www.europeandataportal.eu/en/homepage), to aggregate such descriptions into a single point of access. 
* **Data consumers** to more easily find datasets through a single point of access.

It is clear DCAT-AP focuses on improving discoverability of data by providing a common way for describing and exposing meta-data, so we integrated this specification according to this vision. This implies that:

* Obelisk provides a DCAT-AP compatible HTTP endpoint that describes the Datasets that are available in the Obelisk instance, according to the specified description format. This endpoint is available (without authentication[^1]) at {{extra.obelisk.url}}/api/v3/catalog/dcat
* Obelisk auto-generates the DCAT-AP meta-data for the Dataset based on internal meta-data information Obelisk already stores such as the Dataset name, description, license type, etc[^2].

!!! info "The manager of a Dataset in Obelisk remains in control of whether the Dataset is published via DCAT-AP"
    This configuration option is tied to the internal Obelisk `published` attribute (which determines if the Dataset can be discovered by other Obelisk users, who can then request access) and can be toggled on the Dataset settings page.

!!! warning "The DCAT-AP specification is purely a meta-data interoperability specification"
    Users will not be able to access the actual data via the DCAT-AP interface. Once an Obelisk Dataset is discovered through DCAT-AP (e.g. via an Open Data portal), the User will need to interface with Obelisk directly to _1) request access to the Dataset_ and _2) use the Obelisk Catalog or API to export or retrieve the data_.

[^1]: For optimal integration with DCAT-enabled content aggregators.
[^2]: If important meta-data information is missing, you can report this via the Issue Tracker!

## NGSI-LD
NGSI-LD is a Group Specification developed by ETSI ISG CIM, intended to define an API to provide, consume and subscribe to context information in multiple scenarios and involving multiple stakeholders. It enables close to real-time access to information coming from many different sources (not only IoT).

**Obelisk provides a partial implementation of [version 1.2.2](https://www.etsi.org/deliver/etsi_gs/CIM/001_099/009/01.02.02_60/gs_CIM009v010202p.pdf) of this specification.**

!!! info
    In the context of the [ODALA project](https://oascities.org/odala-developing-the-future-of-smart-cities-communities/) efforts were made to implement some new features that are included in [version 1.5.1](https://www.etsi.org/deliver/etsi_gs/CIM/001_099/009/01.05.01_60/gs_cim009v010501p.pdf) of the NGSI-LD specification. These features include:
    
    * Support for retrieving the total entity count when executing temporal queries via `GET /temporal/entities` (the count is reported as a header value for `NGSILD-Results-Count` if the request includes the query parameter `count=true`).
    * The temporal query endpoint now also supports two-dimensional pagination (as described in section 6.3.10), by including a `Content-Range` header in the HTTP response when applicable.
    * We've implemented the new metadata API endpoints (`GET /types`, `GET /types/{type}`, `GET /attributes` & `GET /attributes/{attrId}`), which allows discovering the data that is available from the NGSI system.

    We hope to incorporate the other changes made between 1.2.2 and 1.5.1 as soon as possible.

!!! question "How to decide on when to use the NGSI-LD compatible interfaces vs. the Obelisk native API?"
    Our recommendation is to only use the NGSI-LD interfaces when you are sure that NGSI-LD compatibility is required for your project, or will be required in the short-term future.

    Using the Obelisk native APIs, you have more efficient data modeling options (e.g. optimized storage for numerical or boolean data [^3]), more querying options (e.g. the ability to query across Datasets) and better development support.
    
    Applications that will be ingesting large amounts of data (e.g. from a high-frequency sensor) should always use the Obelisk native API, as these were designed with scalability and throughput in mind and we cannot guarantee the same performance on the NGSI-LD interfaces.

The remainder of this section documents the Obelisk implementation (Which operations are supported? Which are not? What type of queries can you perform? Etc).

### General
When implementing a specification, there is always room for interpretation, more so with a young specification such as NGSI-LD. Additionally, the Obelisk architecture and core implementation were already completed when the decision was made to make Obelisk NGSI-LD compliant.

NGSI-LD and Obelisk share the intent of having a way for data-oriented applications to store, query and subscribe to data, allowing new applications and integrations to thrive upon this eco-system of data. Obelisk approaches this goal by providing a practical solution, based on best practices extracted from the industry and academia. NGSI-LD approaches the goal from a standards perspective and aims to make solutions such as Obelisk and other technology silos compatible with another by imposing a theoretical specification to adhere to.

Some important considerations can be summarized as follows:

#### Multi-tenancy & Access control
Obelisk supports multi-tenancy by design: individual parties can interact with the same Obelisk instance in a completely isolated way from other users. The NGSI-LD specification does not specify multi-tenancy.

To handle this misalignment, we decided to let each Obelisk Dataset expose its own NGSI-LD compliant broker at the following base URI: {{extra.obelisk.url}}/api/v3/ext/ngsi/{datasetId}/ngsi-ld/v1.

This solves a number of challenges in mapping Obelisk to NGSI-LD, including how access control should be handled. The NGSI-LD specification does not specify how authentication or access control should be implemented. Organisations using existing NGSI implementations will sometimes revert to setting up a simple authentication layer (e.g. Basic Auth) with a binary access control strategy, i.e., upon successful authentication, the client gets access to all the data. By exposing the NGSI-LD broker from a Dataset perspective, we can simplify access control on this broker to the access control that was configured on the Dataset (by the Dataset manager). A system-wide Obelisk NGSI-LD broker would be too complex to implement in terms of access control.

#### Cross-compatibility with Obelisk native data
Obelisk supports limited one-way compatibility between the NGSI-LD organized data and Obelisk native data:

* Data ingested into Obelisk using the NGSI-LD format, can be queried, streamed and exported using the native Obelisk APIs. However, the data values will be wrapped in a fully qualified JSON document according to the NGSI-LD specification. _Be prepared to deal with this overhead when choosing this approach!_
* Data ingested into Obelisk using the native Obelisk Ingest API, cannot be queried using the NGSI-LD APIs or used in NGSI-LD subscriptions.

#### Authentication
To interact with the NGSI-LD APIs that are exposed for a specific Dataset, you must follow the same authentication procedure as for the native Obelisk APIs (see [How to authenticate?](../guides/auth.md#how-to-authenticate)). The credentials that you are using to request a token must have read or write access on the specific Dataset, depending on which NGSI-LD operation you wish to use.

#### Rate Limiting
The [Obelisk rate limiting](../concepts.md#rate-limiting) mechanism also applies to interactions via the NGSI-LD APIs. All NGSI-LD read operations will count for the maximum number of complex event queries you can perform (as NGSI Entities are backed by complex Metric types in Obelisk) and all write operations (post, put, patch) will count for the maximum number of complex events that can be stored AND streamed (this is done because every NGSI-LD change qualifies for being sent as a notification, the NGSI-LD equivalent of [Obelisk Data Streams](../guides/data_stream.md).

[^3]: Obelisk stores booleans, numbers and number arrays using double delta encoding, which leads to efficient compression of the data with a minimal impact on read times. Meta-data is stored separately in dictionaries, so they are only stored once and then referred to using compact references. For NGSI-LD on the other hand, this type of data will end up as JSON documents including the full meta-data, which takes up a lot more space.

### Operations
This section gives an overview of which NGSI-LD operations are (partially) supported by the Obelisk implementation.

#### entities

| API Endpoint | Purpose | Is Supported? |
| --- | --- | --- |
| `POST /entities/` | Creating entities | Yes[^4] |
| `GET /entities/` | Query entities | Yes (Partially)[^5] |
| `GET /entities/{entityId}` | Entity retrieval by id | Yes |
| `DELETE /entities/{entityId}` | Entity deletion by id | Yes (Partially)[^6] |
| `POST /entities/{entityId}/attrs/` | Append entity Attributes | Yes |
| `PATCH /entities/{entityId}/attrs/` | Append entity Attributes | Yes |
| `PATCH /entities/{entityId}/attrs/{attrId}` | Attribute partial update | Yes |
| `DELETE /entities/{entityId}/attrs/{attrId}` | Attribute delete | Yes (Partially)[^6] |

[^4]: The id of an Entity should be globally unique for the Dataset.
[^5]: See [Queries](#query-capabilities) to learn about the query limitations
[^6]: Delete operations are limited to type or attributes set by the same User performing the Delete operations (_You can only delete your own data!_).

#### subscriptions

| API Endpoint | Purpose | Is Supported? |
| --- | --- | --- |
| `POST /subscriptions/` | Subscription creation | Yes |
| `GET /subscriptions/` | Subscription list retrieval | Yes (Partially)[^7] |
| `GET /subscriptions/{subscriptionId}` | Subscription retrieval by id | Yes (Partially)[^7] |
| `PATCH /subscriptions/{subscriptionId}` | Subscription update by id | Yes |
| `DELETE /subscriptions/{subscriptionId}` | Subscription deletion by id | Yes (Partially)[^6] |

[^7]: Our implementation of NGSI-LD subscriptions is backed by Obelisk [Data Streams](../concepts.md#data-streams) and some of the more advanced Subscription features are not available, for example: Additional members of the NotificationParams that provide dynamic information about the state of the Subscription, such as `timesSent`, `lastNotification`, `lastFailure` and `lastSuccess` are currently not supported!

#### csourceRegistrations

Obelisk currently does not support csourceRegistrations. The following endpoints are **NOT** implemented:

* `POST /csourceRegistrations/`
* `GET /csourceRegistrations/`
* `GET /csourceRegistrations/{registrationId}`
* `PATCH /csourceRegistrations/{registrationId}`
* `DELETE /csourceRegistrations/{registrationId}`

#### csourceSubscriptions

Obelisk currently does not support csourceSubscriptions. The following endpoints are **NOT** implemented:

* `POST /csourceSubscriptions/`
* `GET /csourceSubscriptions/`
* `GET /csourceSubscriptions/{subscriptionId}`
* `PATCH /csourceSubscriptions/{subscriptionId}`
* `DELETE /csourceSubscriptions/{subscriptionId}`

#### entityOperations

| API Endpoint | Purpose | Is Supported? |
| --- | --- | --- |
| `POST /entityOperations/create` | Batch Entity creation | Yes |
| `POST /entityOperations/upsert` | Batch Entity create or update (upsert) | Yes |
| `POST /entityOperations/update` | Batch Entity update | Yes |
| `POST /entityOperations/delete` | Batch Entity deletion | Yes |

#### temporal

!!! caution "Context Information and Temporal Information are linked!"
    While the specification is ambiguous on this topic, for the Obelisk implementation we made a clear decision to link the Context and Temporal Information APIs. This implies that all updates performed using the Context Information API are automatically recorded as temporal evolutions of a specific Entity. E.g. when you update an attribute for an Entity using the Context Information API, you will find at least two entries for this Entity when querying the Temporal Information API: _1) the state of the Entity before the update and 2) the state of the Entity after the update._

| API Endpoint | Purpose | Is Supported? |
| --- | --- | --- |
| `POST /temporal/entities/` | Temporal Representation of Entity creation |  Yes |
| `GET /temporal/entities/` | Query temporal evolution of Entities |  Yes (Partially)[^5] |
| `GET /temporal/entities/{entityId}` | Temporal Representation of Entity retrieval by id | Yes |
| `DELETE /temporal/entities/{entityId}` | Temporal Representation of Entity deletion by id | Yes (Partially)[^6] |
| `POST /temporal/entities/{entityId}/attrs/` | Temporal Representation of Entity Attribute instance addition | Yes |
| `DELETE /temporal/entities/{entityId}/attrs/{attrId}` | Attribute from Temporal Representation of Entity deletion | Yes (Partially)[^6] |
| `PATCH /temporal/entities/{entityId}/attrs/{attrId} /{instanceId}` | Attribute Instance update | Yes |
| `DELETE /temporal/entities/{entityId}/attrs/{attrId} /{instanceId}` | Attribute Instance deletion by instance id | Yes (Partially)[^6] |


### Query capabilities
The Obelisk NGSI-LD implementation does not support all query functionality that is defined in the NGSI-LD specification. We can only support the type of queries for which there is a direct Obelisk equivalent.



#### Geo-spatial queries
Geo-spatial queries are limited to `Point` and `Polygon` geometries with restrictions on the type of Geo-relationships that can be interpreted:

* `Point` geometry can be used with `equals` and `near` Geo-relationships
* `Polygon` geometry can be used with `within` and `disjoint` Geo-relationships

#### Temporal queries
NGSI-LD temporal queries operate by default on the `observedAt` Temporal Property, but this can be altered using the `timeproperty` field when composing a temporal query. Unfortunately, we cannot support this with Obelisk at the moment and our implementation will only accept temporal queries that omit `timeproperty` or have it set to `observedAt`.

### Multi-attribute support
Obelisk partially supports Entities with multi-attributes (see section 4.5.5 in the NGSI-LD specification).

> For each Entity, there can be Properties and Relationships that simultaneously have more than one instance. In the case of Properties, there may be more than one source at a time that provides a Property instance, e.g. based on independent sensor measurements with different quality characteristics.

> To be able to explicitly manage such multi-attributes, the optional datasetId property is used, which is of datatype URI. If a datasetId is provided when creating, updating, appending or deleting Properties and Relationships, only instances with the same datasetId are affected, leaving instances with another datasetId or an instance without a datasetId untouched.

For example, the following Entity can be created and interpreted by the Obelisk NGSI-LD implementation:

```json
{
    "id":"urn:ngsi-ld:Vehicle:A4567",
    "type":"Vehicle",
    "speed#1":{
        "type":"Property",
        "value":55,
        "source":{
            "type":"Property",
            "value":"Speedometer"
        },
        "datasetId":"urn:ngsi-ld:Property:speedometerA4567-speed"
    },
    "speed#2":{
        "type":"Property",
        "value":54.5,
        "source":{
            "type":"Property",
            "value":"GPS"
        },
        "datasetId":"urn:ngsi-ld:Property:gpsBxyz123-speed"
    },
    "@context":[
        {
            "speed#1":"http://example.org/speed",
            "speed#2":"http://example.org/speed",
            "source":"http://example.org/hasSource"
        },
        "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
    ]
}
```

The Obelisk implementation will treat the speed properties as separate instance and respect the `datasetId` as a target identifier when Properties are created or modified.

!!! caution
    The attribute `datasetId` used here in the context of NGSI-LD multi-attributes, should not be confused with the ID of a Dataset in Obelisk!

There is however one limitation: when querying the Entity, Obelisk will not be able to return the Entity in the same format as seen in the example above (with the `speed#1` and `speed#2` identifiers). Obelisk uses standard JSON-LD serialization libraries and upon returning the Entity with the exact same context as shown above, our serializer will compact both speed Properties using the first namespace that matches (which makes sense if you think about it). The resulting JSON-LD output will look like this:

```json
{
  "id" : "urn:ngsi-ld:Vehicle:A4567",
  "type" : "Vehicle",
  "speed#1" : [ {
    "type" : "Property",
    "source" : {
      "type" : "Property",
      "value" : "Speedometer"
    },
    "datasetId" : "urn:ngsi-ld:Property:speedometerA4567-speed",
    "value" : 55,
    "observedAt" : "2020-06-02T12:18:01.5543319Z"
  }, {
    "type" : "Property",
    "source" : {
      "type" : "Property",
      "value" : "GPS"
    },
    "datasetId" : "urn:ngsi-ld:Property:gpsBxyz123-speed",
    "value" : 54.5,
    "observedAt" : "2020-06-02T12:18:01.5573648Z"
  } ],
  "@context" : [ {
    "speed#1" : "http://example.org/speed",
    "speed#2" : "http://example.org/speed",
    "source" : "http://example.org/hasSource"
  }, "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld" ]
}
```

This limitation was presented to the researchers at IDLAB that were interested in using this feature and was deemed acceptable.

--8<-- "snippets/glossary.md"
