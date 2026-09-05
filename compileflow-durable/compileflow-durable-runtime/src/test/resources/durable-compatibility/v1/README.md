# Durable compatibility corpus V1

These files freeze the CompileFlow 2.0 release-candidate TBBPM and BPMN definitions, Machine resume coordinates and versioned
persisted envelopes. They become immutable when 2.0 is released; until then, a deliberate Developer Preview Kernel
protocol correction must update the corpus and its semantic assertions together. Later compatible releases must read
and resume the released bytes with current parser/compiler/runtime code. Add a new directory only when a real
incompatible authoritative representation needs its own immutable fixtures. The directory name is test-fixture
organization, not Runtime routing or Machine identity.

The persisted TBBPM corpus covers Start, Wait, Timer, Effect, loop scope state and one committed Kernel fact. BPMN
fixtures freeze representative Wait, Timer, Effect, and structured-scope source profiles. Every recovery test enters
through production semantic-compiler discovery before current code resumes the frozen facts. The companion
`tbbpm-durable-v1/all-constructs.bpm` fixture is also compiled by the compatibility test to freeze the representative
Parallel, Inclusive, nested-scope and Child definition surface without inventing placeholder continuation bytes.
