import csv
from pathlib import Path
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "figures"
OUT.mkdir(exist_ok=True)

def read_summary(filename):
    with (ROOT / filename).open(newline="") as f:
        return list(csv.DictReader(f))

performance = read_summary("three-run-performance-summary.csv")
resources = read_summary("three-run-resource-summary.csv")

def get_value(rows, architecture, vus, metric):
    for row in rows:
        if (row["architecture"] == architecture
                and int(row["vus"]) == vus
                and row["metric"] == metric):
            return float(row["mean"])
    raise ValueError(f"Missing: {architecture}, {vus}, {metric}")

vus_levels = [10, 50, 100]
architectures = ["monolith", "microservices"]
labels = {"monolith": "Monolith", "microservices": "Microservices"}

def line_chart(filename, title, ylabel, data):
    plt.figure(figsize=(8, 5))
    for architecture in architectures:
        values = [data(architecture, v) for v in vus_levels]
        plt.plot(vus_levels, values, marker="o",
                 linewidth=2, label=labels[architecture])
    plt.title(title)
    plt.xlabel("Virtual users (VUs)")
    plt.ylabel(ylabel)
    plt.xticks(vus_levels)
    plt.grid(True, alpha=0.3)
    plt.legend()
    plt.tight_layout()
    plt.savefig(OUT / filename, dpi=180)
    plt.close()

# Local Windows performance results
local = {
    "monolith": {
        10: {"throughput": 96.13, "avg": 3.69, "p95": 9.64},
        50: {"throughput": 484.73, "avg": 2.74, "p95": 7.20},
        100: {"throughput": 977.14, "avg": 2.27, "p95": 6.94},
    },
    "microservices": {
        10: {"throughput": 94.41, "avg": 5.63, "p95": 16.92},
        50: {"throughput": 476.04, "avg": 4.30, "p95": 13.03},
        100: {"throughput": 963.89, "avg": 3.69, "p95": 14.83},
    },
}

line_chart(
    "local-throughput.png",
    "Local Windows: Throughput vs Load",
    "Throughput (requests/second)",
    lambda a, v: local[a][v]["throughput"],
)

plt.figure(figsize=(8, 5))
for architecture in architectures:
    plt.plot(vus_levels, [local[architecture][v]["avg"] for v in vus_levels],
             marker="o", linewidth=2, label=f"{labels[architecture]} average")
    plt.plot(vus_levels, [local[architecture][v]["p95"] for v in vus_levels],
             marker="s", linestyle="--", linewidth=2,
             label=f"{labels[architecture]} p95")
plt.title("Local Windows: Response Latency vs Load")
plt.xlabel("Virtual users (VUs)")
plt.ylabel("Latency (ms)")
plt.xticks(vus_levels)
plt.grid(True, alpha=0.3)
plt.legend()
plt.tight_layout()
plt.savefig(OUT / "local-latency.png", dpi=180)
plt.close()

# Codespaces performance results
line_chart(
    "codespaces-throughput.png",
    "Codespaces: Throughput vs Load",
    "Throughput (requests/second)",
    lambda a, v: get_value(performance, a, v, "throughput"),
)

plt.figure(figsize=(8, 5))
for architecture in architectures:
    for metric, style in [("avg_latency_ms", "-"), ("p95_latency_ms", "--")]:
        values = [get_value(performance, architecture, v, metric)
                  for v in vus_levels]
        suffix = "average" if metric == "avg_latency_ms" else "p95"
        plt.plot(vus_levels, values, marker="o", linestyle=style,
                 linewidth=2, label=f"{labels[architecture]} {suffix}")
plt.title("Codespaces: Response Latency vs Load")
plt.xlabel("Virtual users (VUs)")
plt.ylabel("Latency (ms)")
plt.xticks(vus_levels)
plt.grid(True, alpha=0.3)
plt.legend()
plt.tight_layout()
plt.savefig(OUT / "codespaces-latency.png", dpi=180)
plt.close()

line_chart(
    "codespaces-cpu.png",
    "Codespaces: Average Aggregate Container CPU",
    "Average CPU utilization (%)",
    lambda a, v: get_value(resources, a, v, "cpu_avg"),
)

line_chart(
    "codespaces-memory.png",
    "Codespaces: Average Combined Container Memory",
    "Average memory usage (MiB)",
    lambda a, v: get_value(resources, a, v, "ram_avg"),
)

print("Generated figures:")
for path in sorted(OUT.glob("*.png")):
    print(f"{path} ({path.stat().st_size:,} bytes)")
