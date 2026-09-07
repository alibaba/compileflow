# Flow diagram examples

The Mermaid files in [`docs/examples/flow-diagrams`](../../examples/flow-diagrams/) illustrate control-flow shapes.
They are not TBBPM or BPMN definitions and are not parser, code-generation, or runtime test evidence.

| File                                                                                                  | Shows                          |
| ----------------------------------------------------------------------------------------------------- | ------------------------------ |
| [`sampleBranchMerge.mermaid`](../../examples/flow-diagrams/sampleBranchMerge.mermaid)                 | Simple branch and merge        |
| [`complexBranchMerge.mermaid`](../../examples/flow-diagrams/complexBranchMerge.mermaid)               | Multiple branches and merges   |
| [`simple4LevelGateway.mermaid`](../../examples/flow-diagrams/simple4LevelGateway.mermaid)             | Four sequential gateway levels |
| [`nested4LevelGateway.mermaid`](../../examples/flow-diagrams/nested4LevelGateway.mermaid)             | Four nested gateway levels     |
| [`ultraComplexStructureFlow.mermaid`](../../examples/flow-diagrams/ultraComplexStructureFlow.mermaid) | Combined control-flow shapes   |

Open a file in a Mermaid-capable Markdown viewer. To render an SVG from the repository root:

```bash
pnpm dlx @mermaid-js/mermaid-cli \
  -i docs/examples/flow-diagrams/sampleBranchMerge.mermaid \
  -o sampleBranchMerge.svg
```

Executable semantics come from the TBBPM/BPMN specifications, schemas, implementation, and tests. A diagram must not
be cited as evidence that CompileFlow supports a node or transition.
