"""Fixture regressions for executable Workbench JAR SBOM completeness."""

from __future__ import annotations

import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

from scripts.verify_maven_sbom import SbomVerificationError
from scripts.verify_workbench_sbom import main, verify_bom


VERSION = "2.0.0"
BOOT = "4.1.1"
LOADER = "org/springframework/boot/loader/"
CLASS = b"\xca\xfe\xba\xbe\x00\x00\x00\x3d"


def archive(entries: list[tuple[str, bytes]]) -> bytes:
    buffer = io.BytesIO()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(buffer, "w") as output:
            for name, content in entries:
                output.writestr(zipfile.ZipInfo(name), content)
    return buffer.getvalue()


def hashes(content: bytes) -> list[dict[str, str]]:
    return [{"alg": "SHA-256", "content": hashlib.sha256(content).hexdigest()}]


class VerifyWorkbenchSbomTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="workbench-sbom-")
        self.addCleanup(self.directory.cleanup)
        self.jar = Path(self.directory.name) / "server.jar"
        self.loader_jar = Path(self.directory.name) / "loader.jar"
        self.loader_entries = [
            (LOADER + "launch/JarLauncher.class", CLASS + b"launcher"),
            (LOADER + "launch/Launcher.class", CLASS + b"base"),
        ]
        self.loader_jar.write_bytes(archive(self.loader_entries))
        self.libraries = {
            "BOOT-INF/lib/not-an-artifact-name.jar": archive([("one/A.class", CLASS + b"a")]),
            "BOOT-INF/lib/other.jar": archive([("two/B.class", CLASS + b"b")]),
            "BOOT-INF/lib/packager-added.jar": archive([("tools/C.class", CLASS + b"c")]),
        }
        self.manifest = (
            "Manifest-Version: 1.0\r\n"
            f"Implementation-Version: {VERSION}\r\n"
            f"Spring-Boot-Version: {BOOT}\r\n"
            "Main-Class: org.springframework.boot.loader.launch.JarLauncher\r\n"
            "Start-Class: example.Workbench\r\n"
            "Spring-Boot-Classes: BOOT-INF/classes/\r\n"
            "Spring-Boot-Lib: BOOT-INF/lib/\r\n\r\n"
        ).encode()
        root = {
            "type": "application", "group": "com.alibaba.compileflow",
            "name": "compileflow-workbench-server", "version": VERSION, "bom-ref": "app",
        }
        components = [
            {"type": "library", "group": "unrelated.coordinates", "name": f"component-{i}",
             "version": "9.0", "bom-ref": f"lib-{i}", "hashes": hashes(content)}
            for i, content in enumerate(self.libraries.values())
        ]
        components.append({
            "type": "library", "group": "org.springframework.boot", "name": "spring-boot-loader",
            "version": BOOT, "bom-ref": "loader", "hashes": hashes(self.loader_jar.read_bytes()),
        })
        self.bom = {
            "bomFormat": "CycloneDX", "specVersion": "1.6",
            "metadata": {"component": root}, "components": components,
            "dependencies": [
                {"ref": "app", "dependsOn": ["lib-0", "loader"]},
                {"ref": "lib-0", "dependsOn": ["lib-1"]},
                {"ref": "lib-1", "dependsOn": ["lib-2"]},
                {"ref": "lib-2", "dependsOn": []},
                {"ref": "loader", "dependsOn": []},
            ],
        }
        self.write_jar()

    def write_jar(self, extra: list[tuple[str, bytes]] | None = None) -> None:
        self.jar.write_bytes(archive([
            ("META-INF/MANIFEST.MF", self.manifest),
            ("BOOT-INF/classes/example/Workbench.class", CLASS + b"app"),
            *self.loader_entries, *self.libraries.items(), *(extra or []),
        ]))

    def replace_library(self, content: bytes) -> None:
        self.libraries[next(iter(self.libraries))] = content
        self.bom["components"][0]["hashes"] = hashes(content)
        self.write_jar()

    def reject(self, message: str) -> None:
        with self.assertRaisesRegex(SbomVerificationError, message):
            verify_bom(self.bom, self.jar, VERSION, self.loader_jar)

    def test_accepts_hash_identity_without_filename_coordinate_guessing(self) -> None:
        result = verify_bom(self.bom, self.jar, VERSION, self.loader_jar)
        self.assertEqual(3, result["libraries"])
        self.assertEqual(2, result["loaderClasses"])
        self.assertEqual("class-hashes", result["loaderVerification"])

    def test_compares_loader_resources_but_allows_other_outer_metadata(self) -> None:
        self.loader_entries.append((LOADER + "settings.properties", b"key=value"))
        self.loader_jar.write_bytes(archive([*self.loader_entries, ("META-INF/LICENSE.txt", b"loader license")]))
        self.bom["components"][-1]["hashes"] = hashes(self.loader_jar.read_bytes())
        self.write_jar([("META-INF/LICENSE.txt", b"application license")])
        result = verify_bom(self.bom, self.jar, VERSION, self.loader_jar)
        self.assertEqual(3, result["loaderFiles"])
        self.loader_entries[-1] = (LOADER + "settings.properties", b"tampered")
        self.write_jar()
        self.reject("loader class/resource SHA-256")

    def test_rejects_duplicate_reference_loader_entries(self) -> None:
        self.loader_jar.write_bytes(archive([*self.loader_entries, self.loader_entries[0]]))
        self.bom["components"][-1]["hashes"] = hashes(self.loader_jar.read_bytes())
        self.reject("duplicate ZIP")

    def test_rejects_each_wrong_root_field(self) -> None:
        for field in ("type", "group", "name", "version"):
            with self.subTest(field=field):
                saved = self.bom["metadata"]["component"][field]
                self.bom["metadata"]["component"][field] = "wrong"
                self.reject("root")
                self.bom["metadata"]["component"][field] = saved

    def test_rejects_third_library_missing_hash(self) -> None:
        del self.bom["components"][2]["hashes"]
        self.reject("SHA-256")

    def test_rejects_wrong_hash_even_when_name_matches_jar(self) -> None:
        component = self.bom["components"][0]
        component["name"] = "not-an-artifact-name"
        component["hashes"] = hashes(b"not the packaged bytes")
        self.reject("SHA-256")

    def test_rejects_sha1_only(self) -> None:
        self.bom["components"][0]["hashes"] = [{"alg": "SHA-1", "content": "0" * 40}]
        self.reject("SHA-256")

    def test_rejects_malformed_or_conflicting_hashes(self) -> None:
        original = self.bom["components"][0]["hashes"]
        for invalid in (None, [{"alg": "SHA-256", "content": "no"}], original * 2):
            with self.subTest(hashes=invalid):
                self.bom["components"][0]["hashes"] = invalid
                self.reject("hash")

    def test_accepts_uppercase_hex_digest(self) -> None:
        self.bom["components"][0]["hashes"][0]["content"] = self.bom["components"][0]["hashes"][0]["content"].upper()
        verify_bom(self.bom, self.jar, VERSION, self.loader_jar)

    def test_rejects_ambiguous_hash_match(self) -> None:
        duplicate = copy.deepcopy(self.bom["components"][0])
        duplicate["bom-ref"] = "another-coordinate"
        self.bom["components"].append(duplicate)
        self.reject("ambiguous")

    def test_rejects_duplicate_refs_including_root_and_nested_components(self) -> None:
        for duplicate in (self.bom["components"][0], self.bom["metadata"]["component"]):
            with self.subTest(reference=duplicate["bom-ref"]):
                self.bom["components"][-1]["components"] = [copy.deepcopy(duplicate)]
                self.reject("duplicate.*ref")

    def test_accepts_unique_nested_components(self) -> None:
        nested = self.bom["components"].pop(1)
        self.bom["components"][0]["components"] = [nested]
        verify_bom(self.bom, self.jar, VERSION, self.loader_jar)

    def test_rejects_dangling_dependency_source_or_target(self) -> None:
        for edge in ({"ref": "ghost", "dependsOn": []}, {"ref": "app", "dependsOn": ["ghost"]}):
            with self.subTest(edge=edge):
                self.bom["dependencies"][0] = edge
                self.reject("dangling")

    def test_rejects_duplicate_dependency_rows_and_targets(self) -> None:
        self.bom["dependencies"].append(copy.deepcopy(self.bom["dependencies"][0]))
        self.reject("duplicate dependency")
        self.bom["dependencies"].pop()
        self.bom["dependencies"][0]["dependsOn"].append("loader")
        self.reject("duplicate.*target")

    def test_rejects_disconnected_packaged_component_cycle(self) -> None:
        self.bom["dependencies"][0]["dependsOn"] = ["loader"]
        self.bom["dependencies"][2]["dependsOn"].append("lib-0")
        self.reject("reachable")

    def test_rejects_missing_or_disconnected_loader_component(self) -> None:
        self.bom["components"][-1]["name"] = "other"
        self.reject("loader component")
        self.bom["components"][-1]["name"] = "spring-boot-loader"
        self.bom["dependencies"][0]["dependsOn"] = ["lib-0"]
        self.reject("loader.*reachable")

    def test_rejects_loader_manifest_version_mismatch(self) -> None:
        self.bom["components"][-1]["version"] = "0.0.0"
        self.reject("loader.*version")

    def test_rejects_application_manifest_version_mismatch(self) -> None:
        self.manifest = self.manifest.replace(b"Implementation-Version: 2.0.0", b"Implementation-Version: 1.0.0")
        self.write_jar()
        self.reject("Implementation-Version")

    def test_accepts_folded_manifest_value(self) -> None:
        self.manifest = self.manifest.replace(b"launch.JarLauncher", b"launch.Jar\r\n Launcher")
        self.write_jar()
        verify_bom(self.bom, self.jar, VERSION, self.loader_jar)

    def test_rejects_duplicate_manifest_attribute(self) -> None:
        self.manifest = self.manifest.replace(b"\r\n\r\n", b"\r\nspring-boot-version: 0.0.0\r\n\r\n")
        self.write_jar()
        self.reject("duplicate manifest")

    def test_rejects_missing_or_fake_flattened_loader(self) -> None:
        self.loader_entries = []
        self.write_jar()
        self.reject("loader")
        self.loader_entries = [(LOADER + "launch/JarLauncher.class", b"not a class")]
        self.write_jar()
        self.reject("loader class")

    def test_rejects_reference_loader_byte_or_class_set_mismatch(self) -> None:
        self.loader_entries[0] = (self.loader_entries[0][0], CLASS + b"tampered")
        self.write_jar()
        self.reject("loader class.*SHA-256")
        self.loader_entries.pop()
        self.write_jar()
        self.reject("loader class.*set")

    def test_rejects_reference_loader_not_matching_component_hash(self) -> None:
        self.bom["components"][-1]["hashes"] = hashes(b"different loader jar")
        self.reject("loader JAR.*SHA-256")

    def test_rejects_duplicate_outer_zip_members(self) -> None:
        self.write_jar([("META-INF/MANIFEST.MF", self.manifest)])
        self.reject("duplicate ZIP")

    def test_rejects_duplicate_inner_zip_members_even_with_exact_bom_hash(self) -> None:
        self.replace_library(archive([("A.class", CLASS), ("A.class", CLASS + b"changed")]))
        self.reject("duplicate ZIP")

    def test_rejects_nested_jar_and_disguised_zip_even_with_exact_bom_hash(self) -> None:
        for name in ("hidden.jar", "hidden.zip", "resource.bin"):
            with self.subTest(name=name):
                self.replace_library(archive([(name, archive([("Hidden.class", CLASS)]))]))
                self.reject("nested archive")

    def test_rejects_hidden_archives_outside_direct_boot_libraries(self) -> None:
        for name in ("BOOT-INF/classes/hidden.jar", "BOOT-INF/lib/sub/hidden.jar", "assets/hidden.bin"):
            with self.subTest(name=name):
                self.write_jar([(name, archive([("Hidden.class", CLASS)]))])
                self.reject("archive.*BOOT-INF/lib|direct.*BOOT-INF/lib")

    def test_rejects_concatenated_zip(self) -> None:
        first = archive([("Hidden.class", CLASS)])
        self.replace_library(first + archive([("Visible.class", CLASS)]))
        self.reject("ZIP.*prefix|concatenated")

    def test_rejects_ambiguous_zip_paths(self) -> None:
        for name in ("BOOT-INF/lib/../hidden.jar", "BOOT-INF/lib//hidden.jar", "BOOT-INF\\lib\\hidden.jar"):
            with self.subTest(name=name):
                self.write_jar([(name, b"irrelevant")])
                self.reject("ZIP path")

    def test_cli_reuses_duplicate_json_rejection(self) -> None:
        bom_path = Path(self.directory.name) / "bom.json"
        bom_path.write_text('{"bomFormat":"CycloneDX","bomFormat":"other"}')
        self.assertEqual(1, main([str(bom_path), str(self.jar), "--loader-jar", str(self.loader_jar), "--expected-version", VERSION]))

    def test_cli_accepts_valid_fixture_and_reports_bad_zip(self) -> None:
        bom_path = Path(self.directory.name) / "bom.json"
        bom_path.write_text(json.dumps(self.bom))
        args = [str(bom_path), str(self.jar), "--loader-jar", str(self.loader_jar), "--expected-version", VERSION]
        self.assertEqual(0, main(args))
        self.jar.write_bytes(b"not a jar")
        self.assertEqual(1, main(args))


if __name__ == "__main__":
    unittest.main()
