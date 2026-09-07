#!/usr/bin/env python3
"""Verify an application CycloneDX BOM against the packaged Workbench Boot JAR."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import stat
import sys
from typing import Any
import zipfile
import zlib

if __package__:
    from .verify_maven_sbom import SbomVerificationError, load_bom, require
else:
    from verify_maven_sbom import SbomVerificationError, load_bom, require


LOADER_PREFIX = "org/springframework/boot/loader/"
LIB_PREFIX = "BOOT-INF/lib/"
MAX_JAR_BYTES = 1024 * 1024 * 1024
MAX_MEMBER_BYTES = 128 * 1024 * 1024
MAX_UNPACKED_BYTES = 2 * 1024 * 1024 * 1024
MAX_ENTRIES = 200_000
ZIP_SIGNATURES = (b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")


def _file_hash(path: Path) -> str:
    require(not path.is_symlink() and path.is_file(), f"JAR must be a regular non-symlink file: {path}")
    require(0 < path.stat().st_size <= MAX_JAR_BYTES, f"JAR size outside allowed range: {path}")
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _sha256(component: dict[str, Any]) -> str | None:
    entries = component.get("hashes", [])
    require(isinstance(entries, list), "component hashes must be an array")
    algorithms: set[str] = set()
    digest = None
    for entry in entries:
        require(isinstance(entry, dict), "component hash must be an object")
        algorithm, value = entry.get("alg"), entry.get("content")
        require(isinstance(algorithm, str) and algorithm and isinstance(value, str), "invalid component hash")
        require(algorithm not in algorithms, f"duplicate component hash algorithm: {algorithm}")
        algorithms.add(algorithm)
        if algorithm == "SHA-256":
            require(re.fullmatch(r"[0-9a-fA-F]{64}", value) is not None, "invalid SHA-256 hash")
            digest = value.lower()
    return digest


def _graph(data: dict[str, Any], version: str) -> tuple[str, dict[str, dict[str, Any]], set[str]]:
    require(data.get("bomFormat") == "CycloneDX" and data.get("specVersion") == "1.6", "BOM must be CycloneDX 1.6")
    metadata = data.get("metadata")
    require(isinstance(metadata, dict), "BOM root metadata must be an object")
    root = metadata.get("component")
    require(isinstance(root, dict), "BOM root component must be an object")
    for field, expected in (("type", "application"), ("group", "com.alibaba.compileflow"),
                            ("name", "compileflow-workbench-server"), ("version", version)):
        require(root.get(field) == expected, f"BOM root {field} must be {expected!r}")
    root_ref = root.get("bom-ref")
    require(isinstance(root_ref, str) and root_ref, "BOM root bom-ref must be non-empty")
    items = data.get("components")
    require(isinstance(items, list) and items, "BOM components must be a non-empty array")
    pending = list(items)
    components: dict[str, dict[str, Any]] = {}
    while pending:
        component = pending.pop()
        require(isinstance(component, dict), "component must be an object")
        reference = component.get("bom-ref")
        require(isinstance(reference, str) and reference, "component bom-ref must be non-empty")
        require(reference != root_ref and reference not in components, f"duplicate component ref: {reference}")
        components[reference] = component
        children = component.get("components", [])
        require(isinstance(children, list), "nested components must be an array")
        pending.extend(children)
    known = {root_ref, *components}
    edges = data.get("dependencies")
    require(isinstance(edges, list), "BOM dependencies must be an array")
    graph: dict[str, list[str]] = {}
    for edge in edges:
        require(isinstance(edge, dict), "dependency must be an object")
        source, targets = edge.get("ref"), edge.get("dependsOn")
        require(isinstance(source, str) and source in known, f"dangling dependency source: {source!r}")
        require(source not in graph, f"duplicate dependency ref: {source}")
        require(isinstance(targets, list), f"dependency {source} must have dependsOn array")
        require(all(isinstance(t, str) and t in known for t in targets), f"dangling dependency target from {source}")
        require(len(targets) == len(set(targets)), f"duplicate dependency target from {source}")
        graph[source] = targets
    require(root_ref in graph, "dependency graph misses application root")
    reachable: set[str] = set()
    queue = [root_ref]
    while queue:
        reference = queue.pop()
        if reference not in reachable:
            reachable.add(reference)
            queue.extend(graph.get(reference, []))
    return root_ref, components, reachable


def _members(archive: zipfile.ZipFile, label: str, budget: list[int]) -> list[zipfile.ZipInfo]:
    entries = archive.infolist()
    require(0 < len(entries) <= MAX_ENTRIES, f"ZIP entry count outside allowed range: {label}")
    names: set[str] = set()
    offsets: set[int] = set()
    for entry in entries:
        name = entry.orig_filename
        parts = name.rstrip("/").split("/")
        require(name == entry.filename and "\\" not in name and "\x00" not in name
                and all(p not in ("", ".", "..") for p in parts), f"ambiguous ZIP path in {label}: {name!r}")
        require(name not in names, f"duplicate ZIP member in {label}: {name}")
        require(entry.header_offset not in offsets, f"duplicate ZIP local header in {label}: {name}")
        names.add(name)
        offsets.add(entry.header_offset)
        require(not stat.S_ISLNK(entry.external_attr >> 16), f"ZIP symlink in {label}: {name}")
        require(not entry.flag_bits & 1, f"encrypted ZIP entry in {label}: {name}")
        require(entry.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), f"unsupported ZIP compression in {label}: {name}")
        require(0 <= entry.file_size <= MAX_MEMBER_BYTES, f"ZIP member size outside allowed range: {label}/{name}")
        require(not entry.is_dir() or entry.file_size == 0, f"non-empty ZIP directory: {label}/{name}")
        budget[0] -= entry.file_size
        budget[1] -= 1
        require(budget[0] >= 0 and budget[1] >= 0, "ZIP expansion budget exceeded")
    require(min(offsets) == 0, f"ZIP prefix or concatenated archive is not allowed: {label}")
    return entries


def _member_hash(archive: zipfile.ZipFile, entry: zipfile.ZipInfo) -> tuple[str, bytes]:
    digest = hashlib.sha256()
    with archive.open(entry) as stream:
        prefix = stream.read(4)
        digest.update(prefix)
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest(), prefix


def _is_archive(name: str, prefix: bytes) -> bool:
    return name.lower().endswith((".jar", ".zip")) or prefix in ZIP_SIGNATURES


def _library(content: bytes, label: str, budget: list[int]) -> None:
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        for entry in _members(archive, label, budget):
            if entry.is_dir():
                continue
            _, prefix = _member_hash(archive, entry)
            require(not _is_archive(entry.filename, prefix), f"nested archive in {label}: {entry.filename}")
            require(not entry.filename.startswith(LOADER_PREFIX), f"loader must be flattened, not inside {label}")


def _manifest(content: bytes) -> dict[str, str]:
    require(len(content) <= 64 * 1024, "manifest is too large")
    try:
        lines = content.decode("utf-8").splitlines()
    except UnicodeDecodeError as error:
        raise SbomVerificationError("manifest must be UTF-8") from error
    unfolded: list[str] = []
    for line in lines:
        if not line:
            break
        if line.startswith(" "):
            require(bool(unfolded), "manifest continuation without header")
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    values: dict[str, str] = {}
    for line in unfolded:
        key, separator, value = line.partition(": ")
        require(bool(separator) and bool(key), "malformed manifest header")
        key = key.lower()
        require(key not in values, f"duplicate manifest attribute: {key}")
        values[key] = value
    return values


def _application(jar: Path, budget: list[int]) -> tuple[dict[str, str], dict[str, str], dict[str, str], int]:
    libraries: dict[str, str] = {}
    loader_files: dict[str, str] = {}
    class_count = 0
    with zipfile.ZipFile(jar) as archive:
        entries = _members(archive, str(jar), budget)
        require("META-INF/MANIFEST.MF" in {e.filename for e in entries}, "application manifest is missing")
        manifest = _manifest(archive.read("META-INF/MANIFEST.MF"))
        for entry in entries:
            if entry.is_dir():
                continue
            name = entry.filename
            if name.startswith(LIB_PREFIX) and name.endswith(".jar"):
                require("/" not in name[len(LIB_PREFIX):], f"only direct BOOT-INF/lib JARs are allowed: {name}")
                content = archive.read(entry)
                _library(content, name, budget)
                libraries[name] = hashlib.sha256(content).hexdigest()
                continue
            digest, prefix = _member_hash(archive, entry)
            require(not _is_archive(name, prefix), f"archive outside direct BOOT-INF/lib: {name}")
            if name.startswith(LOADER_PREFIX):
                loader_files[name] = digest
                if name.endswith(".class"):
                    require(prefix == b"\xca\xfe\xba\xbe" and entry.file_size >= 8, f"invalid loader class: {name}")
                    class_count += 1
            elif LOADER_PREFIX in name:
                require(not name.endswith(".class"), f"loader class outside flattened namespace: {name}")
    require(bool(libraries), "application has no BOOT-INF/lib JARs")
    require(class_count > 0, "application has no flattened loader classes")
    return manifest, libraries, loader_files, class_count


def _reference_loader(jar: Path, budget: list[int]) -> dict[str, str]:
    files: dict[str, str] = {}
    with zipfile.ZipFile(jar) as archive:
        for entry in _members(archive, str(jar), budget):
            if entry.is_dir():
                continue
            digest, prefix = _member_hash(archive, entry)
            require(not _is_archive(entry.filename, prefix), f"nested archive in loader JAR: {entry.filename}")
            if entry.filename.startswith(LOADER_PREFIX):
                files[entry.filename] = digest
    return files


def verify_bom(data: dict[str, Any], jar: Path, expected_version: str, loader_jar: Path) -> dict[str, Any]:
    """Prove every packaged library and every flattened loader file by SHA-256."""
    require(isinstance(expected_version, str) and bool(expected_version), "expected version must be non-empty")
    root_ref, components, reachable = _graph(data, expected_version)
    hash_index: dict[str, list[str]] = {}
    for reference, component in components.items():
        digest = _sha256(component)
        if digest is not None and component.get("type") == "library":
            hash_index.setdefault(digest, []).append(reference)
    jar_hash, loader_hash = _file_hash(jar), _file_hash(loader_jar)
    budget = [MAX_UNPACKED_BYTES, MAX_ENTRIES]
    try:
        manifest, libraries, loader_files, class_count = _application(jar, budget)
        require(manifest.get("implementation-version") == expected_version, "application Implementation-Version does not match expected version")
        require(manifest.get("spring-boot-classes") == "BOOT-INF/classes/" and manifest.get("spring-boot-lib") == LIB_PREFIX,
                "manifest must declare the standard BOOT-INF classes and library paths")
        boot_version = manifest.get("spring-boot-version")
        require(bool(boot_version), "manifest Spring-Boot-Version is missing")
        launcher = manifest.get("main-class", "").replace(".", "/") + ".class"
        require(launcher.startswith(LOADER_PREFIX) and launcher in loader_files, "manifest Main-Class is not a flattened loader class")
        loaders = [(ref, c) for ref, c in components.items() if c.get("group") == "org.springframework.boot"
                   and c.get("name") == "spring-boot-loader" and c.get("type") == "library"]
        require(len(loaders) == 1, "BOM must identify exactly one spring-boot-loader component")
        loader_ref, loader_component = loaders[0]
        require(loader_component.get("version") == boot_version, "loader component version differs from manifest Spring-Boot-Version")
        require(loader_ref in reachable, "loader component is not reachable from application root")
        require(_sha256(loader_component) == loader_hash, "loader JAR SHA-256 does not match loader component hashes")
        reference_files = _reference_loader(loader_jar, budget)
        require(loader_files.keys() == reference_files.keys(),
                f"loader class/resource path set differs: missing={sorted(reference_files.keys() - loader_files.keys())}, extra={sorted(loader_files.keys() - reference_files.keys())}")
        for name, digest in loader_files.items():
            require(digest == reference_files[name], f"loader class/resource SHA-256 differs: {name}")
    except (zipfile.BadZipFile, zipfile.LargeZipFile, EOFError, zlib.error, NotImplementedError) as error:
        raise SbomVerificationError(f"invalid ZIP/JAR: {error}") from error
    packaged = []
    for name, digest in sorted(libraries.items()):
        references = hash_index.get(digest, [])
        require(bool(references), f"no BOM library SHA-256 match: {name}, sha256={digest}")
        require(len(references) == 1, f"ambiguous BOM SHA-256 match: {name}, refs={references}")
        require(references[0] in reachable, f"packaged component is not reachable from root: {name}, ref={references[0]}")
        packaged.append({"path": name, "sha256": digest, "bomRef": references[0]})
    return {
        "status": "passed", "version": expected_version, "rootRef": root_ref,
        "jar": str(jar), "jarSha256": jar_hash, "libraries": len(packaged),
        "loaderJar": str(loader_jar), "loaderJarSha256": loader_hash, "loaderRef": loader_ref,
        "springBootVersion": boot_version, "loaderFiles": len(loader_files), "loaderClasses": class_count,
        "loaderVerification": "class-hashes", "packagedLibraries": packaged,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bom", type=Path, help="application CycloneDX 1.6 JSON BOM")
    parser.add_argument("jar", type=Path, help="actual executable Workbench server JAR")
    parser.add_argument("--loader-jar", type=Path, required=True, help="original spring-boot-loader artifact")
    parser.add_argument("--expected-version", required=True, help="exact application release version")
    args = parser.parse_args(argv)
    try:
        result = verify_bom(load_bom(args.bom), args.jar, args.expected_version, args.loader_jar)
    except (OSError, SbomVerificationError, RecursionError) as error:
        print(f"Workbench SBOM verification failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
