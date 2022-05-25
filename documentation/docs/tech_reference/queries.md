# Query Language

You will be using the Obelisk Query Language (OQL) for retrieving raw Metric Events, aggregating data, requesting bulk
exports and setting up data streams. An OQL query is expressed as a JSON document and follows a structured,
object-oriented design.

## Common Query properties

All types of queries share some common properties:

| Attribute | Default Value | Description| 
| --- | --- | --- |
| dataRange <br>([DataRange](#datarange)) | n/a | Specifies the Datasets and Metrics that should be taken into account. |
| timestampPrecision <br>(enum) | `milliseconds` | _(Optional)_ Defines the timestamp precision for the returned results. Available options: `microseconds`, `milliseconds`, `seconds`.|
| from <br>(number) | `null` | _(Optional)_ Limit output to events after (and including) this UTC millisecond timestamp. Cannot be used when setting up streams.|
| to <br>(number) | `null` | _(Optional)_ Limit output to events before (and excluding) this UTC millisecond timestamp. Cannot be used when setting up streams. |
| fields <br>([string]) | `["timestamp", "metric", "source", "value"]` | _(Optional)_ For all queries (except for [aggregates](#querying-metric-stats-aggregates)), the 'fields' attribute holds the names of the [Metric Event](data_format.md#metric-event) fields that should be included in the resulting output. Note: the field `timestamp` will always be included in the result as the first attribute! |
| filter <br>([FilterExpression](filters.md)) | `{}` (select all) | _(Optional)_ Limit output to events matching the specified. |

### DataRange
Specifies the Datasets and Metrics that should be taken into account. The Dataset IDs must be specified, but Metric IDs are optional.

| Attribute | Default Value | Description |
| --- | --- | --- |
| datasets <br>([string]) | n/a | The IDs of the [Datasets](../concepts.md#datasets) the Query should take into account. |
| metrics <br>([string])| all metrics | The IDs of the [Metrics](../concepts.md#metrics) the Query should take into account. |

!!! info "Type Wildcard"
    The metrics list supports wildcards, you can e.g. request all metrics of type number to be returned using `*::number`.

#### Extracting JSON attributes

You can extract individual fields from Metrics of type `::json`, which allows you to combine them as a derived Metric in combination with other Metrics of the same type. For example:

```json
{
  "datasets" : ["1582043401098"],
  "metrics" : ["airquality.no2::number", "AirqualityObserved::json/NO->value::number"]
}
```

When querying raw events using this DataRange, the resultset will contain values for the primitive Metric `airquality.no2::number` along with values that were extracted from the Metric `AirqualityObserved::json` using the specified json path (`NO->value`). The json path syntax used here, is consistent with the syntax for [descending into objects](filters.md#descending-into-objects) when using [Filter Expressions](filters.md).

## Querying Metric Events

When querying raw Metric Event data, the following additional query properties can be applied:

| Attribute | Default Value | Description |
| --- | --- | --- |
| orderBy <br>([OrderBy](#orderby)) | timestamp (ascending) | _(Optional)_ Specifies the ordering of the output. |
| limit <br>(number) | `2500` | _(Optional)_ Limit output to a maximum number of events. The result will contain a cursor pointing to the next results in the storage (that were not returned). |
| cursor <br>(string) | `null` | _(Optional)_ Provide the cursor returned by a previous query to page through large resultsets. |
| limitBy <br>([LimitBy](#limitby)) | `null` | _(Optional)_ Limit the combination of a specific set of Index fields to a specified maximum number. |

### OrderBy
The orderBy clause is defined as follows:

| Attribute | Description |
| --- | --- |
| fields <br>([string]) | List of fields to order by, limited to 'indexed'-fields: <ul><li>timestamp</li><li>dataset</li><li>metric</li><li>producer</li><li>source</li><li>geohash</li></ul> |
| ordering <br>(enum) | Sort order: `asc` (for ascending), `desc` (for descending). |

### LimitBy
The limitBy clause is defined as follows:

| Attribute | Description |
| --- | --- |
| fields <br>([string]) | List of fields to limit by. Only 'indexed'-fields are supported: <ul><li>timestamp</li><li>dataset</li><li>metric</li><li>producer</li><li>source</li><li>geohash</li></ul> |
| limit <br>(number) | Limit results for the specified fields combination to the specified value. |

## Querying Metric Stats (aggregates)

When querying aggregate data, the following additional query properties can be applied:

| Attribute | Default Value | Description |
| --- | --- | --- |
| fields <br>([string]) | `["mean", "count"]` | Override of common fields attribute: List of [Metric Stats](data_format.md#metric-stats) specific fields to return in the result set. |
| orderBy <br>([OrderBy](#orderby)) | timestamp (ascending) | (Optional) Specifies the ordering of the output. |
| groupBy <br>([GroupBy](#groupby)) | `null` | Group the results by time, metadata field or location. |
| limit <br>(number) | `60` | _(Optional)_ Limit output to a maximum number of returned aggregates. The result will contain a cursor pointing to the next results. |
| cursor <br>(string) | `null` | _(Optional)_ Provide the cursor returned by a previous query to page through large resultsets. |

### GroupBy
The groupBy clause is defined as follows:

| Attribute | Description |
| --- | --- |
| fields <br>([string]) | List of fields to group by, limited to 'indexed'-fields: <ul><li>timestamp</li><li>dataset</li><li>metric</li><li>producer</li><li>source</li><li>geohash</li></ul> |
| time <br>([TimeInterval](#timeinterval)) | _(Optional)_ When grouping by time, allows specifying the time interval to group by. |
| geohashPrecision <br>(number) | _(Optional)_  When grouping by geohash, allows grouping by geohashes of a certain precision (between 4 and 8, default 4).

#### TimeInterval
A time interval declaration is specified as follows:

| Attribute | Default Value | Description |
| --- | --- | --- |
| interval <br>(number) | `1` | Length of the time interval. |
| intervalUnit <br>(enum) | `days` | Unit of the time interval: `seconds`, `minutes`, `hours` or `days` |
| offset <br>(number) | `0` | Length of the offset to apply (e.g. useful to correct timezone differences when requesting daily aggregates). |
| offsetUnit <br>(enum) | `seconds` | Unit of the interval offset: `seconds`, `minutes`, `hours` or `days` |

--8<-- "snippets/glossary.md"