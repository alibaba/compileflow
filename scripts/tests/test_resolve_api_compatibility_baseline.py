import unittest
from unittest.mock import patch

from scripts.resolve_api_compatibility_baseline import (
    ReleaseVersion,
    parse_release_version,
    repository_tags,
    resolve_baseline,
)


class ResolveApiCompatibilityBaselineTest(unittest.TestCase):

    def test_first_stable_major_release_has_no_baseline(self) -> None:
        baseline = resolve_baseline(
            "2.0.0",
            ["v1.2.0", "v2.0.0-RC1", "not-a-version"],
        )

        self.assertIsNone(baseline)

    def test_uses_latest_earlier_stable_release_in_same_major(self) -> None:
        baseline = resolve_baseline(
            "2.2.0",
            [
                "v1.9.9",
                "v2.0.0",
                "v2.1.0",
                "v2.1.1-RC1",
                "v2.2.0",
                "v2.3.0",
            ],
        )

        self.assertEqual(ReleaseVersion(2, 1, 0), baseline)

    def test_prerelease_checks_latest_stable_when_available(self) -> None:
        baseline = resolve_baseline(
            "2.1.0-RC1",
            ["v2.0.0", "v2.1.0-Beta1"],
        )

        self.assertEqual(ReleaseVersion(2, 0, 0), baseline)

    def test_prerelease_of_first_major_release_may_have_no_baseline(self) -> None:
        self.assertIsNone(
            resolve_baseline("3.0.0-RC1", ["v2.9.0"])
        )

    def test_rejects_missing_same_major_history_after_first_release(self) -> None:
        with self.assertRaisesRegex(ValueError, "history may be shallow"):
            resolve_baseline("2.0.1", ["v1.2.0"])

    def test_rejects_invalid_numeric_prerelease(self) -> None:
        with self.assertRaisesRegex(ValueError, "leading zeroes"):
            parse_release_version("2.0.0-RC.01")

    @patch("scripts.resolve_api_compatibility_baseline.subprocess.run")
    def test_reads_only_tags_reachable_from_release_commit(self, run) -> None:
        run.return_value.stdout = "v2.0.0\n"

        self.assertEqual(["v2.0.0"], repository_tags())
        run.assert_called_once_with(
            ["git", "tag", "--merged", "HEAD", "--list", "v*"],
            check=True,
            capture_output=True,
            text=True,
        )


if __name__ == "__main__":
    unittest.main()
