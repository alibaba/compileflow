#!/usr/bin/env python3
"""Validate pinned release baselines and optionally compare official upstream facts."""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BASELINES = ROOT / "release-baselines.json"
IMAGE_PATTERN = re.compile(
    r"[a-z0-9][a-z0-9._/-]*:[A-Za-z0-9._+-]+@sha256:[0-9a-f]{64}"
)
TEMURIN_IMAGE_PATTERN = re.compile(
    r"eclipse-temurin:(?P<runtime>\d+\.\d+\.\d+(?:\.\d+)?_\d+)"
    r"-jdk-noble@sha256:[0-9a-f]{64}"
)
POSTGRES_MATRIX_FILES = (
    ".github/workflows/durable-ci.yml",
    ".github/workflows/durable-visibility-feasibility.yml",
    ".github/workflows/workbench-server-ci.yml",
)
POSTGRES_18_FILES = (
    ".github/workflows/performance.yml",
    ".github/workflows/workbench-ci.yml",
    "compileflow-workbench/docker-compose.yml",
    "compileflow-workbench/docker-compose.all-in-one.yml",
    "examples/spring-boot-durable-postgres/src/test/java/com/alibaba/compileflow/examples/durable/DurableSampleApplicationTest.java",
)
JAVA_DOCKERFILES = (
    "compileflow-workbench/docker/Dockerfile.workbench-server",
    "compileflow-workbench/docker/Dockerfile.all-in-one",
)


class BaselineError(ValueError):
    """Raised when release facts are incomplete, stale, or inconsistent."""


def strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise BaselineError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_baselines() -> dict[str, Any]:
    try:
        document = json.loads(BASELINES.read_text(encoding="utf-8"), object_pairs_hook=strict_object)
    except (OSError, json.JSONDecodeError) as error:
        raise BaselineError(f"cannot read {BASELINES}: {error}") from error
    if not isinstance(document, dict) or document.get("schemaVersion") != 3:
        raise BaselineError("release baseline schemaVersion must be 3")
    return document


def require_text(document: dict[str, Any], key: str) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value.strip() or value != value.strip():
        raise BaselineError(f"{key} must be non-blank trimmed text")
    return value


def temurin_runtime(image: str) -> str:
    match = TEMURIN_IMAGE_PATTERN.fullmatch(image)
    if match is None:
        raise BaselineError("workbenchJava.image must be an immutable Eclipse Temurin JDK Noble image")
    return match.group("runtime").replace("_", "+")


def read_repository_file(relative: str) -> str:
    try:
        return (ROOT / relative).read_text(encoding="utf-8")
    except OSError as error:
        raise BaselineError(f"cannot read required baseline consumer {relative}: {error}") from error


def validate_offline(document: dict[str, Any]) -> list[str]:
    postgres = document.get("postgres")
    if not isinstance(postgres, dict) or set(postgres) != {"recommendedMajor", "images"}:
        raise BaselineError("postgres must contain exactly recommendedMajor and images")
    if postgres["recommendedMajor"] != "18":
        raise BaselineError("PostgreSQL 18 must remain the new-install recommendation")
    images = postgres.get("images")
    if not isinstance(images, dict) or set(images) != {"16", "17", "18"}:
        raise BaselineError("postgres.images must contain exactly majors 16, 17, and 18")

    versions: dict[str, str] = {}
    for major, image in images.items():
        if not isinstance(image, str) or not IMAGE_PATTERN.fullmatch(image):
            raise BaselineError(f"PostgreSQL {major} image must be an immutable digest")
        match = re.fullmatch(rf"postgres:({major}\.\d+)-alpine\d+\.\d+@sha256:[0-9a-f]{{64}}", image)
        if match is None:
            raise BaselineError(f"PostgreSQL {major} image tag does not match its major")
        versions[major] = match.group(1)

    for relative in POSTGRES_MATRIX_FILES:
        content = read_repository_file(relative)
        for major, image in images.items():
            if image not in content or f"postgres: '{versions[major]}'" not in content:
                raise BaselineError(f"{relative} does not consume the PostgreSQL {major} baseline exactly")
    for relative in POSTGRES_18_FILES:
        if images["18"] not in read_repository_file(relative):
            raise BaselineError(f"{relative} does not consume the recommended PostgreSQL image exactly")

    java = document.get("workbenchJava")
    expected_java_fields = {"upstreamChannel", "image"}
    if not isinstance(java, dict) or set(java) != expected_java_fields:
        raise BaselineError("workbenchJava fields do not match the release-baseline schema")
    upstream_channel = require_text(java, "upstreamChannel")
    channel_match = re.fullmatch(r"(\d+)-jdk-noble", upstream_channel)
    if channel_match is None:
        raise BaselineError("workbenchJava.upstreamChannel must be a Temurin JDK Noble major channel")
    image = require_text(java, "image")
    container_runtime = temurin_runtime(image)
    container_major = container_runtime.split(".", 1)[0]
    if container_major != channel_match.group(1):
        raise BaselineError("Workbench Java image major must equal its upstream channel")
    compiler_release_match = re.search(
        r"<maven\.compiler\.release>(\d+)</maven\.compiler\.release>",
        read_repository_file("pom.xml"),
    )
    if compiler_release_match is None or container_major != compiler_release_match.group(1):
        raise BaselineError("Workbench Java major must equal the Maven compiler release baseline")
    for relative in JAVA_DOCKERFILES:
        if image not in read_repository_file(relative):
            raise BaselineError(f"{relative} does not consume the Workbench Java baseline exactly")
    notes = [
        "PostgreSQL " + "/".join(versions[major] for major in ("16", "17", "18")),
        f"Workbench Java container={container_runtime}, upstream={upstream_channel}",
    ]
    return notes


def request_json(url: str) -> Any:
    request = urllib.request.Request(url, headers={"User-Agent": "CompileFlow-release-baseline-check/1"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response, object_pairs_hook=strict_object)
    except (OSError, json.JSONDecodeError) as error:
        raise BaselineError(f"cannot query official release source {url}: {error}") from error


def docker_tag(repository: str, tag: str) -> dict[str, Any] | None:
    query = urllib.parse.urlencode({"page_size": 100, "name": tag})
    document = request_json(f"https://hub.docker.com/v2/repositories/library/{repository}/tags/?{query}")
    if not isinstance(document, dict) or not isinstance(document.get("results"), list):
        raise BaselineError(f"Docker Hub returned an invalid tag response for {repository}:{tag}")
    for result in document["results"]:
        if isinstance(result, dict) and result.get("name") == tag:
            return result
    return None


def validate_online(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    postgres_versions = request_json("https://www.postgresql.org/versions.json")
    latest = {
        item["major"]: f"{item['major']}.{item['latestMinor']}"
        for item in postgres_versions
        if isinstance(item, dict) and item.get("major") in {"16", "17", "18"}
    }
    images = document["postgres"]["images"]
    for major in ("16", "17", "18"):
        expected_version = latest.get(major)
        if expected_version is None or not images[major].startswith(f"postgres:{expected_version}-"):
            errors.append(f"PostgreSQL {major} baseline is not current upstream minor {expected_version}")
            continue
        tag, expected_digest = images[major].split("@sha256:", 1)
        result = docker_tag("postgres", tag.removeprefix("postgres:"))
        if result is None or result.get("digest") != f"sha256:{expected_digest}":
            errors.append(f"PostgreSQL image manifest changed or is unavailable: {tag}")

    java = document["workbenchJava"]
    # Temurin's standalone binaries and Docker Official Images are separate
    # publication channels. Validate the artifact we actually ship against its
    # own upstream channel instead of deriving a not-yet-published image tag
    # from the Adoptium binary catalog.
    pinned_tag, expected_digest = java["image"].removeprefix("eclipse-temurin:").split(
        "@sha256:", 1
    )
    pinned_image = docker_tag("eclipse-temurin", pinned_tag)
    if pinned_image is None or pinned_image.get("digest") != f"sha256:{expected_digest}":
        errors.append(f"Eclipse Temurin image manifest changed or is unavailable: {pinned_tag}")
    channel_image = docker_tag("eclipse-temurin", java["upstreamChannel"])
    if channel_image is None:
        errors.append(f"Eclipse Temurin upstream channel is unavailable: {java['upstreamChannel']}")
    elif channel_image.get("digest") != f"sha256:{expected_digest}":
        errors.append(
            "Workbench Java image is not current on the official Eclipse Temurin "
            f"{java['upstreamChannel']} channel"
        )
    if errors:
        raise BaselineError("; ".join(errors))
    return ["Official PostgreSQL and Eclipse Temurin container channels are current"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--online", action="store_true", help="query official upstream release sources")
    return parser.parse_args()


def main() -> int:
    arguments = parse_args()
    try:
        document = load_baselines()
        notes = validate_offline(document)
        if arguments.online:
            notes.extend(validate_online(document))
    except BaselineError as error:
        print(f"Release baseline check failed: {error}", file=sys.stderr)
        return 1
    print("Release baseline check passed: " + "; ".join(notes))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
