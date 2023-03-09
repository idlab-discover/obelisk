# Obelisk service overview

## Core services

### int-dataset-streamer

This service works in the background to process all events posted to the main `metric-event` topic, by reading out the target dataset for each event, so it can be posted to a dataset specific topic. The dataset specific topics are then used for the streaming functionality of Obelisk (see [pub-streaming-service](#pub-streaming-service)).

Note: *this background process is an optimization, as it offloads the [pub-ingest-service](#pub-ingest-service) from having to route the incoming events.*

### int-sink-service

This service works in the background, reading events from the main `metric-event` and store-only topics. The incoming events are grouped in batches, which are persisted with the data store (Clickhouse by default).

### pub-auth-service

This service takes care of authentication for both users and clients, based on OAuth 2.0 and OpenID Connect. Currently, we only support Google as an identity provider.

### pub-ingest-service

This service provides an HTTP API that allows clients to post data batches to Obelisk. Incoming data is validated and transformed into events that are posted on the message broker (Pulsar by default).

### pub-query-service

This service provides an HTTP API that allows clients to post queries, for fetching both raw and aggregated data.

### pub-streaming-service

This service provides an HTTP API that allows clients to stream data. The streams are implemented using [Server-Sent-Events (SSE)](https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events), backed by consumers on the message broker.

## UI Components

### web-apiconsole

This Web application allows users to authenticate with Obelisk and then perform API requests. It embeds a [GraphiQL](https://github.com/graphql/graphiql) client, which can be used to interactively navigate the Catalog GraphQL API.

### web-catalog

This Web application provides the main user interface for Obelisk. It allows users to navigate the platform (e.g. discovering datasets) and perform self-administration (e.g. creating new datasets, setting up client credentials). It also includes an administrator interface.

## Optional services

The following services add additional functionality and can be omitted without impacting the core functionality of the platform.

### int-stats-collector

This service runs a background process that periodically extracts per dataset statistics from the data store, and exposes these via instrumentation towards the monitoring stack (Prometheus by default). Some dashboard panels will be empty in the [web-catalog](#web-catalog) when not deploying this service.

### pub-export-service

This service implements batch data exports in CSV format. It is used by data scientist that require access to large datasets in cases where using the Obelisk query API is not practical.

### pub-issue-service

This service implements a basic issue tracker, which can be used by platform operators to assist users directly from the [web-catalog](#web-catalog). When not deploying this service, the assistance button in the Catalog UI will not operate properly.

### pub-monitor-service

This service implements an end-to-end health check. Data events are ingested periodically, and the various steps in the dataflow are then monitored internally (storage, metadata & data querying, streaming). The health check data is exposed via an HTTP API that is used by the Catalog UI to show the platform status.
