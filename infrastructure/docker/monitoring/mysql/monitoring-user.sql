CREATE USER IF NOT EXISTS 'metrics_exporter'@'%' IDENTIFIED BY 'metrics_dev';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'metrics_exporter'@'%';
