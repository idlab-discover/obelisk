# How to export data?

## Prerequisites
Make sure the following prerequisites are met:

1. You have a valid authentication context (see the guide on [authentication](auth.md#how-to-authenticate)).
2. You have read access to at least one [Dataset](../concepts.md#datasets).

## Using Obelisk Data Exports
Obelisk supports exporting large quantities of data as CSV bulk files (see [Data Exports](../concepts.md#data-exports)), using a two-phased approach. Before you can [download the export](#downloading-an-export), you first need to [request the export](#requesting-an-export) using the Obelisk UI or API, so it can be scheduled by the system.

### Requesting an export
A Data Export can potentially target huge amounts of data, hence exports are not executed on-the-fly. When a user requests an export, a job for the export is added to a queue. The queue is then processed according to the FIFO principle by a number of distributed Exporter Services.

!!! info
    Data Exports are also subjected to your [Usage Limits](../concepts.md#usage-limits). Check your account settings to determine the maximum amount of records a single export can include and how many exports can be associated with your account simultaneously.

You can create a new Data Export using the [{{extra.catalog.name}}]({{extra.catalog.url}}/my/exports), by clicking 'New' on the My Exports page and then following the instructions on the screen. Alternatively, you can also use the Catalog API to create the Data Export (e.g. when creating the export from a service or application). To do this, perform the following GraphQL Query:

```graphql
mutation {
    createStream(input: {
        name: "DemoExport1"
        dataRange: {datasets: "<Some Dataset ID>"}
    }) {
        responseCode
        message
        item {
            id
        }
    }
}
```

This GraphQL mutation requests the creation of a new export with the name "DemoExport1" targeting the Dataset with the specified id. Because no specific Metrics are defined in the `dataRange` and no `filter` is present in the request (see [Common Query Properties](../tech_reference/queries.md#common-query-properties) for a reference on the optional request parameters), the Data Export will export all events that are posted to the respective Dataset up until the export request time. By specifying `responseCode` and `message` in the expected return value, the response will include information on whether the request could be completed successfully. If the request is successful, the `id` of the newly created Data Export is returned as well, which we can then use to initiate the download.

Use the [{{extra.catalog.name}}]({{extra.catalog.url}}/my/exports) to check on the status of your export, or query the GraphQL API:

```graphql
{
    me {
        export(id: "<Export ID>") {
            requestedOn
            status {
                recordsEstimate
                recordsProcessed
                status
            }
            result {
                completedOn
                compressedSizeInBytes
            }
        }
    }
}
```

The response to this request will include:

* The timestamp on which the export was requested
* The current status of the export, in terms of expected number of records, number of records processed up until now and the status identifier (`QUEUING`, `GENERATING`, `CANCELLED`, `COMPLETED` or `FAILED`).
* Information on the result of the export, only when the status of the export is `COMPLETED`, otherwise the value of this attribute will be `null`.

### Downloading an export

To download the export, send an Authenticated GET request to [{{extra.obelisk.url}}/api/v3/data/exports/{exportId}]({{extra.obelisk.url}}/api/v3/data/exports/{exportId}) replacing `{exportId}` with the id returned by the Export request.

--8<-- "snippets/glossary.md"