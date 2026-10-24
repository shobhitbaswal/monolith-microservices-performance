# Preliminary Benchmark Results: Monolith vs Microservices

## 1. Purpose

This document records the first local performance comparison between the
e-commerce monolith and its microservices implementation. These results are
preliminary and should not be treated as a definitive controlled experiment.

## 2. Test Configuration

| Parameter | Value |
|---|---|
| Execution environment | Local machine |
| Load-testing tool | k6 |
| Virtual users | 50 |
| Test duration | 60 seconds |
| Monolith result file | `results/monolith-run1.json` |
| Microservices result file | `results/microservices-run1.json` |
| Monolith load script | `monolith-load.js` |
| Microservices load script | `microservices-load.js` |

## 3. Results

| Metric | Monolith | Microservices |
|---|---:|---:|
| HTTP requests | 29,250 | 28,750 |
| Throughput (requests/s) | 483.82 | 477.06 |
| Average HTTP duration (ms) | 2.90 | 4.32 |
| Median HTTP duration (ms) | 2.11 | 2.17 |
| p90 HTTP duration (ms) | 5.73 | 9.44 |
| p95 HTTP duration (ms) | 7.69 | 12.23 |
| Failed checks | 0 | 0 |
| Failed HTTP request rate | 0% | 0% |

## 4. Preliminary Interpretation

- The microservices run recorded approximately 1.4% lower throughput.
- Average HTTP request duration was approximately 48.9% higher in the
  microservices run.
- The p95 HTTP request duration was approximately 59.0% higher in the
  microservices run.
- Both runs recorded zero failed checks and a 0% failed HTTP request rate.

These observations describe the recorded runs; they do not establish that
microservices are inherently slower than a monolith.

## 5. Limitations

1. The two full benchmark runs were conducted on different days, so machine
   state and other environmental conditions may have differed.
2. Equal virtual-user counts and test durations do not by themselves guarantee
   identical workloads or equivalent resource conditions.
3. The microservices implementation makes synchronous inter-service HTTP
   calls. These calls may contribute to end-to-end latency.
4. The reported HTTP request rate counts requests issued by the k6 script;
   internal service-to-service requests are not necessarily included.
5. One run per architecture is insufficient to characterize run-to-run
   variability. Repeated runs under a controlled protocol are needed.
6. This is a local benchmark. It must be analyzed separately from any future
   cloud benchmark.
7. Run 1 used an earlier microservices load-script payload that differed from the monolith's request payloads.
      Treat these numbers as preliminary historical results, not a final equivalent-workload comparison. Re-run
      both architectures with the reviewed scripts and controlled starting state before drawing conclusions.

## 6. Next Steps

- Verify that both implementations execute equivalent business operations.
- Record the machine specifications, runtime versions, database configuration,
  and resource limits used during testing.
- Run repeated trials under the same protocol and compare variability.
- Capture CPU and memory utilization alongside latency, throughput, and errors.
- Preserve the raw k6 JSON exports for reproducibility.
- Conduct and document the cloud experiment separately.

## 7. Raw Result Files

The raw exports are currently stored locally under `results/` and are ignored
by Git. Keep them intact. Decide how to archive or version the raw results
before publishing the research repository.
