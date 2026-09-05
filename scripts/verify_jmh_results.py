#!/usr/bin/env python3
"""Fail when a JMH JSON result is incomplete or contains invalid metrics."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any


NON_FINITE_TOKENS = frozenset({"NaN", "Infinity", "+Infinity", "-Infinity"})
SAMPLE_TIME_MODE = "sample"
REQUIRED_SAMPLE_PERCENTILES = ("0.0", "50.0", "90.0", "95.0", "99.0", "100.0")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify benchmark identity, sample completeness, and finite JMH metrics."
    )
    parser.add_argument("result", type=Path, help="JMH JSON result file")
    parser.add_argument(
        "--expected-benchmark",
        action="append",
        required=True,
        help="Fully qualified benchmark method; repeat for every expected result",
    )
    parser.add_argument("--expected-forks", type=int, required=True)
    parser.add_argument("--expected-iterations", type=int, required=True)
    parser.add_argument("--expected-threads", type=int)
    parser.add_argument(
        "--expected-param",
        action="append",
        default=[],
        metavar="NAME=VALUE",
        help="Expected JMH parameter value; repeat for every parameter to assert",
    )
    return parser.parse_args()


def parse_expected_params(values: list[str]) -> dict[str, str]:
    expected: dict[str, str] = {}
    for value in values:
        name, separator, parameter_value = value.partition("=")
        if not separator or not name or not parameter_value:
            raise ValueError(f"expected parameter must use NAME=VALUE: {value!r}")
        if name in expected:
            raise ValueError(f"expected parameter is repeated: {name}")
        expected[name] = parameter_value
    return expected


def find_non_finite(value: Any, path: str) -> list[str]:
    errors: list[str] = []
    if isinstance(value, float) and not math.isfinite(value):
        errors.append(f"{path} is not finite: {value!r}")
    elif isinstance(value, str) and value in NON_FINITE_TOKENS:
        errors.append(f"{path} is not finite: {value!r}")
    elif isinstance(value, list):
        for index, item in enumerate(value):
            errors.extend(find_non_finite(item, f"{path}[{index}]"))
    elif isinstance(value, dict):
        for key, item in value.items():
            errors.extend(find_non_finite(item, f"{path}.{key}"))
    return errors


def is_finite_number(value: Any) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
    )


def verify_measurement_matrix(
    matrix: Any,
    field: str,
    location: str,
    benchmark: str,
    expected_forks: int,
    expected_iterations: int,
) -> list[str]:
    if not isinstance(matrix, list):
        return [f"{location} {benchmark}: primaryMetric.{field} is missing"]
    if len(matrix) != expected_forks:
        return [
            f"{location} {benchmark}: {field} has {len(matrix)} fork(s), "
            f"expected {expected_forks}"
        ]

    errors: list[str] = []
    for fork_index, measurements in enumerate(matrix):
        if not isinstance(measurements, list):
            errors.append(
                f"{location} {benchmark}: {field}[{fork_index}] is not an array"
            )
        elif len(measurements) != expected_iterations:
            errors.append(
                f"{location} {benchmark}: {field}[{fork_index}] has "
                f"{len(measurements)} measurement(s), expected "
                f"{expected_iterations}"
            )
    return errors


def verify_primary_metric_identity(
    primary_metric: dict[str, Any], location: str, benchmark: str
) -> list[str]:
    errors: list[str] = []
    if not is_finite_number(primary_metric.get("score")):
        errors.append(
            f"{location} {benchmark}: primaryMetric.score must be a finite number"
        )
    if not isinstance(primary_metric.get("scoreUnit"), str) or not primary_metric[
        "scoreUnit"
    ]:
        errors.append(f"{location} {benchmark}: primaryMetric.scoreUnit is missing")
    return errors


def verify_sample_time_metric(
    primary_metric: dict[str, Any],
    location: str,
    benchmark: str,
    expected_forks: int,
    expected_iterations: int,
) -> list[str]:
    errors = verify_measurement_matrix(
        primary_metric.get("rawDataHistogram"),
        "rawDataHistogram",
        location,
        benchmark,
        expected_forks,
        expected_iterations,
    )
    percentiles = primary_metric.get("scorePercentiles")
    if not isinstance(percentiles, dict):
        errors.append(
            f"{location} {benchmark}: primaryMetric.scorePercentiles is missing"
        )
    else:
        missing = [
            percentile
            for percentile in REQUIRED_SAMPLE_PERCENTILES
            if percentile not in percentiles
        ]
        if missing:
            errors.append(
                f"{location} {benchmark}: scorePercentiles is missing "
                f"{', '.join(missing)}"
            )
        for percentile, value in percentiles.items():
            if not is_finite_number(value):
                errors.append(
                    f"{location} {benchmark}: scorePercentiles[{percentile!r}] "
                    "must be a finite number"
                )

    histograms = primary_metric.get("rawDataHistogram")
    if isinstance(histograms, list):
        for fork_index, measurements in enumerate(histograms):
            if not isinstance(measurements, list):
                continue
            for iteration_index, buckets in enumerate(measurements):
                bucket_path = (
                    f"{location}.primaryMetric.rawDataHistogram"
                    f"[{fork_index}][{iteration_index}]"
                )
                if not isinstance(buckets, list) or not buckets:
                    errors.append(f"{bucket_path} must be a non-empty histogram")
                    continue
                for bucket_index, bucket in enumerate(buckets):
                    if (
                        not isinstance(bucket, list)
                        or len(bucket) != 2
                        or not is_finite_number(bucket[0])
                        or not isinstance(bucket[1], int)
                        or isinstance(bucket[1], bool)
                        or bucket[1] <= 0
                    ):
                        errors.append(
                            f"{bucket_path}[{bucket_index}] must be "
                            "[finite value, positive integer count]"
                        )
    errors.extend(find_non_finite(primary_metric, f"{location}.primaryMetric"))
    return errors


def verify_results(
    result_path: Path,
    expected_benchmarks: set[str],
    expected_forks: int,
    expected_iterations: int,
    expected_threads: int | None = None,
    expected_params: dict[str, str] | None = None,
) -> list[str]:
    if expected_forks <= 0:
        return ["expected_forks must be greater than zero"]
    if expected_iterations <= 0:
        return ["expected_iterations must be greater than zero"]
    if expected_threads is not None and expected_threads <= 0:
        return ["expected_threads must be greater than zero"]

    try:
        results = json.loads(result_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as failure:
        return [f"{result_path}: cannot read JMH JSON: {failure}"]

    if not isinstance(results, list) or not results:
        return [f"{result_path}: expected a non-empty JSON result array"]

    errors: list[str] = []
    actual_benchmarks: list[str] = []
    actual_identities: list[tuple[str, tuple[tuple[str, str], ...]]] = []
    for index, result in enumerate(results):
        location = f"{result_path}[{index}]"
        if not isinstance(result, dict):
            errors.append(f"{location}: expected an object")
            continue

        benchmark = result.get("benchmark")
        if not isinstance(benchmark, str) or not benchmark:
            errors.append(f"{location}: benchmark name is missing")
            continue
        actual_benchmarks.append(benchmark)
        parameters = result.get("params", {})
        if not isinstance(parameters, dict):
            errors.append(f"{location} {benchmark}: params must be an object")
            parameters = {}
        normalized_parameters = tuple(
            sorted((str(name), str(value)) for name, value in parameters.items())
        )
        actual_identities.append((benchmark, normalized_parameters))
        for name, expected_value in (expected_params or {}).items():
            if str(parameters.get(name)) != expected_value:
                errors.append(
                    f"{location} {benchmark}: params[{name!r}]="
                    f"{parameters.get(name)!r}, expected {expected_value!r}"
                )

        if result.get("forks") != expected_forks:
            errors.append(
                f"{location} {benchmark}: forks={result.get('forks')!r}, "
                f"expected {expected_forks}"
            )
        if result.get("measurementIterations") != expected_iterations:
            errors.append(
                f"{location} {benchmark}: measurementIterations="
                f"{result.get('measurementIterations')!r}, expected {expected_iterations}"
            )
        if expected_threads is not None and result.get("threads") != expected_threads:
            errors.append(
                f"{location} {benchmark}: threads={result.get('threads')!r}, "
                f"expected {expected_threads}"
            )

        primary_metric = result.get("primaryMetric")
        if not isinstance(primary_metric, dict):
            errors.append(f"{location} {benchmark}: primaryMetric is missing")
            continue
        errors.extend(
            verify_primary_metric_identity(primary_metric, location, benchmark)
        )
        if result.get("mode") == SAMPLE_TIME_MODE:
            errors.extend(
                verify_sample_time_metric(
                    primary_metric,
                    location,
                    benchmark,
                    expected_forks,
                    expected_iterations,
                )
            )
        else:
            errors.extend(
                verify_measurement_matrix(
                    primary_metric.get("rawData"),
                    "rawData",
                    location,
                    benchmark,
                    expected_forks,
                    expected_iterations,
                )
            )
            errors.extend(find_non_finite(primary_metric, f"{location}.primaryMetric"))
        secondary_metrics = result.get("secondaryMetrics")
        if not isinstance(secondary_metrics, dict):
            errors.append(f"{location} {benchmark}: secondaryMetrics is missing")
        elif result.get("mode") != SAMPLE_TIME_MODE:
            errors.extend(
                find_non_finite(secondary_metrics, f"{location}.secondaryMetrics")
            )

    actual_set = set(actual_benchmarks)
    duplicates = sorted(
        identity for identity in set(actual_identities) if actual_identities.count(identity) > 1
    )
    if duplicates:
        rendered = ", ".join(
            benchmark
            if not parameters
            else f"{benchmark}[{','.join(f'{name}={value}' for name, value in parameters)}]"
            for benchmark, parameters in duplicates
        )
        errors.append(f"{result_path}: duplicate benchmark result(s): {rendered}")
    missing = sorted(expected_benchmarks - actual_set)
    unexpected = sorted(actual_set - expected_benchmarks)
    if missing:
        errors.append(f"{result_path}: missing benchmark result(s): {', '.join(missing)}")
    if unexpected:
        errors.append(
            f"{result_path}: unexpected benchmark result(s): {', '.join(unexpected)}"
        )
    return errors


def main() -> int:
    args = parse_args()
    try:
        expected_params = parse_expected_params(args.expected_param)
    except ValueError as failure:
        print(f"JMH result verification failed: {failure}", file=sys.stderr)
        return 2
    errors = verify_results(
        args.result,
        set(args.expected_benchmark),
        args.expected_forks,
        args.expected_iterations,
        args.expected_threads,
        expected_params,
    )
    if errors:
        print("JMH result verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(f"JMH result verification passed: {args.result}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
