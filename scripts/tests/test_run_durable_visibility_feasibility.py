import argparse
import unittest

from scripts.run_durable_visibility_feasibility import (
    FeasibilityError,
    QueryScenario,
    plan_summary,
    query_scenarios,
    validate_query_plan,
    validate_request,
)


COMMIT = "a" * 40
IMAGE_DIGEST = "b" * 64


def arguments(**overrides: object) -> argparse.Namespace:
    values: dict[str, object] = {
        "allow_schema_reset": True,
        "schema": "cf_durable_visibility_feasibility",
        "runs": 1_000_000,
        "commit": COMMIT,
        "declared_postgres": "17.11",
        "declared_image": (
            "postgres:17.11-alpine3.24@sha256:" + IMAGE_DIGEST
        ),
    }
    values.update(overrides)
    return argparse.Namespace(**values)


def valid_plan() -> list[dict[str, object]]:
    return [
        {
            "Plan": {
                "Node Type": "Limit",
                "Actual Rows": 100,
                "Shared Hit Blocks": 30,
                "Shared Read Blocks": 2,
                "Temp Read Blocks": 0,
                "Temp Written Blocks": 0,
                "Plans": [
                    {
                        "Node Type": "Nested Loop",
                        "Plans": [
                            {
                                "Node Type": "Index Only Scan",
                                "Relation Name": "cf_vis_value",
                                "Index Name": "cf_vis_value_keyword_idx",
                            },
                            {
                                "Node Type": "Limit",
                                "Plans": [
                                    {
                                        "Node Type": "Index Scan",
                                        "Relation Name": "cf_vis_run",
                                        "Index Name": "cf_vis_run_pkey",
                                    }
                                ],
                            },
                        ],
                    }
                ],
            },
            "Planning Time": 0.2,
            "Execution Time": 4.2,
        }
    ]


class RunDurableVisibilityFeasibilityTest(unittest.TestCase):

    def test_accepts_bounded_explicit_scratch_request(self) -> None:
        validate_request(arguments())

    def test_rejects_broad_or_implicit_schema_reset(self) -> None:
        for request in (
            arguments(allow_schema_reset=False),
            arguments(schema="public"),
            arguments(schema="pg_temp"),
            arguments(schema="other"),
        ):
            with self.subTest(request=request):
                with self.assertRaises(FeasibilityError):
                    validate_request(request)

    def test_rejects_unpinned_image_and_unbounded_corpus(self) -> None:
        with self.assertRaisesRegex(FeasibilityError, "immutable"):
            validate_request(arguments(declared_image="postgres:latest"))
        with self.assertRaisesRegex(FeasibilityError, "runs"):
            validate_request(arguments(runs=9_999))

    def test_declares_exact_initial_query_matrix(self) -> None:
        scenarios = query_scenarios("cf_test")

        self.assertEqual(
            {
                "keyword-common",
                "keyword-selective",
                "keyword-continuation",
                "long-common",
                "long-equality-selective",
                "long-continuation",
                "boolean-equality",
                "boolean-continuation",
                "instant-equality-common",
                "instant-equality-continuation",
            },
            {scenario.name for scenario in scenarios},
        )
        self.assertTrue(
            all("LIMIT 100" in scenario.sql for scenario in scenarios)
        )
        self.assertTrue(
            all("run_created_at" in scenario.sql for scenario in scenarios)
        )
        self.assertTrue(all("BETWEEN" not in item.sql for item in scenarios))

    def test_accepts_intended_value_and_run_indexes(self) -> None:
        scenario = QueryScenario(
            "keyword-common",
            "SELECT 1",
            ("cf_vis_value_keyword_idx",),
            100,
        )

        self.assertEqual([], validate_query_plan(scenario, valid_plan()))
        summary = plan_summary(valid_plan())
        self.assertEqual(100, summary["actualRows"])
        self.assertEqual(
            ["cf_vis_run_pkey", "cf_vis_value_keyword_idx"],
            summary["indexes"],
        )

    def test_accepts_postgres_integral_float_row_counts(self) -> None:
        document = valid_plan()
        document[0]["Plan"]["Actual Rows"] = 100.0
        scenario = QueryScenario(
            "keyword-common",
            "SELECT 1",
            ("cf_vis_value_keyword_idx",),
            100,
        )

        self.assertEqual([], validate_query_plan(scenario, document))
        self.assertEqual(100, plan_summary(document)["actualRows"])

        document[0]["Plan"]["Actual Rows"] = 100.5
        self.assertTrue(validate_query_plan(scenario, document))
        self.assertIsNone(plan_summary(document)["actualRows"])

    def test_rejects_sequential_scan_missing_index_and_short_result(self) -> None:
        document = valid_plan()
        value_scan = document[0]["Plan"]["Plans"][0]["Plans"][0]
        value_scan["Node Type"] = "Seq Scan"
        del value_scan["Index Name"]
        document[0]["Plan"]["Actual Rows"] = 12
        scenario = QueryScenario(
            "keyword-common",
            "SELECT 1",
            ("cf_vis_value_keyword_idx",),
            100,
        )

        errors = validate_query_plan(scenario, document)

        self.assertTrue(any("missing required index" in item for item in errors))
        self.assertTrue(any("sequential scan" in item for item in errors))
        self.assertTrue(any("below 100" in item for item in errors))

    def test_reports_malformed_plan_without_crashing_summary(self) -> None:
        scenario = QueryScenario(
            "broken",
            "SELECT 1",
            (),
            1,
        )

        self.assertTrue(validate_query_plan(scenario, {}))
        self.assertIsNone(plan_summary({})["actualRows"])

    def test_rejects_buffer_overrun(self) -> None:
        document = valid_plan()
        document[0]["Plan"]["Shared Hit Blocks"] = 101
        scenario = QueryScenario(
            "long-common",
            "SELECT 1",
            ("cf_vis_value_keyword_idx",),
            100,
            maximum_buffer_blocks=100,
        )

        errors = validate_query_plan(scenario, document)

        self.assertTrue(any("hard budget" in item for item in errors))


if __name__ == "__main__":
    unittest.main()
