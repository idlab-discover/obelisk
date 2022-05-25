CREATE TABLE IF NOT EXISTS metric_events_local ON CLUSTER 'oblx-data'
(
    timestamp          DateTime64(6) Codec (DoubleDelta, LZ4),
    tsReceived         DateTime64(3) Codec (DoubleDelta, LZ4),
    dataset            LowCardinality(String),
    metric_type        LowCardinality(String),
    metric_name        LowCardinality(String),
    value_string       String Codec (LZ4HC),
    value_number       Float64 Codec (Delta, LZ4),
    value_number_array Array(Float64),
    value_bool         UInt8 Codec (Gorilla, LZ4),
    user               LowCardinality(String),
    client             LowCardinality(String),
    source             String,
    tags               Array(String),
    lat                Float64 Codec (Delta, LZ4),
    lng                Float64 Codec (Delta, LZ4),
    elevation          Float64 Codec (Delta, LZ4)
) ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/metric_events_local', '{replica}')
      PARTITION BY (toYYYYMM(timestamp), metric_type)
      ORDER BY (dataset, metric_type, metric_name, source, timestamp, user, client);

CREATE TABLE IF NOT EXISTS metric_events
    ON CLUSTER 'oblx-data' AS metric_events_local ENGINE = Distributed('{cluster}', default, metric_events_local, rand());

CREATE TABLE IF NOT EXISTS metric_metadata_local ON CLUSTER 'oblx-data'
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
) ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/metric_metadata_local', '{replica}')
    ORDER BY (dataset, metric_type, metric_name, user, client, source);

CREATE MATERIALIZED VIEW IF NOT EXISTS metric_metadata_local_mv ON CLUSTER 'oblx-data' TO metric_metadata_local AS SELECT dataset, metric_type, metric_name, user, client, source, min(tsReceived) as started, max(tsReceived) as lastUpdate, count(*) as count FROM metric_events_local GROUP BY dataset, metric_type, metric_name, user, client, source;

CREATE TABLE IF NOT EXISTS metric_metadata ON CLUSTER 'oblx-data' AS metric_metadata_local ENGINE = Distributed('{cluster}', default, metric_metadata_local);

-- Can't use comments in script for now
-- CREATE TABLE IF NOT EXISTS metric_events_local ON CLUSTER 'oblx-data'
-- (
--     timestamp          DateTime64(6) Codec (DoubleDelta, LZ4), -- DateTime with microsecond precision, DoubleDelta codec is a good match for monotonic series
--     tsReceived         DateTime64(3) Codec (DoubleDelta, LZ4), -- DateTime with microsecond precision, DoubleDelta codec is a good match for monotonic series
--     dataset            LowCardinality(String),
--     metric_type        LowCardinality(String),
--     metric_name        LowCardinality(String),
--     value_string       String Codec (LZ4HC),
--     value_number       Float64 Codec (Delta, LZ4),
--     value_number_array Array(Float64),
--     value_bool         UInt8 Codec (Gorilla, LZ4),
--     user               LowCardinality(String),
--     client             LowCardinality(String),
--     source             String,                                 -- Empty string if not present (Nullable negatively affects performance)
--     tags               Array(String),                          -- Empty array if not present (Nullable negatively affects performance)
--     lat                Float64 Codec (Delta, LZ4),             -- Using NaN when there is no location (Nullable negatively affects performance)
--     lng                Float64 Codec (Delta, LZ4),             -- Using Nan when there is no location (Nullable negatively affects performance)
--     elevation          Float64 Codec (Delta, LZ4)              -- Using Nan when there is no elevation (Nullable negatively affects performance)
-- ) ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/metric_events_local', '{replica}')
--       PARTITION BY (toYYYYMM(timestamp), metric_type)
--       ORDER BY (dataset, metric_type, metric_name, source, timestamp, user, client);

-- CREATE TABLE IF NOT EXISTS metric_events ON CLUSTER 'oblx-data' AS metric_events_local ENGINE = Distributed('{cluster}', default, metric_events_local, rand());
