"""Shared JUnit XML parsing for repository evidence verifiers."""

from __future__ import annotations

import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


COUNT_ATTRIBUTES = ("tests", "failures", "errors", "skipped")


@dataclass(frozen=True)
class JUnitCase:
    """One test result with the status needed by release evidence gates."""

    classname: str
    name: str
    failed: bool
    errored: bool
    skipped: bool

    @property
    def identity(self) -> tuple[str, str]:
        return self.classname, self.name


def parse_junit_report(path: Path) -> tuple[list[JUnitCase], list[str]]:
    """Parse one JUnit report and validate its structural accounting."""
    if not path.is_file():
        return [], [f"missing JUnit report: {path}"]
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exception:
        return [], [f"cannot parse JUnit report {path}: {exception}"]

    if root.tag == "testsuite":
        suites = [root]
    elif root.tag == "testsuites":
        suites = list(root.findall("testsuite"))
    else:
        return [], [f"{path}: expected testsuite or testsuites root, found {root.tag!r}"]
    if not suites:
        return [], [f"{path}: report contains no test suites"]

    errors: list[str] = []
    cases: list[JUnitCase] = []
    for index, suite in enumerate(suites):
        suite_cases = [_case(element) for element in suite.findall("testcase")]
        cases.extend(suite_cases)
        label = f"{path}: suite {suite.get('name') or index!r}"
        _compare_counts(label, suite, suite_cases, errors)
    if root.tag == "testsuites":
        _compare_counts(f"{path}: report root", root, cases, errors)

    seen: set[tuple[str, str]] = set()
    for case in cases:
        if not case.classname or not case.name:
            errors.append(
                f"{path}: testcase has incomplete identity: "
                f"classname={case.classname!r}, name={case.name!r}"
            )
            continue
        if case.identity in seen:
            errors.append(
                f"{path}: duplicate testcase: {case.classname} :: {case.name}"
            )
        seen.add(case.identity)
    return cases, errors


def status_counts(cases: list[JUnitCase]) -> dict[str, int]:
    """Return actual JUnit counts for parsed cases."""
    return {
        "tests": len(cases),
        "failures": sum(case.failed for case in cases),
        "errors": sum(case.errored for case in cases),
        "skipped": sum(case.skipped for case in cases),
    }


def _case(element: ET.Element) -> JUnitCase:
    return JUnitCase(
        classname=element.get("classname", "").strip(),
        name=element.get("name", "").strip(),
        failed=element.find("failure") is not None,
        errored=element.find("error") is not None,
        skipped=element.find("skipped") is not None,
    )


def _compare_counts(
    label: str,
    element: ET.Element,
    cases: list[JUnitCase],
    errors: list[str],
) -> None:
    actual = status_counts(cases)
    for name in COUNT_ATTRIBUTES:
        raw = element.get(name)
        if raw is None:
            errors.append(f"{label}: missing {name!r} count")
            continue
        try:
            declared = int(raw)
        except ValueError:
            errors.append(f"{label}: invalid {name!r} count {raw!r}")
            continue
        if declared < 0:
            errors.append(f"{label}: negative {name!r} count {declared}")
        elif declared != actual[name]:
            errors.append(
                f"{label}: declares {name}={declared}, but contains {actual[name]}"
            )
