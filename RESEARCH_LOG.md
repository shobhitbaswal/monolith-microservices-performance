# Research Log: Monolith vs Microservices

## Project objective

Compare the performance and resource-cost trade-offs of a monolithic and a microservices architecture for an e-commerce application under controlled workload conditions.

## Log Entry 001 — Baseline Test Configuration Fix

**Date:** 2026-10-09

**Phase:** Repository inspection and baseline verification

### Initial problem

The command `mvn clean test` failed because the Spring application context could not start during the integration test.

### Root cause

The test configuration in `src/main/resources/application-test.yml` referenced `${TEST_SHIPPING_EMAIL}` without providing a default value. The variable was not available in the test environment.

### Change made

Added the test-only fallback `test-shipping@example.com` to the `shipping-team-email` property in `application-test.yml`.

### Verification

Reran `mvn clean test`.

* Tests run: 73
* Failures: 0
* Errors: 0
* Skipped: 1
* Result: BUILD SUCCESS

### Research implication

A pre-existing test-configuration issue was corrected before performance experimentation. This change was intended to allow the test application context to initialize; it was not a performance optimization.

### Reproducibility note

The test configuration was modified locally. Preserve this change in version control and include it in the experiment setup documentation. Confirm that test notifications cannot reach real recipients before running further tests.

### Next steps

* Verify application startup and database configuration.
* Identify critical e-commerce API workflows.
* Establish a reproducible monolithic performance baseline.
* Record the workload, test environment, and resource measurements.
