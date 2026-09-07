-- CompileFlow 2.0 Durable Kernel -- PostgreSQL greenfield schema.
--
-- PostgreSQL V1 maps the Durable authority categories to seven tables; the table count is an
-- implementation layout, not a Kernel invariant. Durable truth is restricted to immutable stored
-- Process semantics, the Run continuation snapshot, committed execution facts, semantic
-- Journal facts and the closed Kernel Integration Event set.  Generated code, application/provider
-- identity, aliases, ABI/codec identity columns, worker presence and compatibility metadata never enter it.
-- Engine-owned payload bytes carry only the compact CFD + format-version header.
-- All authority timestamps are produced by PostgreSQL.

CREATE TABLE public.cf_durable_process (
    process_id            uuid PRIMARY KEY,
    process_code          varchar(128) COLLATE "C" NOT NULL,
    model_type            varchar(16) COLLATE "C" NOT NULL,
    definition_bytes      bytea NOT NULL,
    definition_digest     char(64) COLLATE "C" NOT NULL,
    registered_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_cf_durable_process_code CHECK (
        process_code <> '' AND btrim(process_code) = process_code),
    CONSTRAINT ck_cf_durable_process_model_type CHECK (model_type IN ('TBBPM', 'BPMN')),
    CONSTRAINT ck_cf_durable_process_digest CHECK (definition_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cf_durable_process_definition CHECK (
        octet_length(definition_bytes) BETWEEN 1 AND 4194304),
    CONSTRAINT uq_cf_durable_process_definition_digest UNIQUE (definition_digest)
);

CREATE FUNCTION public.cf_durable_process_reject_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'CompileFlow Durable Process definitions are immutable and cannot be updated';
END;
$$;

CREATE TRIGGER trg_cf_durable_process_immutable
BEFORE UPDATE ON public.cf_durable_process
FOR EACH ROW EXECUTE FUNCTION public.cf_durable_process_reject_update();

CREATE TABLE public.cf_durable_run (
    run_id                    uuid PRIMARY KEY,
    process_id                uuid NOT NULL,
    process_code              varchar(128) COLLATE "C" NOT NULL,
    namespace                 varchar(128) COLLATE "C" NOT NULL,
    process_version           varchar(64) COLLATE "C",

    status                    varchar(16) COLLATE "C" NOT NULL DEFAULT 'RUNNABLE',
    control_state             varchar(24) COLLATE "C" NOT NULL DEFAULT 'ACTIVE',
    control_revision          bigint NOT NULL DEFAULT 0,
    available_at              timestamptz NOT NULL DEFAULT clock_timestamp(),

    -- Opaque Engine-owned continuation. SQL never interprets model coordinates or frontiers.
    continuation_envelope     bytea NOT NULL,
    occurrence_sequence       bigint NOT NULL DEFAULT 0,
    turn_fault_streak         integer NOT NULL DEFAULT 0,
    retry_code                varchar(64) COLLATE "C",
    retry_observed_at         timestamptz,

    cancel_requested_at       timestamptz,

    lease_owner               varchar(128) COLLATE "C",
    lease_token               uuid,
    lease_until               timestamptz,

    result_envelope           bytea,
    failure_code              varchar(128) COLLATE "C",
    failure_message           varchar(2048) COLLATE "C",
    created_at                timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at                timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at              timestamptz,

    CONSTRAINT fk_cf_durable_run_root_process FOREIGN KEY (process_id)
        REFERENCES public.cf_durable_process(process_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_cf_durable_run_process_identity CHECK (
        namespace <> '' AND btrim(namespace) = namespace
        AND process_code <> '' AND btrim(process_code) = process_code
        AND (process_version IS NULL
             OR (process_version <> '' AND btrim(process_version) = process_version))),
    CONSTRAINT ck_cf_durable_run_status CHECK (status IN (
        'RUNNABLE', 'RUNNING', 'WAITING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_cf_durable_run_control CHECK (
        control_state IN ('ACTIVE', 'PAUSE_REQUESTED', 'PAUSED')
        AND (status <> 'RUNNING' OR control_state <> 'PAUSED')
        AND (status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED') OR control_state = 'ACTIVE')),
    CONSTRAINT ck_cf_durable_run_counters CHECK (
        control_revision >= 0 AND occurrence_sequence >= 0 AND turn_fault_streak >= 0),
    CONSTRAINT ck_cf_durable_run_retry CHECK (
        (turn_fault_streak = 0 AND retry_code IS NULL AND retry_observed_at IS NULL)
        OR (status IN ('RUNNABLE', 'RUNNING')
            AND turn_fault_streak > 0 AND retry_code IS NOT NULL AND retry_observed_at IS NOT NULL)),
    CONSTRAINT ck_cf_durable_run_retry_taxonomy CHECK (
        retry_code IS NULL OR retry_code = 'TURN_EXECUTION_FAULT'),
    CONSTRAINT ck_cf_durable_run_continuation CHECK (
        octet_length(continuation_envelope) BETWEEN 5 AND 4194304
        AND substring(continuation_envelope FROM 1 FOR 3) = decode('434644', 'hex')),
    CONSTRAINT ck_cf_durable_run_result CHECK (
        result_envelope IS NULL OR (
            octet_length(result_envelope) BETWEEN 5 AND 4194304
            AND substring(result_envelope FROM 1 FOR 3) = decode('434644', 'hex'))),
    CONSTRAINT ck_cf_durable_run_lease CHECK (
        (status = 'RUNNING' AND lease_owner IS NOT NULL
         AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_owner IS NULL
            AND lease_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_cf_durable_run_terminal CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND completed_at IS NULL)),
    CONSTRAINT ck_cf_durable_run_failure CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_code <> ''
         AND failure_message IS NOT NULL AND failure_message <> '')
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_message IS NULL)),
    CONSTRAINT ck_cf_durable_run_lifetime CHECK (
        updated_at >= created_at
        AND (retry_observed_at IS NULL OR retry_observed_at >= created_at)
        AND (cancel_requested_at IS NULL OR cancel_requested_at >= created_at)
        AND (completed_at IS NULL OR completed_at >= updated_at))
);

-- Process code is a query projection of the immutable root definition. Namespace and Version are
-- admission attribution owned by the Run and never participate in recovery identity.
CREATE FUNCTION public.cf_durable_run_validate_root_process() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM public.cf_durable_process p
         WHERE p.process_id = NEW.process_id
           AND p.process_code = NEW.process_code
    ) THEN
        RAISE EXCEPTION 'Durable Run root Process does not match process_id';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cf_durable_run_root_process
BEFORE INSERT OR UPDATE OF process_id, process_code ON public.cf_durable_run
FOR EACH ROW EXECUTE FUNCTION public.cf_durable_run_validate_root_process();

CREATE INDEX idx_cf_durable_run_dispatch
    ON public.cf_durable_run(available_at, created_at, run_id)
    WHERE status = 'RUNNABLE' AND control_state = 'ACTIVE' AND cancel_requested_at IS NULL;
CREATE INDEX idx_cf_durable_run_expired_lease
    ON public.cf_durable_run(lease_until, run_id) WHERE status = 'RUNNING';
CREATE INDEX idx_cf_durable_run_recent
    ON public.cf_durable_run(created_at DESC, run_id DESC);
CREATE INDEX idx_cf_durable_run_namespace_recent
    ON public.cf_durable_run(namespace, created_at DESC, run_id DESC);
CREATE INDEX idx_cf_durable_run_process_recent
    ON public.cf_durable_run(namespace, process_code, created_at DESC, run_id DESC);
CREATE INDEX idx_cf_durable_run_retention
    ON public.cf_durable_run(completed_at, run_id)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED');

-- SQL cannot inspect the opaque continuation, so each Run records the exact stored Processes its
-- continuation may enter.  These rows are reachability references, not a second execution graph.
CREATE TABLE public.cf_durable_run_recovery_process (
    run_id       uuid NOT NULL REFERENCES public.cf_durable_run(run_id)
                 ON UPDATE RESTRICT ON DELETE CASCADE,
    process_id   uuid NOT NULL REFERENCES public.cf_durable_process(process_id)
                 ON UPDATE RESTRICT ON DELETE RESTRICT,
    PRIMARY KEY (run_id, process_id)
);

CREATE INDEX idx_cf_durable_run_recovery_process_process_id
    ON public.cf_durable_run_recovery_process(process_id, run_id);

-- EXTERNAL and TIMER are one occurrence state machine.  Only their readiness predicate differs;
-- partial indexes preserve the different polling paths without duplicating lifecycle code.
CREATE TABLE public.cf_durable_wait (
    wait_id               uuid PRIMARY KEY,
    run_id                uuid NOT NULL REFERENCES public.cf_durable_run(run_id)
                          ON UPDATE RESTRICT ON DELETE RESTRICT,
    occurrence_sequence   bigint NOT NULL,
    process_id            uuid NOT NULL REFERENCES public.cf_durable_process(process_id)
                          ON UPDATE RESTRICT ON DELETE RESTRICT,
    process_invocation_id bigint NOT NULL,
    frontier_id           varchar(128) COLLATE "C" NOT NULL,
    element_id            varchar(128) COLLATE "C" NOT NULL,
    kind                  varchar(8) COLLATE "C" NOT NULL,
    event_name            varchar(512) COLLATE "C",
    token_digest          char(64) COLLATE "C",
    due_at                timestamptz,
    status                varchar(12) COLLATE "C" NOT NULL DEFAULT 'ACTIVE',
    resolution_kind       varchar(10) COLLATE "C",
    result_envelope       bytea,
    created_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at           timestamptz,
    consumed_at           timestamptz,
    CONSTRAINT uq_cf_durable_wait_position UNIQUE (run_id, occurrence_sequence),
    CONSTRAINT fk_cf_durable_wait_recovery_process FOREIGN KEY (run_id, process_id)
        REFERENCES public.cf_durable_run_recovery_process(run_id, process_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_cf_durable_wait_sequence CHECK (occurrence_sequence > 0),
    CONSTRAINT ck_cf_durable_wait_invocation CHECK (process_invocation_id >= 0),
    CONSTRAINT ck_cf_durable_wait_frontier CHECK (frontier_id <> ''),
    CONSTRAINT ck_cf_durable_wait_kind CHECK (kind IN ('EXTERNAL', 'TIMER')),
    CONSTRAINT ck_cf_durable_wait_status CHECK (status IN ('ACTIVE', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_cf_durable_wait_shape CHECK (
        (kind = 'EXTERNAL' AND (event_name IS NULL OR event_name <> '')
         AND token_digest IS NOT NULL
         AND token_digest ~ '^[0-9a-f]{64}$')
        OR (kind = 'TIMER' AND event_name IS NULL AND token_digest IS NULL
            AND due_at IS NOT NULL)),
    CONSTRAINT ck_cf_durable_wait_result CHECK (
        (status = 'ACTIVE' AND resolution_kind IS NULL
         AND result_envelope IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolution_kind IS NOT NULL
            AND result_envelope IS NOT NULL AND resolved_at IS NOT NULL
            AND ((kind = 'EXTERNAL' AND resolution_kind IN ('COMPLETED', 'EXPIRED'))
                 OR (kind = 'TIMER' AND resolution_kind = 'FIRED')))
        OR (status = 'CANCELLED' AND resolution_kind IS NULL
            AND result_envelope IS NULL AND resolved_at IS NOT NULL)),
    CONSTRAINT ck_cf_durable_wait_payload CHECK (
        result_envelope IS NULL OR (
            octet_length(result_envelope) BETWEEN 5 AND 4194304
            AND substring(result_envelope FROM 1 FOR 3) = decode('434644', 'hex'))),
    CONSTRAINT ck_cf_durable_wait_lifetime CHECK (
        (resolved_at IS NULL OR resolved_at >= created_at)
        -- Absolute Timers may already be due when scheduled; relative Wait deadlines may not.
        AND (kind = 'TIMER' OR due_at IS NULL OR due_at >= created_at)
        AND (resolution_kind <> 'EXPIRED' OR (due_at IS NOT NULL AND resolved_at >= due_at))
        AND (resolution_kind <> 'FIRED' OR (due_at IS NOT NULL AND resolved_at >= due_at))
        AND (consumed_at IS NULL
             OR (status = 'RESOLVED' AND consumed_at >= resolved_at)))
);

CREATE UNIQUE INDEX uq_cf_durable_wait_token
    ON public.cf_durable_wait(token_digest) WHERE kind = 'EXTERNAL';
CREATE INDEX idx_cf_durable_wait_active_run
    ON public.cf_durable_wait(run_id, frontier_id, occurrence_sequence) WHERE status = 'ACTIVE';
CREATE INDEX idx_cf_durable_wait_due
    ON public.cf_durable_wait(due_at, wait_id)
    WHERE status = 'ACTIVE' AND due_at IS NOT NULL;
CREATE INDEX idx_cf_durable_wait_resolved_unconsumed
    ON public.cf_durable_wait(run_id, frontier_id, occurrence_sequence)
    WHERE status = 'RESOLVED' AND consumed_at IS NULL;
CREATE INDEX idx_cf_durable_wait_consumed_retention
    ON public.cf_durable_wait(consumed_at, wait_id) WHERE consumed_at IS NOT NULL;

CREATE TABLE public.cf_durable_effect (
    effect_id              uuid PRIMARY KEY,
    run_id                 uuid NOT NULL REFERENCES public.cf_durable_run(run_id)
                           ON UPDATE RESTRICT ON DELETE RESTRICT,
    occurrence_sequence    bigint NOT NULL,
    process_id             uuid NOT NULL REFERENCES public.cf_durable_process(process_id)
                           ON UPDATE RESTRICT ON DELETE RESTRICT,
    process_invocation_id  bigint NOT NULL,
    frontier_id            varchar(128) COLLATE "C" NOT NULL,
    element_id             varchar(128) COLLATE "C" NOT NULL,
    recovery_mode          varchar(12) COLLATE "C" NOT NULL,
    max_attempts           integer NOT NULL,
    max_reconcile_attempts integer NOT NULL,
    retry_delay_ms         bigint,
    recovery_deadline_ms   bigint,
    input_envelope         bytea NOT NULL,
    result_envelope        bytea,
    status                 varchar(12) COLLATE "C" NOT NULL DEFAULT 'PENDING',
    running_operation      varchar(12) COLLATE "C",
    dispatch_attempts      integer NOT NULL DEFAULT 0,
    reconcile_attempts     integer NOT NULL DEFAULT 0,
    available_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    lease_owner            varchar(128) COLLATE "C",
    lease_token            uuid,
    lease_until            timestamptz,
    unknown_since          timestamptz,
    readiness_code         varchar(64) COLLATE "C",
    review_required_at     timestamptz,
    review_reason          varchar(2048) COLLATE "C",
    review_revision        bigint NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at             timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at           timestamptz,
    consumed_at            timestamptz,
    CONSTRAINT uq_cf_durable_effect_position UNIQUE (run_id, occurrence_sequence),
    CONSTRAINT fk_cf_durable_effect_recovery_process FOREIGN KEY (run_id, process_id)
        REFERENCES public.cf_durable_run_recovery_process(run_id, process_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_cf_durable_effect_sequence CHECK (occurrence_sequence > 0),
    CONSTRAINT ck_cf_durable_effect_invocation CHECK (process_invocation_id >= 0),
    CONSTRAINT ck_cf_durable_effect_frontier CHECK (frontier_id <> ''),
    CONSTRAINT ck_cf_durable_effect_status CHECK (status IN (
        'PENDING', 'RUNNING', 'UNKNOWN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_cf_durable_effect_operation CHECK (
        running_operation IS NULL OR running_operation IN ('DISPATCH', 'RECONCILE')),
    CONSTRAINT ck_cf_durable_effect_counters CHECK (
        dispatch_attempts >= 0 AND reconcile_attempts >= 0 AND review_revision >= 0),
    CONSTRAINT ck_cf_durable_effect_recovery CHECK (
        max_attempts BETWEEN 1 AND 100
        AND max_reconcile_attempts BETWEEN 0 AND 1000
        AND (retry_delay_ms IS NULL OR retry_delay_ms BETWEEN 1 AND 86400000)
        AND (recovery_deadline_ms IS NULL OR recovery_deadline_ms BETWEEN 1 AND 2592000000)
        AND (
            (recovery_mode = 'MANUAL' AND max_attempts = 1
             AND max_reconcile_attempts = 0 AND retry_delay_ms IS NULL
             AND recovery_deadline_ms IS NULL)
            OR (recovery_mode = 'RETRY' AND max_attempts >= 2
                AND max_reconcile_attempts = 0
                AND retry_delay_ms IS NOT NULL)
            OR (recovery_mode = 'RECONCILE' AND max_reconcile_attempts > 0
                AND retry_delay_ms IS NOT NULL))),
    CONSTRAINT ck_cf_durable_effect_payload CHECK (
        octet_length(input_envelope) BETWEEN 5 AND 4194304
        AND substring(input_envelope FROM 1 FOR 3) = decode('434644', 'hex')
        AND (result_envelope IS NULL OR (
            octet_length(result_envelope) BETWEEN 5 AND 4194304
            AND substring(result_envelope FROM 1 FOR 3) = decode('434644', 'hex')))),
    CONSTRAINT ck_cf_durable_effect_lease CHECK (
        (status = 'RUNNING' AND running_operation IS NOT NULL
         AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND running_operation IS NULL
            AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_cf_durable_effect_unknown CHECK (
        (status = 'UNKNOWN' AND unknown_since IS NOT NULL)
        OR (status = 'RUNNING' AND running_operation = 'RECONCILE' AND unknown_since IS NOT NULL)
        OR ((status <> 'UNKNOWN' AND status <> 'RUNNING')
            OR (status = 'RUNNING' AND running_operation = 'DISPATCH'))
           AND unknown_since IS NULL),
    CONSTRAINT ck_cf_durable_effect_review CHECK (
        (review_required_at IS NULL AND review_reason IS NULL)
        OR (status = 'UNKNOWN' AND review_required_at IS NOT NULL AND review_reason IS NOT NULL)),
    CONSTRAINT ck_cf_durable_effect_readiness CHECK (
        (readiness_code IS NULL OR status IN ('PENDING', 'UNKNOWN'))
        AND (readiness_code IS NULL OR readiness_code IN (
            'PROCESS_RUNTIME_NOT_READY', 'ACTION_NOT_READY',
            'RECONCILE_ACTION_NOT_READY'))),
    CONSTRAINT ck_cf_durable_effect_terminal CHECK (
        (status = 'COMPLETED' AND result_envelope IS NOT NULL AND completed_at IS NOT NULL)
        OR (status = 'CANCELLED' AND result_envelope IS NULL AND completed_at IS NOT NULL)
        OR (status NOT IN ('COMPLETED', 'CANCELLED')
            AND result_envelope IS NULL AND completed_at IS NULL)),
    CONSTRAINT ck_cf_durable_effect_lifetime CHECK (
        updated_at >= created_at
        AND (unknown_since IS NULL OR unknown_since >= created_at)
        AND (review_required_at IS NULL OR review_required_at >= unknown_since)
        AND (completed_at IS NULL OR completed_at >= created_at)
        AND (consumed_at IS NULL
             OR (status = 'COMPLETED' AND consumed_at >= completed_at)))
);

CREATE INDEX idx_cf_durable_effect_active_run
    ON public.cf_durable_effect(run_id, frontier_id, occurrence_sequence)
    WHERE status IN ('PENDING', 'RUNNING', 'UNKNOWN');
CREATE INDEX idx_cf_durable_effect_pending
    ON public.cf_durable_effect(available_at, created_at, effect_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_cf_durable_effect_unknown
    ON public.cf_durable_effect(available_at, created_at, effect_id)
    WHERE status = 'UNKNOWN' AND review_required_at IS NULL;
CREATE INDEX idx_cf_durable_effect_expired_lease
    ON public.cf_durable_effect(lease_until, effect_id) WHERE status = 'RUNNING';
CREATE INDEX idx_cf_durable_effect_review
    ON public.cf_durable_effect(review_required_at, effect_id)
    WHERE status = 'UNKNOWN' AND review_required_at IS NOT NULL;
CREATE INDEX idx_cf_durable_effect_completed_unconsumed
    ON public.cf_durable_effect(run_id, frontier_id, occurrence_sequence)
    WHERE status = 'COMPLETED' AND consumed_at IS NULL;
CREATE INDEX idx_cf_durable_effect_consumed_retention
    ON public.cf_durable_effect(consumed_at, effect_id) WHERE consumed_at IS NOT NULL;

-- Audit/diagnostic facts only.  Journal is not a recovery source and is not mirrored 1:1 to Outbox.
CREATE TABLE public.cf_durable_journal (
    run_id                 uuid NOT NULL REFERENCES public.cf_durable_run(run_id)
                           ON UPDATE RESTRICT ON DELETE RESTRICT,
    sequence               bigint NOT NULL,
    fact_type              varchar(64) COLLATE "C" NOT NULL,
    fact_envelope          bytea,
    occurred_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_cf_durable_journal PRIMARY KEY (run_id, sequence),
    CONSTRAINT ck_cf_durable_journal_sequence CHECK (sequence > 0),
    CONSTRAINT ck_cf_durable_journal_type CHECK (fact_type ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_cf_durable_journal_payload CHECK (
        fact_envelope IS NULL OR (
            octet_length(fact_envelope) BETWEEN 5 AND 4194304
            AND substring(fact_envelope FROM 1 FOR 3) = decode('434644', 'hex')))
);

CREATE INDEX idx_cf_durable_journal_time
    ON public.cf_durable_journal(occurred_at, run_id, sequence);

CREATE FUNCTION public.cf_durable_journal_reject_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'CompileFlow Journal facts are append-only';
END;
$$;

CREATE TRIGGER trg_cf_durable_journal_append_only
BEFORE UPDATE ON public.cf_durable_journal
FOR EACH ROW EXECUTE FUNCTION public.cf_durable_journal_reject_update();

-- Closed Kernel Integration Event set.  event_id is both the row identity and the sink idempotency
-- identity.  There is deliberately no foreign key to Journal, because their retention differs.
CREATE TABLE public.cf_durable_outbox (
    event_id               uuid PRIMARY KEY,
    run_id                 uuid NOT NULL REFERENCES public.cf_durable_run(run_id)
                           ON UPDATE RESTRICT ON DELETE RESTRICT,
    event_type             varchar(32) COLLATE "C" NOT NULL,
    occurrence_id          uuid,
    payload_envelope       bytea NOT NULL,
    status                 varchar(12) COLLATE "C" NOT NULL DEFAULT 'PENDING',
    attempt_count          integer NOT NULL DEFAULT 0,
    revision               bigint NOT NULL DEFAULT 0,
    available_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    lease_owner            varchar(128) COLLATE "C",
    lease_token            uuid,
    lease_until            timestamptz,
    created_at             timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at           timestamptz,
    CONSTRAINT ck_cf_durable_outbox_type CHECK (event_type IN (
        'WAIT_COMMITTED', 'EFFECT_REVIEW_REQUIRED',
        'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED')),
    CONSTRAINT ck_cf_durable_outbox_occurrence CHECK (
        (event_type IN ('WAIT_COMMITTED', 'EFFECT_REVIEW_REQUIRED') AND occurrence_id IS NOT NULL)
        OR (event_type IN ('RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED') AND occurrence_id IS NULL)),
    CONSTRAINT ck_cf_durable_outbox_status CHECK (status IN (
        'PENDING', 'DELIVERING', 'DELIVERED', 'ABANDONED')),
    CONSTRAINT ck_cf_durable_outbox_attempts CHECK (attempt_count >= 0 AND revision >= 0),
    CONSTRAINT ck_cf_durable_outbox_payload CHECK (
        octet_length(payload_envelope) BETWEEN 5 AND 4194304
        AND substring(payload_envelope FROM 1 FOR 3) = decode('434644', 'hex')),
    CONSTRAINT ck_cf_durable_outbox_lease CHECK (
        (status = 'DELIVERING' AND lease_owner IS NOT NULL
         AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'DELIVERING' AND lease_owner IS NULL
            AND lease_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_cf_durable_outbox_terminal CHECK (
        (status IN ('DELIVERED', 'ABANDONED') AND completed_at IS NOT NULL)
        OR (status NOT IN ('DELIVERED', 'ABANDONED') AND completed_at IS NULL)),
    CONSTRAINT ck_cf_durable_outbox_lifetime CHECK (
        completed_at IS NULL OR completed_at >= created_at)
);

CREATE INDEX idx_cf_durable_outbox_dispatch
    ON public.cf_durable_outbox(available_at, created_at, event_id) WHERE status = 'PENDING';
CREATE INDEX idx_cf_durable_outbox_expired_lease
    ON public.cf_durable_outbox(lease_until, event_id) WHERE status = 'DELIVERING';
CREATE INDEX idx_cf_durable_outbox_operator
    ON public.cf_durable_outbox(status, created_at DESC, event_id DESC);
CREATE INDEX idx_cf_durable_outbox_recent
    ON public.cf_durable_outbox(created_at DESC, event_id DESC);
CREATE INDEX idx_cf_durable_outbox_occurrence
    ON public.cf_durable_outbox(run_id, event_type, occurrence_id);
