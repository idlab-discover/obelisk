# How to stream data?

## Prerequisites
Make sure the following prerequisites are met:

1. You have a valid authentication context (see the guide on [authentication](auth.md#how-to-authenticate)).
2. You have read access to at least one [Dataset](../concepts.md#datasets).

## Using Obelisk Data Streams
Obelisk supports push-based communication in the form of [Data Streams](../concepts.md#data-streams), using a two-phased approach. Before you can [connect to a stream](#connecting-to-a-stream), you first need to register this stream using the [Catalog API](#setting-up-a-stream).

### Setting up a stream
Data Streams require active processing in the Obelisk backend, and as such have more impact on the overall system load compared to the poll-based Query APIs (these are stateless). That is why we require you to register your Data Streams upfront and why we only allow a limited number of streams to be active at the same time for a specific account.

You can create a new Data Stream using the [{{extra.catalog.name}}]({{extra.catalog.url}}/my/streams), by clicking 'New' on the My Streams page and then following the instructions on the screen. Alternatively, you can also use the Catalog API to create the Data Stream (e.g. when creating the stream from a service or application). To do this, perform the following GraphQL Query[^1]:

```graphql
mutation {
    createStream(input: {
        name: "DemoStream1"
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

This GraphQL mutation requests the creation of a new stream with the name "DemoStream1" targeting the Dataset with the specified id. Because no specific Metrics are defined in the `dataRange` and no `filter` is present in the request (see [Common Query Properties](../tech_reference/queries.md#common-query-properties) for a reference on the optional request parameters), the Data Stream will be streaming all events that are posted to the respective Dataset. By specifying `responseCode` and `message` in the expected return value, the response will include information on whether the request could be completed successfully. If the request is successful, the `id` of the newly created Data Stream is returned as well, which we can then use to start streaming.

[^1]: The Catalog GraphQL API is available at {{extra.obelisk.url}}/api/v3/catalog/graphql. Documentation and an interactive console can be found at: {{extra.obelisk.url}}/apiconsole.

### Connecting to a stream

The actual Data Streams are implemented using [Server-Sent-Events (SSE)](https://html.spec.whatwg.org/multipage/server-sent-events.html) and can be initiated by performing an (authenticated) HTTP GET request to {{extra.obelisk.url}}/api/v3/data/streams/{streamId}. The request header `Accept: text/event-stream` must be included!

A chunked HTTP response is returned that will not terminate (unless an error occurs). Each event posted to the Dataset[^2] that matches the predefined Data Stream configuration, is written to the HTTP response followed by two newlines `\n\n` (as per Server-Sent-Events specification). It is straightforward to write your own SSE parser, but existing libraries can be found for multiple programming languages:

* Javascript - https://github.com/EventSource/eventsource
* Python - https://pythonhosted.org/eventsource/#client-module
* Java - https://github.com/launchdarkly/okhttp-eventsource

[^2]: Note that the data producer must enable streaming when ingesting data (see [`mode` query parameter](https://obelisk.docs.apiary.io/#/reference/data-api/ingesting-data)). By default, streaming is enabled.

--8<-- "snippets/glossary.md"