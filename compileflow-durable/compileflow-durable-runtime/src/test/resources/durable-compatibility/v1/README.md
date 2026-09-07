# Durable Compatibility Corpus V1

These fixtures preserve TBBPM and BPMN definitions, Machine resume coordinates, and versioned persisted envelopes for
Durable recovery tests. The parser, compiler, and runtime must read and resume the stored bytes without rewriting them.
Fixture directories are immutable; put any incompatible persisted representation in a separate directory. `v1`
identifies the fixture format, not a CompileFlow release or Machine identity.

The persisted TBBPM corpus covers Start, Wait, Timer, Effect, loop scope state and one committed Kernel fact. BPMN
fixtures cover representative Wait, Timer, Effect, and structured-scope definitions. Recovery tests use the production
semantic compiler before resuming the stored state. The companion `tbbpm-durable-v1/all-constructs.bpm` fixture also
covers Parallel, Inclusive, nested-scope, and child-definition semantics without synthetic continuation bytes.
