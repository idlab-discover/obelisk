# Data format

## Metric Event
The main format for both writing to and retrieving data from Obelisk is Metric Event. It is used in the Ingest API, Query APIs and Streaming API (as JSON objects) and in the Export API (as CSV records).

Metric Event instances can be defined as follows:

| Attribute | Default Value[^1] | Description |
| --- | --- | --- |
| timestamp | Obelisk system time | UTC timestamp of the Event. The precision (microseconds, milliseconds, seconds) depends on the context of the request. |
| dataset | n/a | ID of the [Dataset](../concepts.md#datasets) the Event belongs to. |
| metric | n/a | ID of the [Metric](../concepts.md#metrics) the Event belongs to. |
| value | n/a | The raw value that was recorded for the Event. |
| producer | User (or Client) producing the event[^2] | The Producer of the Event. |
| source | `null` | The ID of the [Thing](../concepts.md#things) that is the source of the Event (Producer-specified). |
| tags | `[]` | A set of Producer-specified tags for the Event. |
| location | `null` | The location where the Event was recorded. |
| geohash | n/a | The location as a [Geohash](https://en.wikipedia.org/wiki/Geohash) (this is a derived attribute and cannot be set when ingesting). |
| elevation | `null` | The height in meters (relative to ground-level) of the location where the Event was recorded. |
| tsReceived | n/a | The time at which Obelisk received the Event (UTC milliseconds), cannot be set when ingesting. |

[^1]: This is the default value set by Obelisk when writing data using the Ingest API and there is no specific value set for the attribute.
[^2]: This attribute is fixed and cannot be set by the User.

## Metric Stats
When querying aggregate data, the Query API will return instances of Metric Stats, defined as follows:

| Attribute | Description |
| --- | --- |
| timestamp | UTC timestamp of the starting time of the 'group by time' window of the aggregate result (with a precision determined by the Query, default: milliseconds). When not grouping by time, the timestamp will be `0`.|
| dataset | ID of the [Dataset](../concepts.md#datasets) the aggregate result belongs to (used when grouping by dataset). |
| metric | ID of the [Metric](../concepts.md#metrics) the aggregate result belongs to (used when grouping by metric). |
| producer | Producer the aggregate result belongs to (used when grouping by Producer). |
| source | The ID of the source ([Thing](../concepts.md#things)) the aggregate result belongs to (used when grouping by source). |
| geohash | The [Geohash](https://en.wikipedia.org/wiki/Geohash) the aggregate result was calculated for (used when grouping by geohash). |
| min | The minimum value for all Events of a numerical Metric type that match the query parameters. |
| max | The maximum value for all Events of a numerical Metric type that match the query parameters. |
| mean | The mean value for all Events of a numerical Metric type that match the query parameters. |
| stddev | The standard deviation for all Events of a numerical Metric type that match the query parameters. |
| count | The number of Events of a numerical Metric type that match the query parameters. |

--8<-- "snippets/glossary.md"