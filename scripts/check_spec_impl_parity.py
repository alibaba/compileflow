#!/usr/bin/env python3
"""Verify process-format schemas, implementations, and specifications stay aligned."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema"

NODE_CONTRACT = {
    "start": ("StartParser", "StartWriter"),
    "end": ("EndParser", "EndWriter"),
    "autoTask": ("AutoTaskParser", "AutoTaskWriter"),
    "scriptTask": ("ScriptTaskParser", "ScriptTaskWriter"),
    "exclusive": ("ExclusiveParser", "ExclusiveWriter"),
    "parallel": ("ParallelParser", "ParallelWriter"),
    "inclusive": ("InclusiveParser", "InclusiveWriter"),
    "subBpm": ("SubBpmParser", "SubBpmWriter"),
    "bpmCall": ("BpmCallParser", "BpmCallWriter"),
    "waitTask": ("WaitTaskParser", "WaitTaskWriter"),
    "waitEventTask": ("WaitEventTaskParser", "WaitEventTaskWriter"),
    "timerTask": ("TimerTaskParser", "TimerTaskWriter"),
    "while": ("WhileParser", "WhileWriter"),
    "foreach": ("ForEachParser", "ForEachWriter"),
    "continue": ("ContinueParser", "ContinueWriter"),
    "break": ("BreakParser", "BreakWriter"),
    "note": ("NoteParser", "NoteWriter"),
}

STRUCTURAL_PARSERS = {
    "bpm": "TbbpmDocumentParser",
    "var": "VarParser",
    "invocationPolicy": "InvocationPolicyParser",
    "transition": "TransitionParser",
    "action": "ActionParser",
    "effectPolicy": "EffectPolicyParser",
    "reconcileAction": "ReconcileActionParser",
    "code": "ScriptSourceParser",
    "input": "InputParser",
    "output": "OutputParser",
}

ACTION_TYPES = {"java", "spring-bean", "script"}

NON_NODE_GLOBAL_ELEMENTS = {
    "bpm",
    "var",
    "invocationPolicy",
    "transition",
    "code",
    "action",
    "effectPolicy",
    "reconcileAction",
    "input",
    "output",
}

BPMN_EXTENSION_ELEMENTS = {
    "action",
    "invocationPolicy",
    "code",
    "effectPolicy",
    "reconcileAction",
    "var",
    "input",
    "output",
}

BPMN_EXTENSION_ATTRIBUTES = {
    "collection",
    "execution",
    "index",
    "item",
    "itemType",
    "classpath",
    "source",
    "target",
    "version",
}

REMOVED_BPMN_EXTENSION_VOCABULARY = {
    "resource",
    "collectionVar",
    "concurrency",
    "elementVar",
    "elementVarClass",
    "indexVar",
    "outputCollection",
    "outputElementVar",
}

BPMN_EXTENSION_ENUM_VALUES = {
    "replayable",
    "effect",
    "none",
    "full",
    "manual",
    "retry",
    "reconcile",
}

REMOVED_BPMN_EXTENSION_ENUM_VALUES = {"forbidden", "safe"}

ENGINE_PROTOCOL_FIXTURES = (
    "compileflow-durable/compileflow-durable-runtime/src/test/resources/"
    "tbbpm-durable-v1/all-constructs.bpm",
    "compileflow-integration-tests/src/test/resources/bpmn20/compat/"
    "call_activity.bpmn",
    "compileflow-integration-tests/src/test/resources/bpmn20/compat/"
    "multi_instance_loop.bpmn",
    "compileflow-integration-tests/src/test/resources/bpmn20/compat/"
    "service_task_with_invocation_policy.bpmn",
    "compileflow-integration-tests/src/test/resources/bpmn20/stateful/"
    "stateful_receive_task.bpmn",
)

PROTOCOL_SOURCE_SUFFIXES = {".bpm", ".bpmn", ".java", ".md", ".ts", ".tsx", ".xml"}
IGNORED_SOURCE_PARTS = {".git", "dist", "node_modules", "target"}
AUTO_TASK_SCRIPT_PATTERN = re.compile(
    r'<autoTask\b[^>]*(?<!/)>(?:(?!</autoTask>)[\s\S])*?'
    r'<action\s+type\s*=\s*\\?"script\\?"',
)
ALLOWED_INVALID_AUTO_TASK_SCRIPT_FIXTURES = {
    Path(
        "compileflow-tbbpm/src/test/java/com/alibaba/compileflow/engine/"
        "tbbpm/validation/TbbpmActionValidationTest.java"
    ): 1,
}
BPMN_SERVICE_TASK_SCRIPT_PATTERN = re.compile(
    r'<(?:bpmn:)?serviceTask\b[^>]*(?<!/)>'
    r'(?:(?!</(?:bpmn:)?serviceTask>)[\s\S])*?'
    r'<cf:action\s+[^>]*type\s*=\s*\\?"script\\?"',
)
ALLOWED_INVALID_BPMN_SERVICE_SCRIPT_FIXTURES = {
    Path(
        "compileflow-bpmn/src/test/java/com/alibaba/compileflow/engine/bpmn/"
        "validation/BpmnModelValidatorTest.java"
    ): 1,
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)
    print(f"  FAIL  {message}")


def require_file(
    errors: list[str],
    source_root: Path,
    class_name: str,
) -> Path | None:
    matches = list(source_root.rglob(f"{class_name}.java"))
    if len(matches) != 1:
        fail(
            errors,
            f"{class_name}.java expected exactly once, found {len(matches)}",
        )
        return None
    return matches[0]


def require_registration(
    errors: list[str],
    registry_source: str,
    class_name: str,
    registry_name: str,
) -> None:
    registration = f"new {class_name}()"
    if registration not in registry_source:
        fail(errors, f"{class_name} is not registered in {registry_name}")


def global_xsd_elements(xsd_path: Path) -> set[str]:
    root = ET.parse(xsd_path).getroot()
    element_tag = f"{{{XSD_NAMESPACE}}}element"
    return {
        element.attrib["name"]
        for element in root
        if element.tag == element_tag and "name" in element.attrib
    }


def named_xsd_elements(xsd_path: Path) -> set[str]:
    root = ET.parse(xsd_path).getroot()
    element_tag = f"{{{XSD_NAMESPACE}}}element"
    return {
        element.attrib["name"]
        for element in root.iter(element_tag)
        if "name" in element.attrib
    }


def global_xsd_attributes(xsd_path: Path) -> set[str]:
    root = ET.parse(xsd_path).getroot()
    attribute_tag = f"{{{XSD_NAMESPACE}}}attribute"
    return {
        attribute.attrib["name"]
        for attribute in root
        if attribute.tag == attribute_tag and "name" in attribute.attrib
    }


def xsd_enumerations(xsd_path: Path) -> set[str]:
    root = ET.parse(xsd_path).getroot()
    enumeration_tag = f"{{{XSD_NAMESPACE}}}enumeration"
    return {
        enumeration.attrib["value"]
        for enumeration in root.iter(enumeration_tag)
        if "value" in enumeration.attrib
    }


def xsd_complex_type_attributes(
    xsd_path: Path,
    type_name: str,
) -> dict[str, str | None]:
    root = ET.parse(xsd_path).getroot()
    complex_type_tag = f"{{{XSD_NAMESPACE}}}complexType"
    attribute_tag = f"{{{XSD_NAMESPACE}}}attribute"
    complex_types = [
        element
        for element in root.findall(complex_type_tag)
        if element.attrib.get("name") == type_name
    ]
    if len(complex_types) != 1:
        return {}
    return {
        attribute.attrib["name"]: attribute.attrib.get("use")
        for attribute in complex_types[0].iter(attribute_tag)
        if "name" in attribute.attrib
    }


def xsd_child_element_types(
    xsd_path: Path,
    owner_kind: str,
    owner_name: str,
) -> dict[str, str]:
    root = ET.parse(xsd_path).getroot()
    owner_tag = f"{{{XSD_NAMESPACE}}}{owner_kind}"
    element_tag = f"{{{XSD_NAMESPACE}}}element"
    owners = [
        element
        for element in root.findall(owner_tag)
        if element.attrib.get("name") == owner_name
    ]
    if len(owners) != 1:
        return {}
    return {
        element.attrib["name"]: element.attrib["type"].split(":")[-1]
        for element in owners[0].iter(element_tag)
        if "name" in element.attrib and "type" in element.attrib
    }


def require_source_fragments(
    errors: list[str],
    source_path: Path,
    fragments: tuple[str, ...],
) -> None:
    source = source_path.read_text(encoding="utf-8")
    for fragment in fragments:
        if fragment not in source:
            fail(errors, f"{source_path.name} is missing contract fragment: {fragment}")


def xml_examples(markdown: str) -> list[ET.Element]:
    examples: list[ET.Element] = []
    for source in re.findall(r"```xml\s*\n(.*?)\n```", markdown, re.DOTALL):
        source = source.strip()
        if source.startswith("<?xml") or source.startswith("<bpm"):
            examples.append(ET.fromstring(source))
    return examples


def validate_tbbpm_examples(
    errors: list[str],
    specification: Path,
) -> None:
    try:
        examples = xml_examples(specification.read_text(encoding="utf-8"))
    except ET.ParseError as failure:
        fail(errors, f"{specification.name} contains malformed XML: {failure}")
        return
    if not examples:
        fail(errors, f"{specification.name} contains no complete TBBPM example")
        return
    for root in examples:
        for action in root.iter("action"):
            action_type = action.attrib.get("type")
            if action_type not in ACTION_TYPES:
                fail(
                    errors,
                    f"{specification.name} documents unsupported action type "
                    f"{action_type!r}",
                )
            if action_type == "script" and (
                not action.attrib.get("language", "").strip()
                or len(action.findall("code")) != 1
            ):
                fail(
                    errors,
                    f"{specification.name} script action must declare language "
                    "and exactly one code element",
                )


def validate_forbidden_task_shape(
    errors: list[str],
    project_root: Path,
    pattern: re.Pattern[str],
    allowed_fixtures: dict[Path, int],
    message: str,
) -> None:
    observed_allowed: dict[Path, int] = {}
    for source_path in project_root.rglob("*"):
        if (
            not source_path.is_file()
            or source_path.suffix not in PROTOCOL_SOURCE_SUFFIXES
            or IGNORED_SOURCE_PARTS.intersection(source_path.parts)
        ):
            continue
        relative_path = source_path.relative_to(project_root)
        source = source_path.read_text(encoding="utf-8")
        matches = list(pattern.finditer(source))
        if relative_path in allowed_fixtures:
            observed_allowed[relative_path] = len(matches)
            continue
        for match in matches:
            line = source.count("\n", 0, match.start()) + 1
            fail(
                errors,
                f"{relative_path}:{line} {message}",
            )

    for relative_path, expected_count in allowed_fixtures.items():
        actual_count = observed_allowed.get(relative_path, 0)
        if actual_count != expected_count:
            fail(
                errors,
                f"{relative_path} must contain exactly {expected_count} negative "
                f"forbidden task-shape fixture(s), found {actual_count}",
            )


def validate_executable_task_partition(errors: list[str], project_root: Path) -> None:
    validate_forbidden_task_shape(
        errors,
        project_root,
        AUTO_TASK_SCRIPT_PATTERN,
        ALLOWED_INVALID_AUTO_TASK_SCRIPT_FIXTURES,
        "uses forbidden autoTask + script; definition-owned code must use scriptTask",
    )
    validate_forbidden_task_shape(
        errors,
        project_root,
        BPMN_SERVICE_TASK_SCRIPT_PATTERN,
        ALLOWED_INVALID_BPMN_SERVICE_SCRIPT_FIXTURES,
        "uses forbidden serviceTask + script; definition-owned code must use scriptTask",
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Verify process-format schema/specification parity",
    )
    parser.add_argument(
        "--src",
        type=Path,
        default=Path("."),
        help="Project root",
    )
    parser.add_argument(
        "--xsd",
        type=Path,
        default=Path(
            "compileflow-tbbpm/src/main/resources/TBBPM.xsd",
        ),
        help="Canonical TBBPM XSD",
    )
    parser.add_argument(
        "--bpmn-extension-xsd",
        type=Path,
        default=Path(
            "compileflow-bpmn/src/main/resources/"
            "CompileFlowBpmnExtensions.xsd",
        ),
        help="Canonical CompileFlow BPMN extension XSD",
    )
    args = parser.parse_args()

    project_root = args.src.resolve()
    xsd_path = (
        args.xsd
        if args.xsd.is_absolute()
        else project_root / args.xsd
    )
    bpmn_extension_xsd = (
        args.bpmn_extension_xsd
        if args.bpmn_extension_xsd.is_absolute()
        else project_root / args.bpmn_extension_xsd
    )
    bpmn_root_xsd = (
        project_root / "compileflow-bpmn/src/main/resources/BPMN20.xsd"
    )
    bpmn_specifications = [
        project_root / "docs/en/specifications/bpmn-extensions.md",
        project_root / "docs/zh/specifications/bpmn-extensions.md",
    ]
    tbbpm_specifications = [
        project_root / "docs/en/specifications/tbbpm.md",
        project_root / "docs/zh/specifications/tbbpm.md",
    ]
    java_root = (
        project_root
        / "compileflow-tbbpm/src/main/java"
    )
    parser_registry = (
        java_root
        / "com/alibaba/compileflow/engine/tbbpm/parser"
        / "TbbpmElementParserRegistry.java"
    )
    writer_registry = (
        java_root
        / "com/alibaba/compileflow/engine/tbbpm/writer"
        / "TbbpmElementWriterRegistry.java"
    )
    action_parsing = (
        java_root
        / "com/alibaba/compileflow/engine/tbbpm/parser/action"
        / "ActionParsing.java"
    )
    executable_model_validation = (
        project_root
        / "compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/validation"
        / "ExecutableModelValidation.java"
    )
    tbbpm_call_writer = (
        java_root
        / "com/alibaba/compileflow/engine/tbbpm/writer"
        / "BpmCallWriter.java"
    )
    bpmn_writer = (
        project_root
        / "compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/writer"
        / "BpmnXmlWriter.java"
    )
    workbench_protocol_test = (
        project_root
        / "compileflow-workbench/apps/web/src/authoring/designer/serialization/__tests__"
        / "engineProtocolFixtures.test.ts"
    )
    protocol_golden = (
        project_root / "docs/specs/fixtures/engine-workbench-protocol-golden-v1.json"
    )

    required_paths = [
        xsd_path,
        java_root,
        parser_registry,
        writer_registry,
        action_parsing,
        executable_model_validation,
        tbbpm_call_writer,
        bpmn_writer,
        workbench_protocol_test,
        protocol_golden,
        bpmn_extension_xsd,
        bpmn_root_xsd,
        *(project_root / fixture for fixture in ENGINE_PROTOCOL_FIXTURES),
        *tbbpm_specifications,
        *bpmn_specifications,
    ]
    missing = [str(path) for path in required_paths if not path.exists()]
    if missing:
        for path in missing:
            print(f"FAIL  Required path does not exist: {path}")
        sys.exit(1)

    errors: list[str] = []
    parser_registry_source = parser_registry.read_text(encoding="utf-8")
    writer_registry_source = writer_registry.read_text(encoding="utf-8")
    action_dispatch = action_parsing.read_text(encoding="utf-8")

    print("TBBPM XSD node contract")
    xsd_elements = global_xsd_elements(xsd_path)
    named_elements = named_xsd_elements(xsd_path)
    xsd_nodes = xsd_elements - NON_NODE_GLOBAL_ELEMENTS
    expected_nodes = set(NODE_CONTRACT)
    for element in sorted(xsd_nodes - expected_nodes):
        fail(errors, f"XSD node {element} has no parser/writer contract")
    for element in sorted(expected_nodes - xsd_nodes):
        fail(errors, f"contract node {element} is absent from the XSD")

    print("TBBPM node parser/writer registrations")
    for element, (parser_class, writer_class) in NODE_CONTRACT.items():
        parser_file = require_file(errors, java_root, parser_class)
        writer_file = require_file(errors, java_root, writer_class)
        require_registration(
            errors,
            parser_registry_source,
            parser_class,
            parser_registry.name,
        )
        require_registration(
            errors,
            writer_registry_source,
            writer_class,
            writer_registry.name,
        )
        if parser_file is not None and writer_file is not None:
            print(f"  OK    {element}: {parser_class} / {writer_class}")

    print("TBBPM structural parser registrations")
    for element, parser_class in STRUCTURAL_PARSERS.items():
        require_file(errors, java_root, parser_class)
        require_registration(
            errors,
            parser_registry_source,
            parser_class,
            parser_registry.name,
        )
        if element not in named_elements:
            fail(errors, f"structural element {element} is absent from the XSD")
        print(f"  OK    {element}: {parser_class}")

    print("TBBPM flattened action contract")
    action_cases = {
        match.lower().replace("_", "-")
        for match in re.findall(r"case ([A-Z_]+) ->", action_dispatch)
    }
    if action_cases != ACTION_TYPES:
        fail(
            errors,
            "flattened action parser variants differ from the contract: "
            f"expected={sorted(ACTION_TYPES)}, actual={sorted(action_cases)}",
        )
    else:
        print(f"  OK    exact action variants: {sorted(ACTION_TYPES)}")

    print("ProcessCall mapping type ownership")
    tbbpm_action_inputs = xsd_complex_type_attributes(xsd_path, "InputType")
    tbbpm_call_inputs = xsd_complex_type_attributes(xsd_path, "CallInputType")
    tbbpm_action_outputs = xsd_complex_type_attributes(xsd_path, "ActionOutputType")
    tbbpm_call_outputs = xsd_complex_type_attributes(xsd_path, "CallOutputType")
    if tbbpm_action_inputs.get("dataType") != "required":
        fail(errors, "TBBPM action input dataType must be required")
    if "dataType" in tbbpm_call_inputs:
        fail(errors, "TBBPM called-process input must not declare dataType")
    if tbbpm_action_outputs.get("dataType") != "required":
        fail(errors, "TBBPM action output dataType must be required")
    if "dataType" in tbbpm_call_outputs:
        fail(errors, "TBBPM called-process output must not declare dataType")

    action_mapping_types = xsd_child_element_types(xsd_path, "complexType", "ActionType")
    call_mapping_types = xsd_child_element_types(xsd_path, "element", "bpmCall")
    if action_mapping_types.get("input") != "InputType":
        fail(errors, "TBBPM action input must use InputType")
    if action_mapping_types.get("output") != "ActionOutputType":
        fail(errors, "TBBPM action output must use ActionOutputType")
    if call_mapping_types.get("input") != "CallInputType":
        fail(errors, "TBBPM bpmCall input must use CallInputType")
    if call_mapping_types.get("output") != "CallOutputType":
        fail(errors, "TBBPM bpmCall output must use CallOutputType")

    require_source_fragments(
        errors,
        executable_model_validation,
        (
            "validateInputMappings(modelKind, location, mappings.getInputMappings(), true, messages)",
            "validateInputMappings(modelKind, location, mappings.getInputMappings(), false, messages)",
            "called-process input must not declare dataType",
            "called-process output must not declare dataType",
        ),
    )
    require_source_fragments(
        errors,
        tbbpm_call_writer,
        ("writeMappings(node.getInputMappings(), node.getOutputMappings(), xsw, false)",),
    )
    require_source_fragments(
        errors,
        bpmn_writer,
        (
            "!(element instanceof CallActivity)",
            "if (actionBoundary)",
        ),
    )
    print("  OK    Action owns mapping types; exact child Process contract owns call types")

    print("TBBPM specification example contract")
    for specification in tbbpm_specifications:
        validate_tbbpm_examples(errors, specification)
        print(f"  OK    {specification.name}: documented executable vocabulary")

    print("Executable task partition")
    validate_executable_task_partition(errors, project_root)
    print("  OK    Auto/Service Tasks invoke application code; Script Tasks own inline code")

    print("BPMN extension XSD/specification contract")
    bpmn_elements = global_xsd_elements(bpmn_extension_xsd)
    if bpmn_elements != BPMN_EXTENSION_ELEMENTS:
        fail(
            errors,
            "BPMN extension elements differ from the contract: "
            f"expected={sorted(BPMN_EXTENSION_ELEMENTS)}, "
            f"actual={sorted(bpmn_elements)}",
        )
    bpmn_attributes = global_xsd_attributes(bpmn_extension_xsd)
    if bpmn_attributes != BPMN_EXTENSION_ATTRIBUTES:
        fail(
            errors,
            "BPMN extension attributes differ from the contract: "
            f"expected={sorted(BPMN_EXTENSION_ATTRIBUTES)}, "
            f"actual={sorted(bpmn_attributes)}",
        )
    removed_attributes = REMOVED_BPMN_EXTENSION_VOCABULARY & bpmn_attributes
    if removed_attributes:
        fail(errors, f"BPMN extension XSD retains removed attributes: {sorted(removed_attributes)}")
    bpmn_enumerations = xsd_enumerations(bpmn_extension_xsd)
    missing_enumerations = BPMN_EXTENSION_ENUM_VALUES - bpmn_enumerations
    if missing_enumerations:
        fail(
            errors,
            "BPMN extension XSD is missing closed values: "
            f"{sorted(missing_enumerations)}",
        )
    removed_enumerations = REMOVED_BPMN_EXTENSION_ENUM_VALUES & bpmn_enumerations
    if removed_enumerations:
        fail(
            errors,
            "BPMN extension XSD retains removed closed values: "
            f"{sorted(removed_enumerations)}",
        )
    expected_import = (
        'namespace="http://www.compileflow.org" '
        'schemaLocation="CompileFlowBpmnExtensions.xsd"'
    )
    if expected_import not in bpmn_root_xsd.read_text(encoding="utf-8"):
        fail(errors, "BPMN20.xsd does not import the BPMN extension schema")
    for specification in bpmn_specifications:
        specification_text = specification.read_text(encoding="utf-8")
        for element in BPMN_EXTENSION_ELEMENTS:
            if f"cf:{element}" not in specification_text:
                fail(
                    errors,
                    f"{specification.name} does not document cf:{element}",
                )
        for attribute in BPMN_EXTENSION_ATTRIBUTES:
            if f"cf:{attribute}" not in specification_text:
                fail(
                    errors,
                    f"{specification.name} does not document cf:{attribute}",
                )
        stale_vocabulary = {
            name for name in REMOVED_BPMN_EXTENSION_VOCABULARY if f"cf:{name}" in specification_text
        }
        if stale_vocabulary:
            fail(errors, f"{specification.name} retains removed cf: vocabulary: {sorted(stale_vocabulary)}")
    print("  OK    CompileFlow BPMN extension XSD, import, and bilingual specifications")

    print("Engine/Workbench protocol fixture contract")
    workbench_protocol_source = workbench_protocol_test.read_text(encoding="utf-8")
    protocol_golden_source = protocol_golden.read_text(encoding="utf-8")
    if "engine-workbench-protocol-golden-v1.json" not in workbench_protocol_source:
        fail(errors, "Workbench protocol test does not consume the shared golden manifest")
    for fixture in ENGINE_PROTOCOL_FIXTURES:
        if fixture not in workbench_protocol_source and fixture not in protocol_golden_source:
            fail(errors, f"Workbench protocol test does not consume Engine fixture: {fixture}")
    require_source_fragments(
        errors,
        workbench_protocol_test,
        (
            "generateTbbpmXml",
            "parseTbbpmXml",
            "generateBpmnXml",
            "parseBpmnXml",
            "not.toMatch(/<input[^>]*\\bdataType=/)",
            "not.toMatch(/<cf:input[^>]*\\bdataType=/)",
        ),
    )
    print("  OK    Workbench codecs consume canonical Engine fixtures")

    if errors:
        print(f"\nFAILED: {len(errors)} parity error(s)")
        sys.exit(1)

    print("\nPASSED: process-format schemas, implementations, and specifications are aligned.")


if __name__ == "__main__":
    main()
