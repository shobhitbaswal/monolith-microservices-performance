# Runtime configuration (microservices)

## Tracked defaults

Default application settings are defined in each service's `src/main/resources/application.properties`. Database credentials are supplied through environment variables and must not be committed.

| Variable | Service | Default / configured value | Purpose |
|---|---|---|---|
| `SERVER_PORT` | catalog / order / cart | 8081 / 8082 / 8083 | HTTP port |
| `CATALOG_BASE_URL` | order | `http://localhost:8081` | Catalog endpoint used during checkout |
| `CART_BASE_URL` | order | `http://localhost:8083` | Cart endpoint used to clear the cart |
| `HTTP_CONNECT_TIMEOUT` | order | `2s` | Inter-service connection timeout |
| `HTTP_READ_TIMEOUT` | order | `5s` | Inter-service response timeout |
| `SPRING_DATASOURCE_URL` | all | Supplied externally | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | all | Supplied externally | Database username |
| `SPRING_DATASOURCE_PASSWORD` | all | Supplied externally | Database password; never commit |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | all | `update` in the current IntelliJ test setup | Hibernate schema-management setting |

## Current equivalence-test setup

The following datasource configuration was used for the local functional-equivalence tests:

| Application | Database |
|---|---|
| Monolith | `scratch_mono_check` |
| Catalog service | `scratch_micro_check` |
| Cart service | `scratch_micro_check` |
| Order service | `scratch_micro_check` |

All three microservices share the same `scratch_micro_check` PostgreSQL database in this test setup. This is a local testing arrangement, not a separate-database-per-service deployment.

The microservices were configured with:

- `SPRING_DATASOURCE_USERNAME=postgres`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
- Catalog service URL: `http://localhost:8081`
- Order service URL: `http://localhost:8082`
- Cart service URL: `http://localhost:8083`

The order service calls the catalog service to obtain product prices and calls the cart service to clear the cart during checkout. Its configured connection and response timeouts are 2 seconds and 5 seconds, respectively.

## Benchmark controls and limitations

The 2-second connection timeout and 5-second response timeout are configured defaults, not empirically optimized values. Record them in the benchmark configuration and keep them consistent across repeated runs.

Before collecting final measurements:

1. Record the actual datasource URL, Hibernate DDL setting, connection-pool settings, JVM flags, and resource limits for each application.
2. Confirm equivalent starting data for the monolith and microservices.
3. Reset or recreate scratch database state between benchmark runs where necessary.
4. Use equivalent request payloads, virtual users, and test durations for both architectures.
5. Repeat runs and capture CPU and memory utilization alongside latency, throughput, and error rates.

Because the current microservices share one database, the experiment does not measure the overhead of independently deployed databases for each service. State this limitation when interpreting the results.

Never commit database passwords or other secrets.
