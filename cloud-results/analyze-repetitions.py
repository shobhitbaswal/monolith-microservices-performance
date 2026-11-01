import csv
import re
import statistics
from collections import defaultdict
from pathlib import Path

ROOT = Path("cloud-results")
ARCHS = ["monolith", "microservices"]
VUS_LEVELS = [10, 50, 100]

def ms(value, unit):
    factors = {"us": 0.001, "µs": 0.001, "ms": 1, "s": 1000}
    return float(value) * factors[unit]

def get_files(arch, vus, run):
    if run == 1:
        if vus == 50:
            return ROOT / f"{arch}-k6-output.txt", ROOT / f"{arch}-resource-usage.csv"
        return (
            ROOT / f"{arch}-{vus}vus-output.txt",
            ROOT / f"{arch}-{vus}vus-resource-usage.csv"
        )
    prefix = f"{arch}-{vus}vus-run{run}"
    return ROOT / f"{prefix}-output.txt", ROOT / f"{prefix}-resource-usage.csv"

def parse_k6(path):
    content = path.read_text(encoding="utf-8")

    req = re.search(
        r"http_reqs[.]*:\s*(\d+)\s+([\d.]+)/s", content
    )
    duration = re.search(
        r"http_req_duration[.]*:\s*avg=([\d.]+)(µs|us|ms|s).*?"
        r"p\(95\)=([\d.]+)(µs|us|ms|s)", content
    )
    failed = re.search(
        r"http_req_failed[.]*:\s*([\d.]+)%\s+(\d+)\s+out of\s+(\d+)",
        content
    )
    checks = re.search(
        r"checks_failed[.]*:\s*([\d.]+)%\s+(\d+)\s+out of\s+(\d+)",
        content
    )

    if not all([req, duration, failed, checks]):
        raise ValueError(f"Could not parse complete k6 summary: {path}")

    return {
        "requests": int(req.group(1)),
        "throughput": float(req.group(2)),
        "avg_latency_ms": ms(duration.group(1), duration.group(2)),
        "p95_latency_ms": ms(duration.group(3), duration.group(4)),
        "failed_requests": int(failed.group(2)),
        "failed_checks": int(checks.group(2)),
    }

def mib(value):
    match = re.match(r"([\d.]+)\s*(\w+)", value.strip())
    if not match:
        raise ValueError(f"Unknown memory value: {value}")
    amount, unit = match.groups()
    factors = {"B": 1 / 1048576, "KiB": 1 / 1024,
               "MiB": 1, "GiB": 1024}
    return float(amount) * factors[unit]

def parse_resources(path):
    samples = defaultdict(lambda: [0.0, 0.0])

    with path.open(newline="") as f:
        for row in csv.DictReader(f):
            timestamp = row["timestamp"]
            cpu = float(row["cpu_percent"].replace("%", ""))
            ram = mib(row["memory_usage"].split("/")[0])

            samples[timestamp][0] += cpu
            samples[timestamp][1] += ram

    if not samples:
        raise ValueError(f"No resource samples: {path}")

    cpu_values = [v[0] for v in samples.values()]
    ram_values = [v[1] for v in samples.values()]

    return {
        "cpu_avg": statistics.mean(cpu_values),
        "cpu_peak": max(cpu_values),
        "ram_avg": statistics.mean(ram_values),
        "ram_peak": max(ram_values),
        "samples": len(samples),
    }

def summary(values):
    return (
        statistics.mean(values),
        min(values),
        max(values),
        statistics.stdev(values)
    )

performance = []
resources = []

for arch in ARCHS:
    for vus in VUS_LEVELS:
        runs = []

        for run in [1, 2, 3]:
            output_file, resource_file = get_files(arch, vus, run)

            if not output_file.exists() or not resource_file.exists():
                raise FileNotFoundError(
                    f"Missing files for {arch}, {vus} VUs, run {run}"
                )

            k6 = parse_k6(output_file)
            resource = parse_resources(resource_file)

            runs.append((k6, resource))

            print(
                f"Verified: {arch:13} {vus:3} VUs "
                f"run{run} | {k6['throughput']:.2f} req/s | "
                f"{k6['failed_requests']} failed requests | "
                f"{resource['samples']} resource samples"
            )

        for metric in [
            "throughput", "avg_latency_ms", "p95_latency_ms", "requests"
        ]:
            values = [r[0][metric] for r in runs]
            avg, low, high, sd = summary(values)
            performance.append({
                "architecture": arch,
                "vus": vus,
                "metric": metric,
                "mean": avg,
                "min": low,
                "max": high,
                "std_dev": sd,
            })

        for metric in ["cpu_avg", "cpu_peak", "ram_avg", "ram_peak"]:
            values = [r[1][metric] for r in runs]
            avg, low, high, sd = summary(values)
            resources.append({
                "architecture": arch,
                "vus": vus,
                "metric": metric,
                "mean": avg,
                "min": low,
                "max": high,
                "std_dev": sd,
            })

        failures = sum(r[0]["failed_requests"] for r in runs)
        checks = sum(r[0]["failed_checks"] for r in runs)
        print(f"  Combined failures across 3 runs: {failures} requests, {checks} checks\n")

def save_csv(filename, rows):
    with (ROOT / filename).open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

save_csv("three-run-performance-summary.csv", performance)
save_csv("three-run-resource-summary.csv", resources)

print("\n========== PERFORMANCE SUMMARY ==========")
for arch in ARCHS:
    for vus in VUS_LEVELS:
        print(f"\n{arch.upper()} | {vus} VUs")
        for metric in ["throughput", "avg_latency_ms", "p95_latency_ms"]:
            row = next(
                x for x in performance
                if x["architecture"] == arch
                and x["vus"] == vus
                and x["metric"] == metric
            )
            unit = "req/s" if metric == "throughput" else "ms"
            print(
                f"  {metric:18}: mean {row['mean']:.2f} {unit} | "
                f"min {row['min']:.2f} | max {row['max']:.2f} | "
                f"SD {row['std_dev']:.2f}"
            )

print("\n========== RESOURCE SUMMARY ==========")
for arch in ARCHS:
    for vus in VUS_LEVELS:
        print(f"\n{arch.upper()} | {vus} VUs")
        for metric in ["cpu_avg", "cpu_peak", "ram_avg", "ram_peak"]:
            row = next(
                x for x in resources
                if x["architecture"] == arch
                and x["vus"] == vus
                and x["metric"] == metric
            )
            unit = "MiB" if "ram" in metric else "%"
            print(
                f"  {metric:18}: mean {row['mean']:.2f} {unit} | "
                f"min {row['min']:.2f} | max {row['max']:.2f} | "
                f"SD {row['std_dev']:.2f}"
            )

print("\nSaved:")
print("cloud-results/three-run-performance-summary.csv")
print("cloud-results/three-run-resource-summary.csv")
