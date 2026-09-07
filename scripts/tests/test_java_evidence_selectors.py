"""Bind required Java evidence identities to annotated repository test methods."""

import os
import re
import tempfile
import unittest
from pathlib import Path

from scripts.check_internal_links import SKIP_DIRS
from scripts.verify_durable_release_evidence import REQUIRED_TEST_CASES
from scripts.verify_workbench_postgres_evidence import REQUIRED_METHODS


JAVA_NON_CODE = re.compile(r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*', re.DOTALL)
ANNOTATED_METHOD = re.compile(
    r"((?:@[\w.]+(?:\([^;]*?\))?\s*)+)"
    r"(?:(?:public|protected|private|final|static)\s+)*"
    r"void\s+(\w+)\s*\([^)]*\)\s*(?:throws\s+[\w.,\s]+)?\{"
)
TEST_ANNOTATION = re.compile(r"@(?:[\w.]+\.)?(?:Test|ParameterizedTest|RepeatedTest|TestTemplate)\b")


def source_index(root: Path) -> dict[str, Path]:
    result = {}
    for directory, children, files in os.walk(root):
        children[:] = [name for name in children if name not in SKIP_DIRS]
        relative = Path(directory).relative_to(root).as_posix()
        for source_set in ("src/test/java/", "src/main/java/"):
            if source_set not in relative + "/":
                continue
            package = (relative + "/").split(source_set, 1)[1]
            for name in files:
                if name.endswith(".java"):
                    identity = (package + name[:-5]).replace("/", ".")
                    if identity in result:
                        raise AssertionError(f"Duplicate Java source identity: {identity}")
                    result[identity] = Path(directory) / name
    return result


def test_methods(identity: str, index: dict[str, Path], seen: frozenset[str] = frozenset()) -> set[str]:
    if identity in seen:
        raise AssertionError(f"Cyclic test inheritance: {identity}")
    path = index[identity]
    source = JAVA_NON_CODE.sub(" ", path.read_text(encoding="utf-8"))
    package = re.search(r"\bpackage\s+([\w.]+)\s*;", source)
    if package and identity.rsplit(".", 1)[0] != package.group(1):
        raise AssertionError(f"Package does not match source path: {path}")
    methods = {
        name for annotations, name in ANNOTATED_METHOD.findall(source)
        if TEST_ANNOTATION.search(annotations)
    }
    parent = re.search(r"\bclass\s+" + re.escape(path.stem) + r"\b[^{}]*?\bextends\s+([\w.]+)", source)
    if parent:
        base = parent.group(1)
        imports = dict((name.rsplit(".", 1)[-1], name) for name in re.findall(r"\bimport\s+([\w.]+);", source))
        qualified = imports.get(base, identity.rsplit(".", 1)[0] + "." + base) if "." not in base else base
        if qualified in index:
            methods.update(test_methods(qualified, index, seen | {identity}))
    return methods


class JavaEvidenceSelectorsTest(unittest.TestCase):
    def test_required_identities_exist_in_actual_java_sources(self) -> None:
        root = Path(__file__).resolve().parents[2]
        index = source_index(root)
        required = set().union(*REQUIRED_TEST_CASES.values())
        for simple_name, methods in REQUIRED_METHODS.items():
            matches = [name for name in index if name.rsplit(".", 1)[-1] == simple_name]
            self.assertEqual(1, len(matches), simple_name)
            required.update(f"{matches[0]}#{method}" for method in methods)
        for workflow in (root / ".github/workflows").glob("*.yml"):
            text = workflow.read_text(encoding="utf-8")
            required.update(re.findall(r"--require-testcase\s+['\"]([^'\"]+)['\"]", text))
            for classname in re.findall(r"--require\s+['\"]?([\w.]+)", text):
                with self.subTest(workflow=workflow.name, required_class=classname):
                    self.assertTrue(any(name == classname or name.rsplit(".", 1)[-1] == classname for name in index))
        for selector in sorted(required):
            classname, method = selector.split("#", 1)
            with self.subTest(selector=selector):
                self.assertTrue(classname in index, f"Missing test source: {classname}")
                self.assertIn("/src/test/java/", index[classname].as_posix())
                self.assertTrue(method in test_methods(classname, index), f"Missing annotated or inherited JUnit method: {selector}")

    def test_uses_annotations_and_inheritance_not_comments_or_helper_names(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "module/src/main/java/example/Base.java"
            base.parent.mkdir(parents=True)
            base.write_text(
                "package example; public class Base { @Test public void inherited() {} }",
                encoding="utf-8",
            )
            child = root / "module/src/test/java/example/Child.java"
            child.parent.mkdir(parents=True)
            child.write_text(
                "package example; class Child extends Base {\n"
                "// @Test void commentDecoy() {}\n"
                'String text = "@Test void stringDecoy() {}";\n'
                "void helper() {}\n"
                "@ParameterizedTest @ValueSource(strings = {\"a\"}) void parameterized(String value) {}\n"
                "}", encoding="utf-8",
            )
            self.assertEqual({"inherited", "parameterized"}, test_methods("example.Child", source_index(root)))


if __name__ == "__main__":
    unittest.main()
