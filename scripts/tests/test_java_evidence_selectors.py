"""Bind required Java evidence identities to annotated repository test methods."""

import os
import re
import tempfile
import unittest
from pathlib import Path

from scripts.check_internal_links import SKIP_DIRS
from scripts.verify_durable_release_evidence import REQUIRED_TEST_CASES
from scripts.verify_workbench_postgres_evidence import REQUIRED_METHODS


METHOD_DECLARATION = re.compile(
    r"(?:(?:public|protected|private|final|static|synchronized)\s+)*void\s+(\w+)\s*\("
)
TEST_ANNOTATION = re.compile(r"^@(?:[\w.]+\.)?(?:Test|ParameterizedTest|RepeatedTest|TestTemplate)\b")


def strip_java_non_code(source: str) -> str:
    """Replace Java comments and literals with spaces while preserving newlines."""
    result = list(source)
    index = 0
    length = len(source)
    while index < length:
        if source.startswith("//", index):
            end = source.find("\n", index + 2)
            end = length if end < 0 else end
        elif source.startswith("/*", index):
            end = source.find("*/", index + 2)
            end = length if end < 0 else end + 2
        elif source.startswith('"""', index):
            end = source.find('"""', index + 3)
            end = length if end < 0 else end + 3
        elif source[index] in {'"', "'"}:
            quote = source[index]
            end = index + 1
            while end < length:
                if source[end] == "\\":
                    end += 2
                    continue
                end += 1
                if source[end - 1] == quote:
                    break
        else:
            index += 1
            continue
        for position in range(index, min(end, length)):
            if result[position] != "\n":
                result[position] = " "
        index = end
    return "".join(result)


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


def annotated_test_methods(source: str) -> set[str]:
    """Return test methods using a linear, line-oriented annotation scan."""
    methods = set()
    awaiting_method = False
    annotation_depth = 0
    declaration = ""
    for line in source.splitlines():
        stripped = line.strip()
        if annotation_depth > 0:
            annotation_depth += stripped.count("(") - stripped.count(")")
            continue
        if stripped.startswith("@"):
            if TEST_ANNOTATION.search(stripped):
                awaiting_method = True
            annotation_depth = max(0, stripped.count("(") - stripped.count(")"))
            continue
        if not awaiting_method or not stripped:
            continue
        declaration += " " + stripped
        method = METHOD_DECLARATION.search(declaration)
        if method:
            methods.add(method.group(1))
            awaiting_method = False
            declaration = ""
        elif ";" in stripped or "{" in stripped:
            awaiting_method = False
            declaration = ""
    return methods


def test_methods(identity: str, index: dict[str, Path], seen: frozenset[str] = frozenset()) -> set[str]:
    if identity in seen:
        raise AssertionError(f"Cyclic test inheritance: {identity}")
    path = index[identity]
    source = strip_java_non_code(path.read_text(encoding="utf-8"))
    package = re.search(r"\bpackage\s+([\w.]+)\s*;", source)
    if package and identity.rsplit(".", 1)[0] != package.group(1):
        raise AssertionError(f"Package does not match source path: {path}")
    methods = annotated_test_methods(source)
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
                "package example; public class Base {\n@Test\npublic void inherited() {}\n}",
                encoding="utf-8",
            )
            child = root / "module/src/test/java/example/Child.java"
            child.parent.mkdir(parents=True)
            child.write_text(
                "package example; class Child extends Base {\n"
                "// @Test void commentDecoy() {}\n"
                "/* @Test void blockCommentDecoy() {} */\n"
                'String text = "@Test void stringDecoy() {}";\n'
                'String block = """@Test void textBlockDecoy() {}""";\n'
                "char quote = '\\''; // @Test void characterDecoy() {}\n"
                "void helper() {}\n"
                "@ParameterizedTest\n"
                "@ValueSource(strings = {\"a\"})\n"
                "void parameterized(String value) {}\n"
                "}", encoding="utf-8",
            )
            self.assertEqual({"inherited", "parameterized"}, test_methods("example.Child", source_index(root)))


if __name__ == "__main__":
    unittest.main()
