#!/usr/bin/env python3
"""Require Developer Certificate of Origin sign-off on every non-merge commit."""

from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path


SIGN_OFF_RE = re.compile(
    r"^Signed-off-by:\s*[^<>\r\n]+\s+<([^<>\s]+)>\s*$",
    re.IGNORECASE | re.MULTILINE,
)


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


def signed_off_by_author(message: str, author_email: str) -> bool:
    return any(
        email.casefold() == author_email.casefold()
        for email in SIGN_OFF_RE.findall(message)
    )


def check_range(repo: Path, base: str, head: str) -> list[str]:
    base_commit = git(repo, "rev-parse", "--verify", f"{base}^{{commit}}").strip()
    head_commit = git(repo, "rev-parse", "--verify", f"{head}^{{commit}}").strip()
    commits = git(
        repo,
        "rev-list",
        "--reverse",
        "--no-merges",
        f"{base_commit}..{head_commit}",
    ).splitlines()

    errors: list[str] = []
    for commit in commits:
        author_email, subject, message = git(
            repo,
            "show",
            "-s",
            "--format=%ae%n%s%n%B",
            commit,
        ).split("\n", 2)
        if not signed_off_by_author(message, author_email):
            errors.append(
                f"{commit[:12]} {subject!r} has no Signed-off-by trailer matching author {author_email}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check DCO sign-off for every non-merge commit in BASE..HEAD."
    )
    parser.add_argument("--base", required=True, help="base commit or revision")
    parser.add_argument("--head", required=True, help="head commit or revision")
    parser.add_argument("--repo", type=Path, default=Path("."), help="Git repository")
    args = parser.parse_args()

    try:
        errors = check_range(args.repo, args.base, args.head)
    except subprocess.CalledProcessError as exc:
        detail = exc.stderr.strip() or str(exc)
        print(f"DCO check could not inspect the requested commit range: {detail}")
        return 1

    if errors:
        print("DCO sign-off check failed:")
        for error in errors:
            print(f"- {error}")
        print("Amend each commit with `git commit --amend --signoff` and update the pull request.")
        return 1

    print("DCO sign-off check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
