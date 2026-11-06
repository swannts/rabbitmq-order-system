db.getSiblingDB('admin').createUser({
  user: 'metrics_exporter',
  pwd: 'metrics_dev',
  roles: [
    { role: 'clusterMonitor', db: 'admin' },
    { role: 'read', db: 'local' },
  ],
});
