FROM mongo:8.0.32

# Local replica-set member authentication key. Production deployments must
# provide a separately managed secret instead of using this development key.
RUN printf '%s' 'b3JkZXItcGxhdGZvcm0tbG9jYWwtcmVwbGljYS1zZXQta2V5LWRldi1vbmx5LTAx' > /etc/mongo-keyfile \
    && chown mongodb:mongodb /etc/mongo-keyfile \
    && chmod 400 /etc/mongo-keyfile
