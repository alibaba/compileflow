from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.check_dco import check_range, signed_off_by_author


class CheckDcoTest(unittest.TestCase):
    def test_signoff_must_match_author_email(self) -> None:
        message = "Change\n\nSigned-off-by: Reviewer <reviewer@example.com>\n"
        self.assertFalse(signed_off_by_author(message, "author@example.com"))
        self.assertTrue(signed_off_by_author(message, "REVIEWER@example.com"))

    def test_range_rejects_unsigned_commit_and_accepts_signed_commit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self.run_git(repo, "init", "-q")
            self.run_git(repo, "config", "user.name", "DCO Author")
            self.run_git(repo, "config", "user.email", "author@example.com")

            (repo / "tracked.txt").write_text("base\n", encoding="utf-8")
            self.run_git(repo, "add", "tracked.txt")
            self.run_git(repo, "commit", "-q", "-m", "Base")
            base = self.run_git(repo, "rev-parse", "HEAD").strip()

            (repo / "tracked.txt").write_text("unsigned\n", encoding="utf-8")
            self.run_git(repo, "commit", "-q", "-am", "Unsigned change")
            unsigned = self.run_git(repo, "rev-parse", "HEAD").strip()
            errors = check_range(repo, base, unsigned)
            self.assertEqual(1, len(errors))
            self.assertIn("Unsigned change", errors[0])

            self.run_git(repo, "commit", "--amend", "-q", "--signoff", "--no-edit")
            signed = self.run_git(repo, "rev-parse", "HEAD").strip()
            self.assertEqual([], check_range(repo, base, signed))

    @staticmethod
    def run_git(repo: Path, *args: str) -> str:
        return subprocess.run(
            ["git", "-C", str(repo), *args],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ).stdout


if __name__ == "__main__":
    unittest.main()
