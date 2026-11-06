CREATE ROLE metrics_exporter LOGIN PASSWORD 'metrics_dev';
GRANT pg_monitor TO metrics_exporter;
