import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import check_release_baselines as baselines


DIGESTS = {"16": "a" * 64, "17": "b" * 64, "18": "c" * 64}
VERSIONS = {"16": "16.15", "17": "17.11", "18": "18.6"}
MYSQL_DIGEST = "f" * 64
MYSQL_VERSION = "8.4.7"


class CheckReleaseBaselinesTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.previous_root = baselines.ROOT
        self.previous_baselines = baselines.BASELINES
        baselines.ROOT = self.root
        baselines.BASELINES = self.root / "release-baselines.json"

    def tearDown(self) -> None:
        baselines.ROOT = self.previous_root
        baselines.BASELINES = self.previous_baselines
        self.temporary.cleanup()

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def fixture(self) -> dict[str, object]:
        self.write("pom.xml", "<maven.compiler.release>17</maven.compiler.release>\n")
        images = {
            major: f"postgres:{version}-alpine3.24@sha256:{DIGESTS[major]}"
            for major, version in VERSIONS.items()
        }
        matrix = "\n".join(
            f"postgres: '{VERSIONS[major]}'\npostgres_image: {image}"
            for major, image in images.items()
        )
        for relative in baselines.POSTGRES_MATRIX_FILES:
            self.write(relative, matrix)
        for relative in baselines.POSTGRES_18_FILES:
            self.write(relative, images["18"])
        mysql_image = f"mysql:{MYSQL_VERSION}@sha256:{MYSQL_DIGEST}"
        for relative in baselines.MYSQL_FILES:
            path = self.root / relative
            existing = path.read_text(encoding="utf-8") if path.exists() else ""
            self.write(relative, existing + "\n" + mysql_image)
        runtime = "17.0.20+8"
        tag = runtime.replace("+", "_") + "-jdk-noble"
        java_image = f"eclipse-temurin:{tag}@sha256:{'d' * 64}"
        for relative in baselines.JAVA_DOCKERFILES:
            self.write(relative, f"FROM {java_image}\n")
        node_version = "24.20.0"
        node_image = f"node:{node_version}-alpine3.24@sha256:{'e' * 64}"
        self.write("compileflow-workbench/.node-version", node_version + "\n")
        self.write("compileflow-workbench/pnpm-workspace.yaml", f"nodeVersion: {node_version}\n")
        for relative in baselines.NODE_DOCKERFILES:
            path = self.root / relative
            existing = path.read_text(encoding="utf-8") if path.exists() else ""
            self.write(relative, existing + f"FROM {node_image}\n")
        document: dict[str, object] = {
            "schemaVersion": 5,
            "refreshedAt": "2026-08-14",
            "postgres": {"recommendedMajor": "18", "images": images},
            "mysql": {"version": MYSQL_VERSION, "image": mysql_image},
            "workbenchJava": {
                "upstreamChannel": "17-jdk-noble",
                "image": java_image,
            },
            "workbenchNode": {"version": node_version, "image": node_image},
        }
        self.write("release-baselines.json", json.dumps(document))
        return document

    def test_offline_contract_accepts_an_immutable_channel_image(self) -> None:
        document = self.fixture()

        notes = baselines.validate_offline(document)

        self.assertIn("container=17.0.20+8, upstream=17-jdk-noble", "; ".join(notes))
        self.assertIn("Workbench Node=24.20.0", "; ".join(notes))

    def test_duplicate_manifest_keys_fail_closed(self) -> None:
        self.write("release-baselines.json", '{"schemaVersion": 5, "schemaVersion": 5}')

        with self.assertRaisesRegex(baselines.BaselineError, "duplicate JSON key"):
            baselines.load_baselines()

    def test_workbench_major_must_match_the_compiler_release_baseline(self) -> None:
        document = self.fixture()
        self.write("pom.xml", "<maven.compiler.release>21</maven.compiler.release>\n")

        with self.assertRaisesRegex(baselines.BaselineError, "Maven compiler release"):
            baselines.validate_offline(document)

    def test_workbench_image_major_must_match_the_upstream_channel(self) -> None:
        document = self.fixture()
        document["workbenchJava"]["upstreamChannel"] = "21-jdk-noble"

        with self.assertRaisesRegex(baselines.BaselineError, "upstream channel"):
            baselines.validate_offline(document)

    def test_legacy_schema_is_rejected(self) -> None:
        self.write("release-baselines.json", '{"schemaVersion": 1}')

        with self.assertRaisesRegex(baselines.BaselineError, "schemaVersion must be 5"):
            baselines.load_baselines()

    def test_node_consumers_must_match_the_single_baseline(self) -> None:
        document = self.fixture()
        self.write("compileflow-workbench/.node-version", "24.19.0\n")

        with self.assertRaisesRegex(baselines.BaselineError, "does not consume the Node baseline"):
            baselines.validate_offline(document)

    def test_online_contract_rejects_a_stale_temurin_channel(self) -> None:
        document = self.fixture()
        upstream_versions = [
            {"major": major, "latestMinor": version.split(".", 1)[1]}
            for major, version in VERSIONS.items()
        ]

        def docker_result(repository: str, tag: str) -> dict[str, str]:
            if repository == "postgres":
                major = tag.split(".", 1)[0]
                return {"name": tag, "digest": f"sha256:{DIGESTS[major]}"}
            if repository == "mysql":
                return {"name": tag, "digest": f"sha256:{MYSQL_DIGEST}"}
            if repository == "node":
                return {"name": tag, "digest": f"sha256:{'e' * 64}"}
            if tag == "17.0.20_8-jdk-noble":
                return {"name": tag, "digest": f"sha256:{'d' * 64}"}
            return {"name": tag, "digest": f"sha256:{'e' * 64}"}

        def upstream(url: str) -> object:
            if url == "https://www.postgresql.org/versions.json":
                return upstream_versions
            return [{"version": "v24.20.0", "lts": "Krypton"}]

        with (
            patch.object(baselines, "request_json", side_effect=upstream),
            patch.object(baselines, "docker_tag", side_effect=docker_result),
        ):
            with self.assertRaisesRegex(baselines.BaselineError, "not current"):
                baselines.validate_online(document)

    def test_online_contract_accepts_the_current_official_temurin_channel(self) -> None:
        document = self.fixture()
        upstream_versions = [
            {"major": major, "latestMinor": version.split(".", 1)[1]}
            for major, version in VERSIONS.items()
        ]

        def docker_result(repository: str, tag: str) -> dict[str, str]:
            if repository == "postgres":
                major = tag.split(".", 1)[0]
                return {"name": tag, "digest": f"sha256:{DIGESTS[major]}"}
            if repository == "mysql":
                return {"name": tag, "digest": f"sha256:{MYSQL_DIGEST}"}
            if repository == "node":
                return {"name": tag, "digest": f"sha256:{'e' * 64}"}
            return {"name": tag, "digest": f"sha256:{'d' * 64}"}

        def upstream(url: str) -> object:
            if url == "https://www.postgresql.org/versions.json":
                return upstream_versions
            return [{"version": "v24.20.0", "lts": "Krypton"}]

        with (
            patch.object(baselines, "request_json", side_effect=upstream) as request,
            patch.object(baselines, "docker_tag", side_effect=docker_result),
        ):
            notes = baselines.validate_online(document)

        self.assertIn("Node release channels are current", "; ".join(notes))
        self.assertEqual(2, request.call_count)
        self.assertEqual(
            [
                "https://www.postgresql.org/versions.json",
                "https://nodejs.org/dist/index.json",
            ],
            [call.args[0] for call in request.call_args_list],
        )

    def test_online_contract_rejects_a_changed_pinned_temurin_manifest(self) -> None:
        document = self.fixture()
        upstream_versions = [
            {"major": major, "latestMinor": version.split(".", 1)[1]}
            for major, version in VERSIONS.items()
        ]

        def docker_result(repository: str, tag: str) -> dict[str, str]:
            if repository == "postgres":
                major = tag.split(".", 1)[0]
                return {"name": tag, "digest": f"sha256:{DIGESTS[major]}"}
            if repository == "mysql":
                return {"name": tag, "digest": f"sha256:{MYSQL_DIGEST}"}
            if repository == "node":
                return {"name": tag, "digest": f"sha256:{'e' * 64}"}
            if tag == "17.0.20_8-jdk-noble":
                return {"name": tag, "digest": f"sha256:{'e' * 64}"}
            return {"name": tag, "digest": f"sha256:{'d' * 64}"}

        def upstream(url: str) -> object:
            if url == "https://www.postgresql.org/versions.json":
                return upstream_versions
            return [{"version": "v24.20.0", "lts": "Krypton"}]

        with (
            patch.object(baselines, "request_json", side_effect=upstream),
            patch.object(baselines, "docker_tag", side_effect=docker_result),
        ):
            with self.assertRaisesRegex(baselines.BaselineError, "manifest changed"):
                baselines.validate_online(document)

    def test_online_contract_rejects_a_stale_node_release(self) -> None:
        document = self.fixture()
        upstream_versions = [
            {"major": major, "latestMinor": version.split(".", 1)[1]}
            for major, version in VERSIONS.items()
        ]

        def upstream(url: str) -> object:
            if url == "https://www.postgresql.org/versions.json":
                return upstream_versions
            return [{"version": "v24.21.0", "lts": "Krypton"}]

        def docker_result(repository: str, tag: str) -> dict[str, str]:
            if repository == "postgres":
                major = tag.split(".", 1)[0]
                return {"name": tag, "digest": f"sha256:{DIGESTS[major]}"}
            if repository == "mysql":
                return {"name": tag, "digest": f"sha256:{MYSQL_DIGEST}"}
            if repository == "node":
                return {"name": tag, "digest": f"sha256:{'e' * 64}"}
            return {"name": tag, "digest": f"sha256:{'d' * 64}"}

        with (
            patch.object(baselines, "request_json", side_effect=upstream),
            patch.object(baselines, "docker_tag", side_effect=docker_result),
        ):
            with self.assertRaisesRegex(baselines.BaselineError, "Node 24 LTS 24.21.0"):
                baselines.validate_online(document)

    def test_online_contract_rejects_a_changed_pinned_node_manifest(self) -> None:
        document = self.fixture()
        upstream_versions = [
            {"major": major, "latestMinor": version.split(".", 1)[1]}
            for major, version in VERSIONS.items()
        ]

        def upstream(url: str) -> object:
            if url == "https://www.postgresql.org/versions.json":
                return upstream_versions
            return [{"version": "v24.20.0", "lts": "Krypton"}]

        def docker_result(repository: str, tag: str) -> dict[str, str]:
            if repository == "postgres":
                major = tag.split(".", 1)[0]
                return {"name": tag, "digest": f"sha256:{DIGESTS[major]}"}
            if repository == "mysql":
                return {"name": tag, "digest": f"sha256:{MYSQL_DIGEST}"}
            if repository == "node":
                return {"name": tag, "digest": f"sha256:{'f' * 64}"}
            return {"name": tag, "digest": f"sha256:{'d' * 64}"}

        with (
            patch.object(baselines, "request_json", side_effect=upstream),
            patch.object(baselines, "docker_tag", side_effect=docker_result),
        ):
            with self.assertRaisesRegex(baselines.BaselineError, "Node image manifest changed"):
                baselines.validate_online(document)


if __name__ == "__main__":
    unittest.main()
