-- CompileFlow 2.0 Durable Kernel -- MySQL greenfield schema.
--
-- MySQL V1 maps the Durable authority categories to seven tables; the table count is an
-- implementation layout, not a Kernel invariant. Durable truth is restricted to immutable stored
-- Process semantics, the Run continuation snapshot, committed execution facts, semantic
-- Journal facts and the closed Kernel Integration Event set.  Generated code, application/provider
-- identity, aliases, ABI/codec identity columns, worker presence and compatibility metadata never enter it.
-- Engine-owned payload bytes carry only the compact CFD + format-version header.
-- All authority timestamps are produced by MySQL.
-- DATETIME authority values are UTC. The Store scopes session time_zone to +00:00 and
-- uses explicit UTC JDBC calendars, independently of application and connection defaults.

CREATE TABLE cf_durable_process (
    process_id            char(36) PRIMARY KEY,
    process_code          varchar(128) NOT NULL,
    model_type            varchar(16) NOT NULL,
    definition_bytes      longblob NOT NULL,
    definition_digest     char(64) NOT NULL,
    registered_at         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_cf_durable_process_code CHECK (
        process_code <> '' AND TRIM(process_code) = process_code),
    CONSTRAINT ck_cf_durable_process_model_type CHECK (model_type IN ('TBBPM', 'BPMN')),
    CONSTRAINT ck_cf_durable_process_digest CHECK (
        REGEXP_LIKE(definition_digest, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_cf_durable_process_definition CHECK (
        octet_length(definition_bytes) BETWEEN 1 AND 4194304),
    CONSTRAINT uq_cf_durable_process_definition_digest UNIQUE (definition_digest),
    CONSTRAINT uq_cf_durable_process_identity UNIQUE (process_id, process_code)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TRIGGER trg_cf_durable_process_immutable
BEFORE UPDATE ON cf_durable_process
FOR EACH ROW SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'CompileFlow Durable Process definitions are immutable and cannot be updated';

CREATE TABLE cf_durable_run (
    run_id                    char(36) PRIMARY KEY,
    process_id                char(36) NOT NULL,
    process_code              varchar(128) NOT NULL,
    namespace                 varchar(128) NOT NULL,
    process_version           varchar(64),

    status                    varchar(16) NOT NULL DEFAULT 'RUNNABLE',
    control_state             varchar(24) NOT NULL DEFAULT 'ACTIVE',
    control_revision          bigint NOT NULL DEFAULT 0,
    available_at              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- Opaque Engine-owned continuation. SQL never interprets model coordinates or frontiers.
    continuation_envelope     longblob NOT NULL,
    occurrence_sequence       bigint NOT NULL DEFAULT 0,
    turn_fault_streak         integer NOT NULL DEFAULT 0,
    retry_code                varchar(64),
    retry_observed_at         datetime(3),

    cancel_requested_at       datetime(3),

    lease_owner               varchar(128),
    lease_token               char(36),
    lease_until               datetime(3),

    result_envelope           longblob,
    failure_code              varchar(128),
    failure_message           varchar(2048),
    created_at                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at              datetime(3),

    CONSTRAINT fk_cf_durable_run_root_process FOREIGN KEY (process_id, process_code)
        REFERENCES cf_durable_process(process_id, process_code)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_cf_durable_run_process_identity CHECK (
        namespace <> '' AND TRIM(namespace) = namespace
        AND process_code <> '' AND TRIM(process_code) = process_code
        AND (process_version IS NULL
             OR (process_version <> '' AND TRIM(process_version) = process_version))),
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
        AND SUBSTRING(continuation_envelope, 1, 3) = X'434644'),
    CONSTRAINT ck_cf_durable_run_result CHECK (
        result_envelope IS NULL OR (
            octet_length(result_envelope) BETWEEN 5 AND 4194304
            AND SUBSTRING(result_envelope, 1, 3) = X'434644')),
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
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE INDEX idx_cf_durable_run_dispatch
    ON cf_durable_run(status, control_state, cancel_requested_at, available_at, created_at, run_id);
CREATE INDEX idx_cf_durable_run_expired_lease
    ON cf_durable_run(status, lease_until, run_id);
CREATE INDEX idx_cf_durable_run_recent
    ON cf_durable_run(created_at DESC, run_id DESC);
CREATE INDEX idx_cf_durable_run_namespace_recent
    ON cf_durable_run(namespace, created_at DESC, run_id DESC);
CREATE INDEX idx_cf_durable_run_process_recent
    ON cf_durable_run(namespace, process_code, created_at DESC, run_id DESC);
CREATE INDEX idx_cf_durable_run_retention
    ON cf_durable_run(status, completed_at, run_id);

-- SQL cannot inspect the opaque continuation, so each Run records the exact stored Processes its
-- continuation may enter.  These rows are reachability references, not a second execution graph.
CREATE TABLE cf_durable_run_recovery_process (
    run_id       char(36) NOT NULL,
    process_id   char(36) NOT NULL,
    PRIMARY KEY (run_id, process_id),
    CONSTRAINT fk_cf_durable_recovery_run FOREIGN KEY (run_id)
        REFERENCES cf_durable_run(run_id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_cf_durable_recovery_process FOREIGN KEY (process_id)
        REFERENCES cf_durable_process(process_id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE INDEX idx_cf_durable_run_recovery_process_process_id
    ON cf_durable_run_recovery_process(process_id, run_id);

-- EXTERNAL and TIMER are one occurrence state machine. Only their readiness predicate differs;
-- status-leading indexes preserve the different polling paths without duplicating lifecycle code.
CREATE TABLE cf_durable_wait (
    wait_id               char(36) PRIMARY KEY,
    run_id                char(36) NOT NULL,
    occurrence_sequence   bigint NOT NULL,
    process_id            char(36) NOT NULL,
    process_invocation_id bigint NOT NULL,
    frontier_id           varchar(128) NOT NULL,
    element_id            varchar(128) NOT NULL,
    kind                  varchar(8) NOT NULL,
    event_name            varchar(512),
    token_digest          char(64),
    due_at                datetime(3),
    status                varchar(12) NOT NULL DEFAULT 'ACTIVE',
    resolution_kind       varchar(10),
    result_envelope       longblob,
    created_at            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at           datetime(3),
    consumed_at           datetime(3),
    CONSTRAINT uq_cf_durable_wait_position UNIQUE (run_id, occurrence_sequence),
    CONSTRAINT fk_cf_durable_wait_run FOREIGN KEY (run_id)
        REFERENCES cf_durable_run(run_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_cf_durable_wait_process FOREIGN KEY (process_id)
        REFERENCES cf_durable_process(process_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_cf_durable_wait_recovery_process FOREIGN KEY (run_id, process_id)
        REFERENCES cf_durable_run_recovery_process(run_id, process_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_cf_durable_wait_sequence CHECK (occurrence_sequence > 0),
    CONSTRAINT ck_cf_durable_wait_invocation CHECK (process_invocation_id >= 0),
    CONSTRAINT ck_cf_durable_wait_frontier CHECK (frontier_id <> ''),
    CONSTRAINT ck_cf_durable_wait_kind CHECK (kind IN ('EXTERNAL', 'TIMER')),
    CONSTRAINT ck_cf_durable_wait_status CHECK (status IN ('ACTIVE', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_cf_durable_wait_shape CHECK (
        (kind = 'EXTERNAL' AND (event_name IS NULL OR event_name <> '')
         AND token_digest IS NOT NULL
         AND REGEXP_LIKE(token_digest, '^[0-9a-f]{64}$', 'c'))
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
            AND SUBSTRING(result_envelope, 1, 3) = X'434644')),
    CONSTRAINT ck_cf_durable_wait_lifetime CHECK (
        (resolved_at IS NULL OR resolved_at >= created_at)
        -- Absolute Timers may already be due when scheduled; relative Wait deadlines may not.
        AND (kind = 'TIMER' OR due_at IS NULL OR due_at >= created_at)
        AND (resolution_kind <> 'EXPIRED' OR (due_at IS NOT NULL AND resolved_at >= due_at))
        AND (resolution_kind <> 'FIRED' OR (due_at IS NOT NULL AND resolved_at >= due_at))
        AND (consumed_at IS NULL
             OR (status = 'RESOLVED' AND consumed_at >= resolved_at)))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE UNIQUE INDEX uq_cf_durable_wait_token
    ON cf_durable_wait(token_digest);
CREATE INDEX idx_cf_durable_wait_active_run
    ON cf_durable_wait(status, run_id, frontier_id, occurrence_sequence);
CREATE INDEX idx_cf_durable_wait_due
    ON cf_durable_wait(status, due_at, wait_id);
CREATE INDEX idx_cf_durable_wait_resolved_unconsumed
    ON cf_durable_wait(status, consumed_at, run_id, frontier_id, occurrence_sequence);
CREATE INDEX idx_cf_durable_wait_consumed_retention
    ON cf_durable_wait(consumed_at, wait_id);

CREATE TABLE cf_durable_effect (
    effect_id              char(36) PRIMARY KEY,
    run_id                 char(36) NOT NULL,
    occurrence_sequence    bigint NOT NULL,
    process_id             char(36) NOT NULL,
    process_invocation_id  bigint NOT NULL,
    frontier_id            varchar(128) NOT NULL,
    element_id             varchar(128) NOT NULL,
    recovery_mode          varchar(12) NOT NULL,
    max_attempts           integer NOT NULL,
    max_reconcile_attempts integer NOT NULL,
    retry_delay_ms         bigint,
    recovery_deadline_ms   bigint,
    input_envelope         longblob NOT NULL,
    result_envelope        longblob,
    status                 varchar(12) NOT NULL DEFAULT 'PENDING',
    running_operation      varchar(12),
    dispatch_attempts      integer NOT NULL DEFAULT 0,
    reconcile_attempts     integer NOT NULL DEFAULT 0,
    available_at           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    lease_owner            varchar(128),
    lease_token            char(36),
    lease_until            datetime(3),
    unknown_since          datetime(3),
    readiness_code         varchar(64),
    review_required_at     datetime(3),
    review_reason          varchar(2048),
    review_revision        bigint NOT NULL DEFAULT 0,
    created_at             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at           datetime(3),
    consumed_at            datetime(3),
    CONSTRAINT uq_cf_durable_effect_position UNIQUE (run_id, occurrence_sequence),
    CONSTRAINT fk_cf_durable_effect_run FOREIGN KEY (run_id)
        REFERENCES cf_durable_run(run_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_cf_durable_effect_process FOREIGN KEY (process_id)
        REFERENCES cf_durable_process(process_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_cf_durable_effect_recovery_process FOREIGN KEY (run_id, process_id)
        REFERENCES cf_durable_run_recovery_process(run_id, process_id)
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
        AND SUBSTRING(input_envelope, 1, 3) = X'434644'
        AND (result_envelope IS NULL OR (
            octet_length(result_envelope) BETWEEN 5 AND 4194304
            AND SUBSTRING(result_envelope, 1, 3) = X'434644'))),
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
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE INDEX idx_cf_durable_effect_active_run
    ON cf_durable_effect(status, run_id, frontier_id, occurrence_sequence);
CREATE INDEX idx_cf_durable_effect_pending
    ON cf_durable_effect(status, available_at, created_at, effect_id);
CREATE INDEX idx_cf_durable_effect_unknown
    ON cf_durable_effect(status, review_required_at, available_at, created_at, effect_id);
CREATE INDEX idx_cf_durable_effect_expired_lease
    ON cf_durable_effect(status, lease_until, effect_id);
CREATE INDEX idx_cf_durable_effect_review
    ON cf_durable_effect(status, review_required_at, effect_id);
CREATE INDEX idx_cf_durable_effect_completed_unconsumed
    ON cf_durable_effect(status, consumed_at, run_id, frontier_id, occurrence_sequence);
CREATE INDEX idx_cf_durable_effect_consumed_retention
    ON cf_durable_effect(consumed_at, effect_id);

-- Audit/diagnostic facts only.  Journal is not a recovery source and is not mirrored 1:1 to Outbox.
CREATE TABLE cf_durable_journal (
    run_id                 char(36) NOT NULL,
    sequence               bigint NOT NULL,
    fact_type              varchar(64) NOT NULL,
    fact_envelope          longblob,
    occurred_at            datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_cf_durable_journal PRIMARY KEY (run_id, sequence),
    CONSTRAINT fk_cf_durable_journal_run FOREIGN KEY (run_id)
        REFERENCES cf_durable_run(run_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_cf_durable_journal_sequence CHECK (sequence > 0),
    CONSTRAINT ck_cf_durable_journal_type CHECK (
        REGEXP_LIKE(fact_type, '^[A-Z][A-Z0-9_]*$', 'c')),
    CONSTRAINT ck_cf_durable_journal_payload CHECK (
        fact_envelope IS NULL OR (
            octet_length(fact_envelope) BETWEEN 5 AND 4194304
            AND SUBSTRING(fact_envelope, 1, 3) = X'434644'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE INDEX idx_cf_durable_journal_time
    ON cf_durable_journal(occurred_at, run_id, sequence);

CREATE TRIGGER trg_cf_durable_journal_append_only
BEFORE UPDATE ON cf_durable_journal
FOR EACH ROW SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'CompileFlow Journal facts are append-only';

-- Closed Kernel Integration Event set.  event_id is both the row identity and the sink idempotency
-- identity.  There is deliberately no foreign key to Journal, because their retention differs.
CREATE TABLE cf_durable_outbox (
    event_id               char(36) PRIMARY KEY,
    run_id                 char(36) NOT NULL,
    event_type             varchar(32) NOT NULL,
    occurrence_id          char(36),
    payload_envelope       longblob NOT NULL,
    status                 varchar(12) NOT NULL DEFAULT 'PENDING',
    attempt_count          integer NOT NULL DEFAULT 0,
    revision               bigint NOT NULL DEFAULT 0,
    available_at           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    lease_owner            varchar(128),
    lease_token            char(36),
    lease_until            datetime(3),
    created_at             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at           datetime(3),
    CONSTRAINT fk_cf_durable_outbox_run FOREIGN KEY (run_id)
        REFERENCES cf_durable_run(run_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
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
        AND SUBSTRING(payload_envelope, 1, 3) = X'434644'),
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
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE INDEX idx_cf_durable_outbox_dispatch
    ON cf_durable_outbox(status, available_at, created_at, event_id);
CREATE INDEX idx_cf_durable_outbox_expired_lease
    ON cf_durable_outbox(status, lease_until, event_id);
CREATE INDEX idx_cf_durable_outbox_operator
    ON cf_durable_outbox(status, created_at DESC, event_id DESC);
CREATE INDEX idx_cf_durable_outbox_recent
    ON cf_durable_outbox(created_at DESC, event_id DESC);
CREATE INDEX idx_cf_durable_outbox_occurrence
    ON cf_durable_outbox(run_id, event_type, occurrence_id);
