import io
import tarfile
import tempfile
import unittest
from pathlib import Path

from scripts import verify_source_archive as verifier


class VerifySourceArchiveTest(unittest.TestCase):

    VERSION = "2.0.0"

    def build_archive(self, extra_file: str | None = None) -> Path:
        temporary = tempfile.NamedTemporaryFile(suffix=".tar.gz", delete=False)
        temporary.close()
        archive_path = Path(temporary.name)
        root = f"compileflow-{self.VERSION}"

        with tarfile.open(archive_path, mode="w:gz") as archive:
            files = sorted(verifier.REQUIRED_FILES | ({extra_file} if extra_file else set()))
            for relative in files:
                content = f"fixture:{relative}\n".encode()
                member = tarfile.TarInfo(f"{root}/{relative}")
                member.size = len(content)
                member.mode = 0o755 if relative == "mvnw" else 0o644
                archive.addfile(member, io.BytesIO(content))
        self.addCleanup(archive_path.unlink, missing_ok=True)
        return archive_path

    def test_accepts_minimal_clean_source_archive(self) -> None:
        archive = self.build_archive()

        self.assertEqual(verifier.validate_archive(archive, self.VERSION), len(verifier.REQUIRED_FILES))

    def test_rejects_editor_and_incremental_build_residue(self) -> None:
        for forbidden in ("compileflow.iml", "web.tsbuildinfo", "cache.pyc"):
            with self.subTest(forbidden=forbidden):
                archive = self.build_archive(forbidden)

                with self.assertRaisesRegex(ValueError, "generated or private file"):
                    verifier.validate_archive(archive, self.VERSION)


if __name__ == "__main__":
    unittest.main()
