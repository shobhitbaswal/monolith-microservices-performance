# Local Benchmark Results: Monolith vs Microservices

## 1. Purpose

This report records a preliminary local performance comparison between the
e-commerce monolith and its microservices implementation. It reports two
50-VU trials per architecture. These measurements are specific to the
implementations, environment, and workload tested; they are not a universal
ranking of architectural styles.

## 2. Test Configuration

| Parameter | Value |
|---|---|
| Execution environment | Local machine |
| Load-testing tool | k6 |
| Virtual users | 50 |
| Test duration | 60 seconds per trial |
| Monolith load script | `monolith-load.js` |
| Microservices load script | `microservices-load.js` |
| Monolith database | PostgreSQL, `scratch_mono_check` |
| Microservices database | PostgreSQL, `scratch_micro_check` |
| Monolith result exports | `monolith-run1.json`, `monolith-run2.json` |
| Microservices result exports | `microservices-run1.json`, `microservices-run2.json` |

Each script exercises five HTTP operations per iteration: product-list retrieval,
product-detail retrieval, order creation, adding an item to the cart, and cart
retrieval. Both scripts use 50 VUs and a 60-second duration, with a 0.5-second
sleep in each iteration.

## 3. Results

### Individual trials

| Metric | Monolith run 1 | Monolith run 2 | Microservices run 1 | Microservices run 2 |
|---|---:|---:|---:|---:|
| HTTP requests | 29,250 | 29,250 | 28,750 | 28,840 |
| Throughput (requests/s) | 485.13 | 484.32 | 475.60 | 476.48 |
| Average HTTP duration (ms) | 2.65 | 2.81 | 4.64 | 3.96 |
| Median HTTP duration (ms) | 1.86 | 1.97 | 2.13 | 2.04 |
| p90 HTTP duration (ms) | 4.74 | 5.29 | 9.99 | 9.60 |
| p95 HTTP duration (ms) | 6.89 | 7.50 | 13.11 | 12.95 |
| Failed checks | 0 | 0 | 0 | 0 |
| Failed HTTP request rate | 0% | 0% | 0% | 0% |

### Mean of the two trials per architecture

| Metric | Monolith mean | Microservices mean |
|---|---:|---:|
| Throughput (requests/s) | 484.73 | 476.04 |
| Average HTTP duration (ms) | 2.73 | 4.30 |
| Median HTTP duration (ms) | 1.92 | 2.09 |
| p90 HTTP duration (ms) | 5.02 | 9.80 |
| p95 HTTP duration (ms) | 7.20 | 13.03 |

The means above are simple arithmetic means of the two trial-level summary
values, not pooled per-request statistics.

## 4. Interpretation

Across these two trials, the monolith recorded approximately 1.8% higher
HTTP request throughput than the microservices implementation. The mean
average HTTP duration was approximately 57.5% higher for microservices, and
the mean p95 HTTP duration was approximately 81.1% higher.

Both implementations recorded zero failed checks and zero failed HTTP
requests in these trials.

One plausible contributor to the latency difference is synchronous
service-to-service HTTP communication in the microservices order flow,
including calls to the catalog and cart services. These measurements do not
isolate the cost of that communication, so it should be treated as a possible
explanation rather than a proven cause.

The results describe this particular workload and implementation. They do not
show that monoliths are inherently faster or that microservices are inherently
slower.

## 5. Limitations

1. The benchmark ran locally; results may differ on cloud infrastructure.
2. Only two full trials per architecture are available. More repeated trials
   are needed to characterize variability and improve confidence.
3. The database state was not reset to an identical baseline before every
   trial. Order and cart mutations can change the starting state over time.
4. The two architectures were run sequentially, not under simultaneous,
   randomized, or otherwise fully controlled environmental conditions.
5. Equal VU counts and test durations do not guarantee identical execution
   conditions or identical numbers of completed business iterations.
6. The microservices implementation uses synchronous inter-service HTTP
   calls, while the monolith performs related operations within one application.
   This is an architectural difference in the implementations being measured.
7. The microservices share a PostgreSQL database rather than using isolated
   databases per service. The results therefore do not represent every
   possible microservices deployment design.
8. The microservices cart model permits a nullable price, and the cart smoke
   test stored an item with a null price. Order creation obtained product
   pricing through the catalog service. This is a functional-equivalence
   limitation that should be documented when interpreting the workload.
9. The k6 HTTP request rate counts requests made by the load script. Internal
   service-to-service requests are not necessarily included in that count.
10. CPU and memory utilization were not captured as part of these four
    trials, so this report does not draw resource-efficiency conclusions.
11. These results are for the 50-VU workload only. They do not validate
    previous claims about a 100-VU comparison.

## 6. Next Steps

- Repeat trials with a documented and reproducible database reset procedure.
- Record machine specifications, JVM/runtime versions, database settings,
  and resource limits.
- Capture CPU and memory utilization alongside latency, throughput, and errors.
- Verify and document functional equivalence between the tested workflows.
- Keep local results separate from any future cloud benchmark.
- Update conclusions only when additional measurements support them.

## 7. Raw Result Files

The k6 summary exports are stored at the repository root:

- `monolith-run1.json`
- `monolith-run2.json`
- `microservices-run1.json`
- `microservices-run2.json`

Keep these files intact for reproducibility. If raw exports are ignored by
Git, archive them separately or explicitly decide which artifacts should be
versioned before publishing.
