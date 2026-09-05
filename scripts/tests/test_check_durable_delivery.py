"""Tests for the greenfield Durable delivery gate."""

from __future__ import annotations

import unittest

from scripts.check_durable_delivery import (
    DeliveryError,
    INTEGRATION_EVENTS,
    KERNEL_TABLES,
    extract_outbox_event_types,
    extract_sql_tables,
    require,
    strip_sql_comments,
)


class DurableDeliveryGateTest(unittest.TestCase):
    def test_extracts_exact_kernel_tables(self) -> None:
        source = "\n".join(
            f"CREATE TABLE public.{table} (id uuid);" for table in sorted(KERNEL_TABLES)
        )
        self.assertEqual(KERNEL_TABLES, extract_sql_tables(source))

    def test_ignores_commented_table_names(self) -> None:
        source = "-- CREATE TABLE cf_durable_alias (id uuid);\nCREATE TABLE cf_durable_run (id uuid);"
        self.assertEqual(frozenset({"cf_durable_run"}), extract_sql_tables(source))

    def test_extracts_closed_outbox_events(self) -> None:
        values = ", ".join(f"'{event}'" for event in sorted(INTEGRATION_EVENTS))
        source = (
            "CONSTRAINT ck_cf_durable_outbox_type "
            f"CHECK (event_type IN ({values}))"
        )
        self.assertEqual(INTEGRATION_EVENTS, extract_outbox_event_types(source))

    def test_rejects_missing_outbox_constraint(self) -> None:
        with self.assertRaisesRegex(DeliveryError, "closed Outbox"):
            extract_outbox_event_types("CREATE TABLE cf_durable_outbox (event_type text)")

    def test_strips_only_sql_line_comments(self) -> None:
        self.assertEqual("\nSELECT 'Alias';", strip_sql_comments("-- Alias\nSELECT 'Alias';"))

    def test_require_reports_actionable_failure(self) -> None:
        with self.assertRaisesRegex(DeliveryError, "exact Version"):
            require(False, "exact Version required")


if __name__ == "__main__":
    unittest.main()
