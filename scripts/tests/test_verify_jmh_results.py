import json
import tempfile
import unittest
from pathlib import Path

from scripts.verify_jmh_results import verify_results


BENCHMARK = "com.alibaba.compileflow.benchmarks.ExampleBenchmark.execute"


def result(
    raw_data: list[list[float]],
    score_error: float | str = 0.5,
    params: dict[str, str] | None = None,
) -> dict:
    return {
        "benchmark": BENCHMARK,
        "forks": 2,
        "threads": 4,
        "measurementIterations": 3,
        "primaryMetric": {
            "score": 42.0,
            "scoreError": score_error,
            "scoreConfidence": [41.5, 42.5],
            "scoreUnit": "ops/ms",
            "rawData": raw_data,
        },
        "secondaryMetrics": {},
        "params": params or {},
    }


def sample_time_result() -> dict:
    return {
        "benchmark": BENCHMARK,
        "mode": "sample",
        "forks": 2,
        "threads": 4,
        "measurementIterations": 3,
        "primaryMetric": {
            "score": 42.0,
            "scoreError": 0.5,
            "scoreConfidence": [41.5, 42.5],
            "scoreUnit": "us/op",
            "scorePercentiles": {
                "0.0": 40.0,
                "50.0": 42.0,
                "90.0": 44.0,
                "95.0": 45.0,
                "99.0": 46.0,
                "100.0": 47.0,
            },
            "rawDataHistogram": [
                [[[40.0, 1]], [[41.0, 1]], [[42.0, 1]]],
                [[[43.0, 1]], [[44.0, 1]], [[45.0, 1]]],
            ],
        },
        "secondaryMetrics": {
            "p0.50": {
                "score": 42.0,
                "scoreError": "NaN",
                "scoreConfidence": ["NaN", "NaN"],
            }
        },
    }


class VerifyJmhResultsTest(unittest.TestCase):

    def verify(self, payload: object) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            return verify_results(path, {BENCHMARK}, 2, 3, 4)

    def test_accepts_complete_finite_results(self) -> None:
        errors = self.verify([result([[40.0, 41.0, 42.0], [43.0, 44.0, 45.0]])])

        self.assertEqual([], errors)

    def test_accepts_distinct_parameter_combinations_for_one_benchmark(self) -> None:
        raw_data = [[40.0, 41.0, 42.0], [43.0, 44.0, 45.0]]
        payload = [
            result(raw_data, params={"snapshotBytes": "16384"}),
            result(raw_data, params={"snapshotBytes": "65536"}),
        ]

        errors = self.verify(payload)

        self.assertEqual([], errors)

    def test_rejects_duplicate_benchmark_and_parameter_identity(self) -> None:
        raw_data = [[40.0, 41.0, 42.0], [43.0, 44.0, 45.0]]
        payload = [
            result(raw_data, params={"snapshotBytes": "16384"}),
            result(raw_data, params={"snapshotBytes": "16384"}),
        ]

        errors = self.verify(payload)

        self.assertTrue(any("duplicate benchmark result" in error for error in errors))

    def test_checks_expected_parameter_values(self) -> None:
        raw_data = [[40.0, 41.0, 42.0], [43.0, 44.0, 45.0]]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.json"
            path.write_text(
                json.dumps([result(raw_data, params={"snapshotBytes": "65536"})]),
                encoding="utf-8",
            )

            errors = verify_results(
                path,
                {BENCHMARK},
                2,
                3,
                4,
                {"snapshotBytes": "16384"},
            )

        self.assertTrue(any("params['snapshotBytes']" in error for error in errors))

    def test_rejects_partial_fork_measurements(self) -> None:
        errors = self.verify([result([[40.0], [43.0, 44.0, 45.0]])])

        self.assertTrue(any("has 1 measurement(s), expected 3" in error for error in errors))

    def test_rejects_jmh_nan_tokens(self) -> None:
        errors = self.verify(
            [result([[40.0, 41.0, 42.0], [43.0, 44.0, 45.0]], "NaN")]
        )

        self.assertTrue(any("is not finite: 'NaN'" in error for error in errors))

    def test_rejects_missing_and_unexpected_benchmarks(self) -> None:
        payload = result([[40.0, 41.0, 42.0], [43.0, 44.0, 45.0]])
        payload["benchmark"] = "com.example.Unexpected.run"

        errors = self.verify([payload])

        self.assertTrue(any("missing benchmark result" in error for error in errors))
        self.assertTrue(any("unexpected benchmark result" in error for error in errors))

    def test_accepts_sample_time_histograms_and_jmh_nan_auxiliary_errors(self) -> None:
        errors = self.verify([sample_time_result()])

        self.assertEqual([], errors)

    def test_rejects_partial_sample_time_histograms(self) -> None:
        payload = sample_time_result()
        payload["primaryMetric"]["rawDataHistogram"][0] = payload["primaryMetric"][
            "rawDataHistogram"
        ][0][:1]

        errors = self.verify([payload])

        self.assertTrue(any("has 1 measurement(s), expected 3" in error for error in errors))

    def test_rejects_missing_sample_time_percentiles(self) -> None:
        payload = sample_time_result()
        del payload["primaryMetric"]["scorePercentiles"]["99.0"]

        errors = self.verify([payload])

        self.assertTrue(any("scorePercentiles is missing 99.0" in error for error in errors))

    def test_rejects_non_finite_sample_time_primary_metric(self) -> None:
        payload = sample_time_result()
        payload["primaryMetric"]["score"] = "NaN"

        errors = self.verify([payload])

        self.assertTrue(any("primaryMetric.score is not finite" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
