# Installation Instructions

# Kubernetes

Kubernetes version > 1.20.x

Obelisk has been evaluated on Kubernetes 1.20.x and above on several bare metal installations. Cloud provider specific installations have not been evaluated but should not pose a problem.

# Persistent Volumes

Either Dynamic Provisioning or configured volumes are necessary for multiple dependencies (e.g. Clickhouse, Pulsar, etc.) , make sure you have storage ready to go before proceeding with the installation of the dependencies.

# Dependencies

⚠️ In the following we provide brief installation instructions for our dependencies, a getting started point. If you plan on using Obelisk extensively, we **highly recommend** you familiarize yourself with all these dependencies and refer to their Git repositories for more extensive instructions and guidelines.

Most of these dependencies have several methods of deployment, here we only highlight how we deploy these ourselves at the time of writing.

We recommend you to install each dependency into a separate namespace, we are going to install the following dependencies:

- Traefik
- Pulsar
- Clickhouse
- MongoDB
- Redis
- Gubernator

## Traefik - Ingress Controller

Obelisk comes packaged with several Traefik middleware resources for Ingress, so it is advised to install Traefik as Ingress Controller.

[traefik 10.19.4 · traefik/traefik](https://artifacthub.io/packages/helm/traefik/traefik)

[https://github.com/traefik/traefik](https://github.com/traefik/traefik)

It is possible to use nginx ingress controller, but this would require you to add the right annotations in the ingress resources through the Helm config.

## Pulsar - Streaming Platform

There’s a choice of Pulsar Helm charts or more extensive Pulsar-based platforms like Streamnative platform or Datastax Lunar Streaming. At this time we recommend using `apache/pulsar`

[pulsar 2.9.3 · streamnative/pulsar](https://artifacthub.io/packages/helm/streamnative/pulsar)

[streamnative/pulsar GitHub](https://github.com/streamnative/charts/tree/master/charts/pulsar)

For installation instructions on Pulsar itself, we refer to the chart’s readme. However here is an overview of the Streamnative Pulsar components we enable in our Obelisk deployments:

```yaml
components:
 # NECESSARY
  # zookeeper, necessary for Pulsar to function
  zookeeper: true
  # bookkeeper, necessary for Pulsar to function
  bookkeeper: true
  # broker, necessary for Pulsar to function
  broker: true

  # OPTIONAL used in Obelisk production
  # pulsar detector, monitor distribution latency from message sending to message consumption
  pulsar_detector: true
  # bookkeeper - autorecovery, recommended for production environments!
  autorecovery: true
  # toolset, recommended for management of Pulsar.
  #### Installation instructions rely on this toolset
  toolset: true
  # pulsar manager, a dashboard
  pulsar_manager: true

 # OPTIONAL not used in Obelisk production
  # functions
  functions: false
  # proxy
  proxy: false
  # pulsar sql
  sql_worker: false
  # kop
  kop: false
  # mop
  mop: false
  # superset
  superset: false
```

Pulsar charts also have a monitoring stack which can be deployed alongside it, you can do this to get started, but we recommend using [prometheus-community/kube-prometheus-stack](https://artifacthub.io/packages/helm/prometheus-community/kube-prometheus-stack) for a more complete Kubernetes monitoring stack (you can always use the Grafana dashboards from the Pulsar chart for your own monitoring stack).

### Pulsar config

Setup namespaces through either the `pulsar-admin` CLI tool or the pulsar-manager dashboard.

💡 Enable the `pulsar-toolset` in your helm deployment and create an alias to work with it as follows:

```bash
$ alias pulsar-admin='kubectl exec -n $PULSAR_NAMESPACE pulsar-toolset-0 -- bin/pulsar-admin'
$ pulsar-admin --version
Current version of pulsar admin client is: 2.9.3
```

### Namespaces

Obelisk uses three pulsar namespaces for its topics:

- public/oblx-core: global metric events topics, the main message buffer
- public/oblx-ds: dataset specific topics which enable streaming
- public/default: various topics

The oblx namespaces will need to be created and configured.

### Namespace public/oblx-core

Topics in this namespace

- **metric_events**
- **metric_events_store_only**
- invalid_metric_events

The final topic is used to *park* invalid metrics that can’t be parsed by the Oblx Ingest Service. For this we can rely on topic auto-creation . The other two however will have to be partitioned topics.

More information on partitioned topics can be found [here](https://pulsar.apache.org/docs/v2.0.1-incubating/cookbooks/PartitionedTopics/). The main takeaway of partitioned topics is:

> By default, Pulsar topics are served by a single broker. Using only a single broker, however, limits a topic’s maximum throughput. *Partitioned topics* are a special type of topic that can span multiple brokers and thus allow for much higher throughput.
>

In the following, we assume you are using `pulsar-admin`  to service your pulsar cluster (either through the alias or by exec’ing into a Pulsar pod). These instructions will create the namespace and partitioned topics. Here we are using the public tenant (a default present one) since our pulsar cluster is not exposed and we don’t have need for multi tenancy on a pulsar level.

```bash
pulsar-admin namespaces create public/oblx-core
pulsar-admin topics create-partitioned-topic \
 persistent://public/oblx-core/metric_events --partitions 9
pulsar-admin topics create-partitioned-topic \
 persistent://public/oblx-core/metric_events_store_only --partitions 9
```

The number of partitions per topic here is set at nine but this can be tweaked according to your use case. Two thing to consider with regard to partitions:

- Make sure number of partitions ≥ number of brokers so load of topic can be spread across them
- Partition count can be increased but **not decreased** (since this is equivalent to deleting a topic). So best start small and grow when needed

### Namespace public/oblx-ds

This namespace houses auto-created Dataset specific topics which are used to setup streaming through SSE.

It is important to setup [message retention and expiry](https://pulsar.apache.org/docs/en/cookbooks-retention-expiry/) for topics on this namespace. The oblx streaming components will auto create topics and subscriptions needed to facilitate streaming. By default, Pulsar will store unacknowledged messages indefinitely and delete messages when acknowledge by all active subscriptions. To ensure we don’t store all data for inactive streams and that an active stream can *rewind* it’s data flow we need to set the following:

⚠️ Be sure to check `--help` for these commands and ensure you are using the correct time notation. At the time of writing these commands use different granularities (seconds, minutes and d/h/m/s notation)

```bash
pulsar-admin namespaces create public/oblx-ds
pulsar-admin namespaces set-backlog-quota public/oblx-ds --limit 2G --limitTime 43200 \
 --policy consumer_backlog_eviction
# Message TTL = 1 day = 60*60*24 seconds (--messageTTL set in second)
pulsar-admin namespaces set-message-ttl public/oblx-ds --messageTTL 86400
# Acked message retention (for replay) = 1 day
pulsar-admin namespaces set-retention public/oblx-ds --time 1d --size 10G
# Backlog retention = 12 hours = 43200 seconds | 2G
pulsar-admin namespaces set-backlog-quota public/oblx-ds --limit 2G --limitTime 43200 \
 --policy consumer_backlog_eviction
# Expire subscriptions >1 week = 7*60*24 minutes = 10080 minutes
pulsar-admin namespaces set-subscription-expiration-time public/oblx-core --time 10080
```

These values are recommendations to get started and can be tweaked to cater to different user experiences.

## Clickhouse

Clickhouse is the database of choice for all Obelisk metrics. To install Clickhouse we aren’t going to use a Helm chart but an operator, the Altinity Clickhouse Operator!

[https://github.com/Altinity/clickhouse-operator](https://github.com/Altinity/clickhouse-operator)

### Zookeeper

Clickhouse relies on Zookeeper for coordination of data replication and distributed DDL queries execution.

To install Zookeeper you can either follow [these instructions](https://github.com/Altinity/clickhouse-operator/blob/master/docs/zookeeper_setup.md) from the `altinity/clickhouse-operator` repository or you use a Helm chart.

[clickhouse-operator/zookeeper_setup.md at master · Altinity/clickhouse-operator](https://github.com/Altinity/clickhouse-operator/blob/master/docs/zookeeper_setup.md)

In Obelisk production we use the `bitnami/zookeeper` chart.

[zookeeper 9.0.2 · bitnami/bitnami](https://artifacthub.io/packages/helm/bitnami/zookeeper)

Whatever route you choose, make sure you enable `autopurge` (see [Clickhouse recommendations on Zookeeper](https://clickhouse.com/docs/en/operations/tips/#zookeeper)). Add this to your `values.yaml` when using the Bitnami chart.

```yaml
autopurge:
  snapRetainCount: 10
  purgeInterval: 1
```

### Install Altinity clickhouse-operator

```bash
kubectl apply -f https://raw.githubusercontent.com/Altinity/clickhouse-operator/master/deploy/operator/clickhouse-operator-install-bundle.yaml
# Verify correct install
kubectl get pods --namespace kube-system
```

Should see an entry like this:

```bash
clickhouse-operator-857c69ffc6-zdlh7         2/2     Running   0          44s
```

This pod will watch for `clickhouseinstallation` resources and handle installs, scaling and upgrades according to changes made to these resources. Setting up Clickhouse is thus as simple as creating one yaml file.

### Install Clickhouse using ClickhouseInstallation file

How many shards and replicas you define for you clickhouse installation is up to you, but we recommend at least 2 shards and 2 replicas for production environments. This way you get the performance and fault tolerance benefits of sharding, as well as data loss protection.

You can always grow your CH cluster and add more shards when needed.

CH Requirements:

- A cluster named `oblx-data`
- ≥2 shards
- ≥2 replicas

💡 A sample `ClickhouseInstallation.yaml` file can be found in the deployment folder! Be sure to change the following according to your setup:

- `spec.configuration.zookeeper` : set the correct URL(s)
- `spec.templates.volumeClaimTemplates` : use your desired storage class and/or configured volumes

Then just apply the chi file to install

```yaml
kubectl apply -f deployment/clickhouse/clickhouseinstallation.yaml
```

### Create tables

Once all Clickhouse nodes are up and running, we can create the necessary tables.

The necessary SQL statements can be found at `obelisk/plugin-datastore-clickhouse/initdb-cluster/init.sql`

You can copy this file to one of the clickhouse pods and execute the queries like so:

```bash
# chi-clickhouse-oblx-data-0-0-0 is the name of a clickhouse pod, change appropriately
kubectl cp plugin-datastore-clickhouse/initdb-cluster/init.sql \
 chi-clickhouse-oblx-data-0-0-0:/tmp/init.sql
kubectl exec chi-clickhouse-oblx-data-0-0-0 -- clickhouse-client --queries-file /tmp/init.sql
```

You should get an output similar to this (here we are using CH v22.3)

```bash
chi-clickhouse-oblx-data-1-0    9000    0               3       0
chi-clickhouse-oblx-data-1-1    9000    0               2       0
chi-clickhouse-oblx-data-0-1    9000    0               1       0
chi-clickhouse-oblx-data-0-0    9000    0               0       0
chi-clickhouse-oblx-data-1-0    9000    0               3       0
chi-clickhouse-oblx-data-1-1    9000    0               2       0
chi-clickhouse-oblx-data-0-1    9000    0               1       0
chi-clickhouse-oblx-data-0-0    9000    0               0       0
chi-clickhouse-oblx-data-1-0    9000    0               3       0
chi-clickhouse-oblx-data-1-1    9000    0               2       0
chi-clickhouse-oblx-data-0-1    9000    0               1       0
chi-clickhouse-oblx-data-0-0    9000    0               0       0
chi-clickhouse-oblx-data-1-0    9000    0               3       0
chi-clickhouse-oblx-data-1-1    9000    0               2       0
chi-clickhouse-oblx-data-0-1    9000    0               1       0
chi-clickhouse-oblx-data-0-0    9000    0               0       0
chi-clickhouse-oblx-data-1-0    9000    0               3       0
chi-clickhouse-oblx-data-1-1    9000    0               2       0
chi-clickhouse-oblx-data-0-1    9000    0               1       0
chi-clickhouse-oblx-data-0-0    9000    0               0       0
```

## MongoDB

MongoDB installation can remain very default, just make sure you use persistence and disable auth as we aren’t exposing the database.

[mongodb 11.1.3 · bitnami/bitnami](https://artifacthub.io/packages/helm/bitnami/mongodb)

## Redis

For Redis any setup will do and persistence isn’t really needed (used for ephemeral state storage). A standalone with replicas for failover is adequate.

[redis 16.9.10 · bitnami/bitnami](https://artifacthub.io/packages/helm/bitnami/redis)

```
helm install redis -n redis bitnami/redis --set architecture=standalone \
 --set auth.enabled=false
```

## Gubernator

Gubernator is used for rate limiting of API requests.

[https://github.com/mailgun/gubernator](https://github.com/mailgun/gubernator)

Gubernator can be installed with their kubernetes deployment file

```
# Download the kubernetes deployment spec
$ curl -O https://raw.githubusercontent.com/mailgun/gubernator/master/k8s-deployment.yaml

# Edit the deployment file to change the environment config variables
$ vi k8s-deployment.yaml

# Create the deployment (includes headless service spec)
$ kubectl create -f k8s-deployment.yaml
```

In our case, the default config is adequate, though we prefer to lock the version instead of depend on `latest` tag, as restarts and rescheduling can lead to unwanted upgrades. Last confirmed working version is `v2.0.0-rc.24` so change the following

```
   containers:
        - image: ghcr.io/mailgun/gubernator:v2.0.0-rc.24
```
