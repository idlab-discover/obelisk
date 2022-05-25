# Filter Expressions

## Operator Fields

When operating on a specific data type, you can use all the fields of that data type as the first-hand argument for
Filter Expressions targeting specific fields.

For example: when filtering Metric Events, you can filter using `timestamp`, `dataset` (Dataset ID), `source`, etc... The [Data Format](data_format.md#data-format) page gives a full overview of the data types with a full description of their fields.

### Descending into objects
Sometimes the field references another object instead of a single numerical, boolean or textual value. Obelisk has
a 'descend into' notation for dealing with this type of scenarios, for example:

* Filter on the client ID of the Producer of a Metric Event: `producer->clientId`
* Filter on the latitude of the Location of a Metric Event: `location->lat`
* Filter on a custom attribute of the JSON value of a Metric Event: `value->someAttribute->exampleAttr1`

## Logical Operators

### _and

The expression holds true if all the child expressions evaluate to true.

??? note "Show Example"
    Only retain events that were received by the platform after the specified timestamp _and_ were produced by a Thing with id `demoThing14`.

    ```json
    {
      "_and" : [ {
        "tsReceived" : {
          "_gt" : 1618213433092
        }
      }, {
        "source" : {
          "_eq" : "demoThing14"
        }
      } ]
    }
    ```

### _or

The expression holds true if one of the child expressions evaluate to true.

??? note "Show Example"
    Only retain events that were produced by a Thing with id `demoThing14` _or_ are tagged with the tag `demo`.

    ```json
    {
      "_or" : [ {
        "source" : {
          "_eq" : "demoThing14"
        }
      }, {
        "_withTag" : "demo"
      } ]
    }
    ```

### _not

The expression holds true if the wrapped expression evaluates to false.

??? note "Show Example"
    Only retain events that were not tagged with the tag `demo`.

    ```json
    {
      "_not" : {
        "_withTag" : "demo"
      }
    }
    ```

## Comparison Operators

### _eq

The expression holds true if the target field is equal to the specified value.

??? note "Show Example"
    Only retain events that were produced by the client with the specified id.

    ```json
    {
      "producer->clientId" : {
        "_eq" : "6064815c38a40510cdd5fb41"
      }
    }
    ```

### _neq

The expression holds true if the target field is not equal to the specified value. Shorthand for the combination of [_not](#_not) and [_eq](#_eq).

### _gt

The expression holds true if the target field is greater than the specified value.

??? note "Show Example"
    Only retain events that were produced at an elevation of higher than 10 meters.

    ```json
    {
      "elevation" : {
        "_gt" : 10
      }
    }
    ```

### _gte

The expression holds true if the target field is greater than or equal to the specified value.

??? note "Show Example"
    Only retain events that were produced at an elevation of higher than or equal to 10 meters.

    ```json
    {
      "elevation" : {
        "_gte" : 10
      }
    }
    ```

### _lt

The expression holds true if the target field is less than the specified value.

??? note "Show Example"
    Only retain events that were produced with a value of less than 400.0 (assuming the metric type is `::number`).

    ```json
    {
      "value" : {
        "_lt" : 400.0
      }
    }
    ```

### _lte

The expression holds true if the target field is less than or equal to the specified value.

??? note "Show Example"
    Only retain events that were produced with a value less than or equal to 400.0 (assuming the metric type is `::number`).

    ```json
    {
      "value" : {
        "_lte" : 400.0
      }
    }
    ```

### _in

The expression holds true if the value of the target field is in the specified set of values.

??? note "Show Example"
    Only retain events that were produced by Things that match one of the ids in the specified list.

    ```json
    {
      "source" : {
        "_in" : [ "demoThing12", "demoThing14", "demoThing31" ]
      }
    }
    
    ```

## Evaluation Operators
Some additional operators that are used to filter by a specific field.

### _startsWith

The expression holds true if the target field starts with the specified character sequence (only works on String fields).

??? note "Show Example"
    Only retain events for which the metric name starts with `airquality`.

    ```json
    {
      "metric" : {
        "_startsWith" : "airquality"
      }
    }
    ```

??? tip "Easy geospatial queries"
    You can use the _startsWith operator in combination with the `geohash` field to easily find all events produced in an area. A geohash is a convenient way of expressing a location (anywhere in the world) using a short alphanumeric string. Each additional character introduces more precision, implying that nearby locations share a prefix[^1].

    E.g. Only retain events that were produced in and around Ghent, Belgium.

    ```json
    {
      "geohash" : {
        "_startsWith" : "u14d"
      }
    }
    
    ```

[^1]: Nearby locations generally have similar prefixes, though not always: there are edge-cases straddling large-cell boundaries.

### _regex

The expression holds true if the target field matches the specified regular expression.

??? note "Show example"
    Only retain events for which the metric name contains `temperature`.

    ```json
    {
      "source" : {
        "_regex" : "demoThing\\d+"
      }
    }
    ```

??? tip "Ignore case option"
    The regex operator supports case-insensitive matching:

    ```json
    {
      "source" : {
        "_regex" : "demothing\\d+",
        "_options" : "i"
      }
    }
    ```

### _exists

The expression holds true if the target field exists and its value is not empty.

??? note "Show Example"
    Only retain events that have a Location defined.

    ```json
    {
      "_exists": "location"
    }
    ```

### _withTag
The expression holds true if the Metric Event is tagged with the specified tag.

??? note "Show Example"
    Only retain events that have the tag `demo`.

    ```json
    {
      "_withTag" : "demo"
    }
    ```

### _withAnyTag
The expression holds true if the Metric Event is tagged with at least one of the specified tags.

??? note "Show Example"
    Only retain events that have at least one of the specified tags `demo`, `eval` or `test`.

    ```json
    {
      "_withAnyTag" : [ "demo", "eval", "test" ]
    }
    ```

### _locationInCircle
The expression holds true if the Metric Event is located within the Circle defined by the specified center Location and radius.

??? note "Show Example"
    Only retain events produced within a 70 meter radius of the Saint Bavo's Cathedral (Ghent, Belgium).

    ```json
    {
      "_locationInCircle" : {
        "center" : {
          "lat" : 51.0521581,
          "lng" : 3.7271245
        },
        "radius" : 70
      }
    }
    ```

### _locationInPolygon
The expression holds true if the Metric Event is located within the Polygon defined by the specified vertices.

??? note "Show Example"
    Only retain events produced in the area of the public square "Groentenmarkt" (Ghent, Belgium).

    ```json
    {
      "_locationInPolygon" : [ [ 51.056036, 3.722218 ], [ 51.055818, 3.7219578 ], [ 51.055607, 3.722259 ], [ 51.055834, 3.721827 ] ]
    }
    ```

???+ warning
    Each vertex is represented by a pair of coordinates (latitude, longitude). Vertices should be specified in a clockwise or counterclockwise order. The minimum number of vertices is 3

--8<-- "snippets/glossary.md"