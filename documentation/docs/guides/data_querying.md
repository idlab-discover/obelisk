# How to query data?

## Prerequisites
Make sure the following prerequisites are met:

1. You have a valid authentication context (see the guide on [authentication](auth.md#how-to-authenticate)).
2. You have read access to at least one [Dataset](../concepts.md#datasets).

## Using the API
Obelisk exposes an HTTP endpoint which accepts HTTP POST requests for querying data and statistics. The HTTP body for these requests should be a JSON object representing an [Obelisk Query](../tech_reference/queries.md).

For a detailed reference of the Ingest endpoint, we refer to the [API documentation]({{extra.apidocs.url}}/#/reference/data-api/querying-data). In the following sections, we will show some practical examples of queries in the context of a realistic example use case.

### Use case overview
Let us revisit the use case described in the [Ingest Guide](data_ingestion.md#example). We have a Dataset containing measurements for temperature (`temperature.celsius::number`) and relative humidity (`humidity.rh::number`) on a per-room basis in a smart building context. Each building produces the data via a sensor gateway, for which the gateway id is modeled as a tag. The sensor gateway periodically polls the temperature and humidity sensors that are placed in each room. The device id of the sensor is modeled as the Metric Event `source`, while the room the measurement was produced is also modeled as a tag.

Say we want to develop a simple dashboard on top of this Dataset, displaying a basic overview of the environment status for a specific building. The features we have in mind are:

* Displaying the current temperature and humidity values for each room in the building (see [Example 1](#example-1-raw-event-query)).
* Plotting a graph, showing the course of the values for a selectable sensor, measured in the last 24 hours (see [Example 2](#example-2-raw-event-query)).
* Plotting a graph, showing the minimum, average and maximum value for a selectable Metric during office hours for the last month (see [Example 3](#example-3-aggregate-query)).

### Example 1 (Raw event query)
To receive the data for the first feature, we can send a POST request to {{extra.obelisk.url}}/api/v3/data/query/events with the following JSON request body:

```json
{
  "dataRange":{
    "datasets":[
      "<ID of the Dataset>"
    ],
    "metrics":[
      "temperature.celsius::number",
      "humidity.rh::number"
    ]
  },
  "filter":{
    "_withTag":"gateway=demo1"
  },
  "orderBy":{
    "fields":[
      "timestamp"
    ],
    "ordering":"desc"
  },
  "limitBy":{
    "fields":[
      "metric",
      "source"
    ],
    "limit":1
  }
}
```

The `dataRange` defines the domain that the query targets, in this case we want to receive the latest values for both the temperature and humidity sensors. The `filter` allows us to select only the data coming from a specific building, by filtering on data that has a specific gateway tag. Using the `orderBy` attribute, we instruct the query to process the data sorted by `timestamp` in reverse order (as we want to find the latest produced data). Finally, we need to restrict the result-set to a single result for each unique `metric` and `source` (the sensor) combination, by specifying the `limitBy` attribute.

By default the query will return a result-set containing the `timestamp`, `metric`, `source` and `value` attributes for each event. You can change this behavior by explicitly setting the `fields` attribute in the query object (see [Common Query properties](../tech_reference/queries.md#common-query-properties)). E.g. by including `tags` in the fields, we can use the room tag to display room information instead of relying on an additional mapping table (from sensor ID to room) in the application.

### Example 2 (Raw event query)
This second example is more straightforward: fetching the values for a specific series within a specific time-window is one of the core features of Obelisk.

To retrieve the data for this second example, we can send a POST request to {{extra.obelisk.url}}/api/v3/data/query/events with the following JSON request body:

```json
{
  "dataRange": {
    "datasets": [
      "<ID of the Dataset>"
    ],
    "metrics": [
      "humidity.rh::number"
    ]
  },
  "from": 1618991425686,
  "to": 1619077825686,
  "filter": {
    "_and": [
      {
        "_withTag":"gateway=demo1"
      },
      {
        "source": {
          "_eq": "<ID of the selected sensor>"
        }
      }
    ]
  }
}
```

In this sample, we are querying values for the last 24 hours for a selected humidity sensor, hence why this time only `humidity.rh::number` is included in the `dataRange`. To select a specific time-range, we can use the `from` and `to` attributes, with `to` being the current timestamp at the time and `from` being _current timestamp - (24 x 60 x 60 x 1000)_ (24 hours earlier). We retain the filter on the specific gateway tag, but add an additional filter on the selected source using `_and`.

!!! warning
    Always check if the query response contains a `cursor` attribute. If a cursor is present, additional results can be retrieved from Obelisk. To continue 'paging' through the result-set, let your client execute the query again, this time including the received value for `cursor`.

### Example 3 (Aggregate query)
In this final example, we show a more advanced scenario. Retrieving aggregates for a non-continuous time-window is not directly supported by the Obelisk Query API[^1]. Sometimes some post-processing is needed in the client application.

We cannot calculate aggregates directly for the non-office hours, but we can calculate hourly aggregates and then filter and process the results. To do this, we send a POST request to {{extra.obelisk.url}}/api/v3/data/query/stats with the following JSON request body:

```json
{
  "dataRange": {
    "datasets": [
      "<ID of the Dataset>"
    ],
    "metrics": [
      "temperature.celsius::number"
    ]
  },
  "from": 1614553200000,
  "to": 1617227999000,
  "fields": ["min", "max", "mean"],
  "filter":{
    "_withTag":"gateway=demo1"
  },
  "groupBy": {
    "time": {
      "interval": 1,
      "intervalUnit": "hours"
    }
  }
}
```

A familiar `dataRange` is used, based on the selected Metric. The `from` and `to` attributes define the time-range covering the month we wish to display the aggregate information for. When performing aggregate queries, a different set of fields are available (see [Querying Metric Stats](../tech_reference/queries.md#querying-metric-stats-aggregates)). Here we wish to calculate the minimal, maximum and average values. A filter is added to query only the data for the selected building (based on the gateway tag). Finally, the `groupBy` attribute is used to group the aggregate results in buckets of 1 hour.

The response includes an hour-by-hour aggregate value (min, max, mean) and a timestamp (at the start of the respective hour) for all temperature measurements, measured at the selected building during the past month. It is then up to the client to only retain the records for which the timestamp is during business hours and then merge the results on a day-by-day basis (daily minimum is the minimum of the hourly minima, etc...). 

[^1]: Unless you 'manually' exclude all the non-office hours by adding explicit filters on `timestamp` for each day of the month.

--8<-- "snippets/glossary.md"