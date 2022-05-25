# How to ingest data?

## Prerequisites
Make sure the following prerequisites are met:

1. You have a valid authentication context (see the guide on [authentication](auth.md#how-to-authenticate)).
2. A [Dataset](../concepts.md#datasets) exists that you can use to store the data.
3. Your authentication context has a `WRITE` [grant](../concepts.md#roles) on the respective Dataset.

## Data mapping
Before sending any data to Obelisk, think about how you can map your data into the Obelisk [Metric Event](../tech_reference/data_format.md#metric-event) format. The choices you make here can have an impact on how the data can be retrieved later on, how large your Dataset is going to become and even how fast certain queries will execute.

The following set of best practices / guidelines can help you in this process:

### Use the built-in metadata dimensions
Obelisk has several Metric Event fields to model metadata. Some of these will be provided by the platform upon ingestion or are mandatory: `dataset` (based on the request path), `metric`, `producer` and `tsReceived`. The other metadata properties are optional and 100% user-defined: `timestamp`, `source`, `tags`, `location` (a latitude, longitude pair) and `elevation`.

We recommend always setting these properties if the respective information is available at the producer side. These properties not only help with guaranteeing the uniqueness of data points (see next section), they also act as indexes, making it possible to find specific data quickly and efficiently. Furthermore, fine-grained access control at the Dataset level relies on Filter Expressions that are defined in terms of these metadata properties. Think about the different profiles that will need to access your data in advance and identify which properties can be used as distinguishing factors.

### Be mindful of the uniqueness of your data
Obelisk uses a composite key as the primary key for your data, composed of the following properties: `timestamp`, `dataset`, `metric`, `source` and `producer`.

The implication of this is that if you ingest a new data point with the exact same values for these properties as a point that was already stored, the old point will be replaced (e.g. content of `value`) without warnings![^2]

[^2]: Obelisk merges duplicate points (data records with the same composite key) in the background and retains the last inserted point. This implies that query results can in some cases include duplicate points for recently inserted data.

!!! help "Example use-case: High-frequency data"
    A common issue with uniqueness occurs when a producer tries to ingest at a rate higher than 1 kHz but uses millisecond precision. Some events will have an identical timestamp, resulting in data-loss because Obelisk will only retain the latest data point for events with the same timestamp (assuming the other properties of the primary key remain the same). 

??? tip "How to deal with this?"
    The obvious solution is to start using microsecond timestamp precision (which is supported by Obelisk). Unfortunately, this is not always possible, e.g. the sensor hardware only supports milliseconds. In this case, you can buffer the data coming from the source and spoof the microsecond precision part of the timestamp (=> convert the timestamp from milliseconds to microseconds and increment the microsecond value each time an event with the same millisecond timestamp is encountered in the windowed buffer). 

    Alternatively, you could use the `source` property to ensure that the events are unique, e.g. in cases when the single high-frequency stream can be broken down into a number of lower-frequency streams, grouped by Device ID.

### Always prefer using primitive Metric types
We've discussed the difference between primitive and complex Metric types [here](../concepts.md#metrics). Always prefer to use the former if you can, even if this would require small changes to your application code. Obelisk can perform a lot of optimizations in the background when dealing with primitive Metric types that are not possible with complex types. Using primitive types will allow you to produce more data efficiently, query or stream faster, while having a significantly lower impact on your usage limit quota.

!!! note "Example"
    We've witnessed developers using a JSON Metric (`::json`) to store values similar to the following JSON Object in Obelisk:

    ```json
    {
      "battery_level": 0.7,
      "deviceId": "19ee-4297",
      "location" : {
        "latitude": 51.0455426,
        "longitude": 3.7031979
      }
    }
    ```

    This type of usage can be optimized by:
    
    1. Using a Number Metric (`::number`) to store the battery level value (0.7).
    2. Revisiting your data mapping strategy: the other JSON entries are meta-data, so use the built-in Obelisk meta-data properties to model these properties instead.

### Be aware of your usage limits
Keep in mind how much data you can ingest, stream and query according to your usage limits when modeling how your application will use Obelisk.
See also the Concept documentation on [Usage Plans and Limits](../concepts.md#rate-limiting).

### Collaborating with other partners? Align your data mapping!
When collaborating with other partners, you can coordinate the data mapping. Using a consistent naming scheme for Metrics and Things (sources) can make it easier for other people to use your Dataset.

!!! tip
    Identify common Metrics early on, e.g. `temperature::number`, `temp.celsius` and `temperature_celsius` can all be variations of the same concept. Unifying these measurements into a single Metric series, can facilitate building applications on top of this data later down the road.

## Using the API
Obelisk exposes an HTTP endpoint which accepts HTTP POST requests for ingesting data. The HTTP body for these requests should be a JSON array of [Metric Events](../tech_reference/data_format.md#metric-event).

For a detailed reference of the Ingest endpoint, we refer to the [API documentation]({{extra.apidocs.url}}/#/reference/data-api/ingesting-data).

### Example
In this example use case, a sensor gateway periodically (every minute) collects temperature and humidity measurements for five different rooms in a building and sends these to Obelisk.

Taking into account the above tips:

1. We will optimally use the built-in metadata: 
    * The unique sensor ID is mapped to `source`
    * The room name and gateway ID are modeled as `tags`
    * Location and elevation are not relevant for this indoor use case, and are not used
2. The individual measurements can be uniquely stored into Obelisk: data is coming in at a low rate (so timestamp clashing is not an issue) and the sensor ID stored in `source` uniquely identifies the series.
3. The temperature and humidity measurements can be modeled as Metrics of type `::number` (for optimal storage and querying performance)
4. The use case produces *5 (number of rooms) x 2 (sensors per room) x 60 (every minute) = 600 events / hour*. This rate can be supported by the default Obelisk usage limits.

The authenticated HTTP POST to `{{extra.obelisk.url}}/api/v3/data/ingest/{datasetId}` the sensor gateway performs every minute will be similar to the following JSON array:

```json
[ {
  "timestamp" : 1619000040000,
  "metric" : "temperature.celsius::number",
  "value" : 19.677089583816606,
  "source" : "device_MTYzODg3NDIzNw==",
  "tags" : [ "roomId=RoomA", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "humidity.rh::number",
  "value" : 38,
  "source" : "device_MzIwNjg3Mzc2",
  "tags" : [ "roomId=RoomA", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "temperature.celsius::number",
  "value" : 21.928479535006627,
  "source" : "device_MTYzODg3NDIzOA==",
  "tags" : [ "roomId=RoomB", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "humidity.rh::number",
  "value" : 31,
  "source" : "device_MzIwNjg3Mzc3",
  "tags" : [ "roomId=RoomB", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "temperature.celsius::number",
  "value" : 20.192775766921137,
  "source" : "device_MTYzODg3NDIzOQ==",
  "tags" : [ "roomId=RoomC", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "humidity.rh::number",
  "value" : 44,
  "source" : "device_MzIwNjg3Mzc4",
  "tags" : [ "roomId=RoomC", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "temperature.celsius::number",
  "value" : 19.891534860656762,
  "source" : "device_MTYzODg3NDI0MA==",
  "tags" : [ "roomId=RoomD", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "humidity.rh::number",
  "value" : 37,
  "source" : "device_MzIwNjg3Mzc5",
  "tags" : [ "roomId=RoomD", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "temperature.celsius::number",
  "value" : 18.078412366437977,
  "source" : "device_MTYzODg3NDI0MQ==",
  "tags" : [ "roomId=RoomE", "gateway=demo1" ]
}, {
  "timestamp" : 1619000040000,
  "metric" : "humidity.rh::number",
  "value" : 31,
  "source" : "device_MzIwNjg3Mzgw",
  "tags" : [ "roomId=RoomE", "gateway=demo1" ]
} ]
```

### Constraints
Aside from the usage limits, there are a number of other constraints that you should be aware of when posting data:

1. The total size of the request body cannot exceed **16 MB**. Larger bodies will result in an HTTP error response with code 413.
2. User-defined metadata fields, such as the Metric name[^1], `source` and individual entries of `tags` have a limit of **128 characters**.
3. An event can have a maximum of **32 tags**.
4. When using Metric type `::number[]`, the size of the number array is limited to **128 entries**.
5. When using Metric types `::json` or `::string`, the string representation of the value is limited to **65535 characters**.
6. The `timestamp` must be a positive Integer. No further validation is performed, Obelisk will accept events in the past or future (as this can be useful for certain use cases). However, we recommend developers to validate if the produced timestamps conform to their own expected time-range before posting requests!

An invalid request body (JSON syntax errors, or not conforming to the data format), or failure to adhere to constraints 2-6 will result in an HTTP error response with code 400.

[^1]: The value of `metric`, without the type suffix (e.g. `::number`).

--8<-- "snippets/glossary.md"