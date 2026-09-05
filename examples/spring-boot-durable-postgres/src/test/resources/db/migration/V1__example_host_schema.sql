-- Proves that a host application's host Flyway history can coexist with
-- CompileFlow Durable's independently owned schema history.
CREATE TABLE cf_example_host_schema_marker
(
    marker_id INTEGER NOT NULL PRIMARY KEY
);
