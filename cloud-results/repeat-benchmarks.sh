#!/usr/bin/env bash

ROOT="/workspaces/monolith-microservices-performance"
cd "$ROOT" || exit 1

run_test() {
    ARCH="$1"
    VUS="$2"
    RUN="$3"

    echo ""
    echo "================================================"
    echo "STARTING: $ARCH | $VUS VUs | RUN $RUN"
    echo "================================================"

    if [ "$ARCH" = "monolith" ]; then
        COMPOSE="compose.monolith.yml"
        SCRIPT="monolith-load.js"
        READY_URL="http://localhost:8080/products"
    else
        COMPOSE="compose.microservices.yml"
        SCRIPT="microservices-load.js"
    fi

    # Stop both architectures and remove database volumes.
    docker compose -f compose.monolith.yml down -v --remove-orphans
    docker compose -f compose.microservices.yml down -v --remove-orphans

    # Start the selected architecture.
    docker compose -f "$COMPOSE" up -d

    # Wait for application readiness.
    echo "Waiting for services..."

    READY=0

    for attempt in $(seq 1 120); do
        if [ "$ARCH" = "monolith" ]; then
            if curl -fsS "$READY_URL" >/dev/null 2>&1; then
                READY=1
                break
            fi
        else
            if curl -fsS http://localhost:8081/products >/dev/null 2>&1 &&
               curl -fsS http://localhost:8082/actuator/health >/dev/null 2>&1 &&
               curl -fsS http://localhost:8083/actuator/health >/dev/null 2>&1; then
                READY=1
                break
            fi
        fi

        sleep 2
    done

    if [ "$READY" -ne 1 ]; then
        echo "ERROR: $ARCH did not become ready."
        docker compose -f "$COMPOSE" logs --tail=50
        return 1
    fi

    echo "$ARCH READY"

    # Prepare unique output filenames.
    PREFIX="cloud-results/${ARCH}-${VUS}vus-${RUN}"

    echo "timestamp,container,cpu_percent,memory_usage,memory_percent" \
        > "${PREFIX}-resource-usage.csv"

    # Start k6.
    k6 run --vus "$VUS" --duration 60s \
        --out "json=${PREFIX}.json" \
        "$SCRIPT" \
        > "${PREFIX}-output.txt" 2>&1 &

    K6_PID=$!

    # Capture Docker resource usage while k6 is running.
    while kill -0 "$K6_PID" 2>/dev/null; do
        TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

        while IFS= read -r LINE; do
            echo "$TIMESTAMP,$LINE" \
                >> "${PREFIX}-resource-usage.csv"
        done < <(
            docker stats --no-stream \
                --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
                2>/dev/null
        )

        sleep 1
    done

    wait "$K6_PID"
    K6_EXIT=$?

    echo ""
    echo "========== $ARCH | $VUS VUs | $RUN =========="
    tail -35 "${PREFIX}-output.txt"

    echo ""
    echo "K6 exit code: $K6_EXIT"
    echo "Resource samples:"
    wc -l "${PREFIX}-resource-usage.csv"

    if [ "$K6_EXIT" -ne 0 ]; then
        echo "WARNING: This benchmark returned a non-zero exit code."
    fi

    echo "COMPLETED: $ARCH | $VUS VUs | $RUN"
}

# Two additional repetitions: run2 and run3.
for RUN in run2 run3; do
    for VUS in 10 50 100; do
        run_test monolith "$VUS" "$RUN" || echo "MONOLITH RUN FAILED"

        run_test microservices "$VUS" "$RUN" || echo "MICROSERVICES RUN FAILED"
    done
done

echo ""
echo "================================================"
echo "ALL ADDITIONAL BENCHMARKS FINISHED"
echo "================================================"

# Stop containers after all experiments.
docker compose -f compose.monolith.yml down
docker compose -f compose.microservices.yml down

echo "Docker containers stopped."
