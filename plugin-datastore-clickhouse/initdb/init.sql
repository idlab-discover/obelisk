CREATE TABLE IF NOT EXISTS metric_events_local
(
    timestamp          DateTime64(6) Codec (DoubleDelta, LZ4), -- DateTime with microsecond precision, DoubleDelta codec is a good match for monotonic series
    tsReceived         DateTime64(3) Codec (DoubleDelta, LZ4), -- DateTime with microsecond precision, DoubleDelta codec is a good match for monotonic series
    dataset            LowCardinality(String),
    metric_type        LowCardinality(String),
    metric_name        LowCardinality(String),
    value_string       String Codec (LZ4HC),
    value_number       Float64 Codec (Delta, LZ4),
    value_number_array Array(Float64),
    value_bool         UInt8 Codec (Gorilla, LZ4),
    user               LowCardinality(String),
    client             LowCardinality(String),
    source             String,                                 -- Empty string if not present (Nullable negatively affects performance)
    tags               Array(String),                          -- Empty array if not present (Nullable negatively affects performance)
    lat                Float64 Codec (Delta, LZ4),             -- Using NaN when there is no location (Nullable negatively affects performance)
    lng                Float64 Codec (Delta, LZ4),             -- Using Nan when there is no location (Nullable negatively affects performance)
    elevation          Float64 Codec (Delta, LZ4)              -- Using Nan when there is no elevation (Nullable negatively affects performance)
) ENGINE = ReplacingMergeTree()
      PARTITION BY (toYYYYMM(timestamp), metric_type)
      ORDER BY (dataset, metric_type, metric_name, source, timestamp, user, client);

CREATE TABLE IF NOT EXISTS metric_events as metric_events_local ENGINE = Distributed('oblx-data', default, metric_events_local, rand());

CREATE TABLE IF NOT EXISTS metric_metadata_local
(
    dataset     LowCardinality(String),
    metric_type LowCardinality(String),
    metric_name LowCardinality(String),
    user        LowCardinality(String),
    client      LowCardinality(String),
    source      String,
    started     SimpleAggregateFunction(min, DateTime64(3)),
    lastUpdate  SimpleAggregateFunction(max, DateTime64(3)),
    count       SimpleAggregateFunction(sum, UInt64)
) ENGINE = AggregatingMergeTree() ORDER BY (dataset, metric_type, metric_name, user, client, source);

CREATE MATERIALIZED VIEW IF NOT EXISTS metric_metadata_local_mv TO metric_metadata_local AS SELECT dataset, metric_type, metric_name, user, client, source, min(tsReceived) as started, max(tsReceived) as lastUpdate, count(*) as count FROM metric_events_local GROUP BY dataset, metric_type, metric_name, user, client, source;

CREATE TABLE IF NOT EXISTS metric_metadata AS metric_metadata_local ENGINE = Distributed('oblx-data', default, metric_metadata_local);
