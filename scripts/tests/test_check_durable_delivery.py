"""Tests for the greenfield Durable delivery gate."""

from __future__ import annotations

import unittest
from unittest.mock import patch

from scripts.check_durable_delivery import (
    DeliveryError,
    INTEGRATION_EVENTS,
    KERNEL_TABLES,
    check_documented_boundary,
    extract_outbox_event_types,
    extract_sql_tables,
    require,
    strip_sql_comments,
)


class DurableDeliveryGateTest(unittest.TestCase):
    ARCHITECTURE = """
Starting a Run stores an immutable Process definition and binds the Run to its `processId`.
Aliases belong to the Deploy control plane; the Engine rejects a reused Run ID before resolving an Alias.
The Kernel does not persist generated Java source, classes, bytecode, live object instances,
executor state, or an in-memory route. Declared application values are serialized as typed state.
A missing binding is an application/runtime capability problem.
The Wait token is an opaque, one-shot credential. An application token table is not required.
Recovery does not provide fuzzy matching and uses a content-addressed `processId`.
Namespace and optional Version belong to Run admission attribution, not stored semantic identity.
Every Run stores one exact root `processId`.
"""

    def test_accepts_nonpersistent_runtime_and_serialized_application_values(self) -> None:
        with patch("scripts.check_durable_delivery.AUTHORITATIVE_DOCUMENTS", ()), patch(
            "scripts.check_durable_delivery.read", return_value=self.ARCHITECTURE
        ):
            check_documented_boundary()

    def test_rejects_persisting_live_runtime_objects(self) -> None:
        invalid = self.ARCHITECTURE.replace("does not persist", "persists")
        with patch("scripts.check_durable_delivery.AUTHORITATIVE_DOCUMENTS", ()), patch(
            "scripts.check_durable_delivery.read", return_value=invalid
        ), self.assertRaisesRegex(DeliveryError, "boundary statement"):
            check_documented_boundary()

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
