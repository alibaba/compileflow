-- CompileFlow Workbench Server baseline schema.
--
-- Deploy tables are owned by compileflow-deploy-control-plane's V1 migration.

CREATE TABLE cf_process_draft
(
    code        VARCHAR(128)             NOT NULL PRIMARY KEY,
    name        VARCHAR(256)             NOT NULL,
    type        VARCHAR(32)              NOT NULL,
    xml         TEXT                     NOT NULL,
    description VARCHAR(1024),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(128)             NOT NULL,
    tags_json   TEXT                     NOT NULL,
    revision    BIGINT                   NOT NULL,
    CONSTRAINT ck_process_draft_type CHECK (type IN ('TBBPM', 'BPMN')),
    CONSTRAINT ck_process_draft_revision CHECK (revision >= 0),
    CONSTRAINT ck_process_draft_timestamps CHECK (updated_at >= created_at)
);
CREATE INDEX idx_process_draft_updated ON cf_process_draft (updated_at);

CREATE TABLE cf_execution_log
(
    id                   VARCHAR(128) NOT NULL PRIMARY KEY,
    process_code         VARCHAR(128) NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    duration_ms          BIGINT       NOT NULL,
    error_code           VARCHAR(128),
    error_message        VARCHAR(4096),
    invocation_id        VARCHAR(128) NOT NULL,
    parent_invocation_id VARCHAR(128),
    call_depth           INT          NOT NULL,
    trace_id             VARCHAR(128) NOT NULL,
    namespace            VARCHAR(128) NOT NULL,
    model_type           VARCHAR(32)  NOT NULL,
    source_digest        VARCHAR(64),
    requested_version    VARCHAR(64),
    effective_version    VARCHAR(64),
    routing_source       VARCHAR(32),
    route_alias          VARCHAR(64),
    route_revision       BIGINT,
    logged_at            BIGINT       NOT NULL,
    CONSTRAINT ck_exec_log_status CHECK (status IN ('success', 'failed')),
    CONSTRAINT ck_exec_log_model_type CHECK (model_type IN ('TBBPM', 'BPMN')),
    CONSTRAINT ck_exec_log_source_digest CHECK (
        source_digest IS NULL OR source_digest ~ '^[0-9a-f]{64}$'
) ,
    CONSTRAINT ck_exec_log_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_exec_log_call_tree CHECK (
        (call_depth = 0 AND parent_invocation_id IS NULL)
        OR (call_depth > 0 AND parent_invocation_id IS NOT NULL)
    ),
    CONSTRAINT ck_exec_log_error CHECK (
        (status = 'success' AND error_code IS NULL AND error_message IS NULL)
        OR (status = 'failed' AND error_code IS NOT NULL AND error_message IS NOT NULL)
    ),
    CONSTRAINT ck_exec_log_logged_at CHECK (logged_at > 0),
    CONSTRAINT ck_exec_log_route_attribution CHECK (
        (route_alias IS NULL AND route_revision IS NULL)
        OR (route_alias IS NOT NULL AND route_revision IS NOT NULL AND route_revision > 0)
    )
);
CREATE INDEX idx_exec_log_ts ON cf_execution_log (logged_at, id);
CREATE INDEX idx_exec_log_effective_version ON cf_execution_log (effective_version);
CREATE INDEX idx_exec_log_process_ts ON cf_execution_log (process_code, logged_at);
CREATE INDEX idx_exec_log_trace_depth
    ON cf_execution_log (trace_id, call_depth, logged_at);
CREATE INDEX idx_exec_log_parent
    ON cf_execution_log (parent_invocation_id, logged_at);
CREATE INDEX idx_exec_log_route_ts
    ON cf_execution_log (namespace, process_code, route_alias, logged_at);

CREATE TABLE cf_async_invocation
(
    invocation_id  VARCHAR(128) NOT NULL PRIMARY KEY,
    process_code      VARCHAR(128) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    attempts       INT          NOT NULL,
    total_attempts BIGINT       NOT NULL,
    redrive_count  INT          NOT NULL,
    max_attempts   INT          NOT NULL,
    retry_delay_ms BIGINT       NOT NULL,
    available_at   BIGINT       NOT NULL,
    params_json    TEXT         NOT NULL,
    routing_json   TEXT         NOT NULL,
    result_json    TEXT,
    error_code     VARCHAR(128),
    error_message  VARCHAR(4096),
    trace_id       VARCHAR(128),
    lease_token    VARCHAR(128),
    lease_until    BIGINT,
    duration_ms    BIGINT,
    created_at     BIGINT       NOT NULL,
    updated_at     BIGINT       NOT NULL,
    started_at     BIGINT,
    completed_at   BIGINT,
    CONSTRAINT ck_async_invocation_status CHECK (
        status IN ('queued', 'running', 'succeeded', 'dead_letter')
        ),
    CONSTRAINT ck_async_invocation_retry_policy CHECK (
        attempts >= 0
            AND total_attempts >= attempts
            AND redrive_count >= 0
            AND max_attempts BETWEEN 1 AND 100
            AND attempts <= max_attempts
            AND retry_delay_ms BETWEEN 0 AND 604800000
        ),
    CONSTRAINT ck_async_invocation_lease_state CHECK (
        (status = 'running' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
            OR (status <> 'running' AND lease_token IS NULL AND lease_until IS NULL)
        ),
    CONSTRAINT ck_async_invocation_attempt_state CHECK (
        (attempts = 0 AND started_at IS NULL)
            OR (attempts > 0 AND started_at IS NOT NULL)
        ),
    CONSTRAINT ck_async_invocation_lifecycle CHECK (
        status = 'queued'
            OR (status IN ('running', 'succeeded', 'dead_letter') AND attempts > 0)
        ),
    CONSTRAINT ck_async_invocation_completion_state CHECK (
        (status IN ('queued', 'running') AND completed_at IS NULL)
            OR (status IN ('succeeded', 'dead_letter') AND completed_at IS NOT NULL)
        ),
    CONSTRAINT ck_async_invocation_duration CHECK (
        duration_ms IS NULL OR duration_ms >= 0
        ),
    CONSTRAINT ck_async_invocation_error CHECK (
        ((error_code IS NULL AND error_message IS NULL)
            OR (error_code IS NOT NULL AND error_message IS NOT NULL))
            AND (status <> 'succeeded' OR error_code IS NULL)
            AND (status <> 'dead_letter' OR error_code IS NOT NULL)
        ),
    CONSTRAINT ck_async_invocation_timestamps CHECK (
        created_at > 0
            AND available_at >= created_at
            AND updated_at >= created_at
            AND (started_at IS NULL OR started_at >= created_at)
            AND (completed_at IS NULL OR (started_at IS NOT NULL
            AND completed_at >= started_at AND completed_at >= updated_at))
        )
);
CREATE INDEX idx_async_invocation_created ON cf_async_invocation (created_at);
CREATE INDEX idx_async_invocation_status_available
    ON cf_async_invocation (status, available_at, created_at);
CREATE INDEX idx_async_invocation_status_lease
    ON cf_async_invocation (status, lease_until);
CREATE INDEX idx_async_invocation_process_created
    ON cf_async_invocation (process_code, created_at);
CREATE INDEX idx_async_invocation_process_status_created
    ON cf_async_invocation (process_code, status, created_at);

CREATE TABLE cf_async_invocation_attempt
(
    attempt_id      VARCHAR(128) NOT NULL PRIMARY KEY,
    invocation_id   VARCHAR(128) NOT NULL,
    sequence_no     BIGINT       NOT NULL,
    redrive_count   INT          NOT NULL,
    attempt_number  INT          NOT NULL,
    worker_id       VARCHAR(128) NOT NULL,
    lease_token     VARCHAR(128) NOT NULL,
    outcome         VARCHAR(32)  NOT NULL,
    disposition     VARCHAR(32),
    trace_id        VARCHAR(128),
    error_code      VARCHAR(128),
    error_message   VARCHAR(4096),
    duration_ms     BIGINT,
    started_at      BIGINT       NOT NULL,
    finished_at     BIGINT,
    next_attempt_at BIGINT,
    CONSTRAINT fk_async_attempt_invocation FOREIGN KEY (invocation_id)
        REFERENCES cf_async_invocation (invocation_id) ON DELETE CASCADE,
    CONSTRAINT ck_async_attempt_identity CHECK (
        sequence_no > 0
            AND redrive_count >= 0
            AND attempt_number BETWEEN 1 AND 100
        ),
    CONSTRAINT ck_async_attempt_duration CHECK (
        duration_ms IS NULL OR duration_ms >= 0
        ),
    CONSTRAINT ck_async_attempt_timestamps CHECK (
        started_at > 0
            AND (finished_at IS NULL OR finished_at >= started_at)
            AND (next_attempt_at IS NULL OR next_attempt_at >= finished_at)
        ),
    CONSTRAINT ck_async_attempt_state CHECK (
        (
            outcome = 'running'
                AND disposition IS NULL
                AND trace_id IS NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND duration_ms IS NULL
                AND finished_at IS NULL
                AND next_attempt_at IS NULL
            )
            OR (
            outcome = 'succeeded'
                AND disposition = 'succeeded'
                AND trace_id IS NOT NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND duration_ms IS NOT NULL
                AND finished_at IS NOT NULL
                AND next_attempt_at IS NULL
            )
            OR (
            outcome IN ('failed', 'lease_expired')
                AND disposition IN ('retry_scheduled', 'dead_lettered')
                AND error_code IS NOT NULL
                AND error_message IS NOT NULL
                AND finished_at IS NOT NULL
                AND (
                (disposition = 'retry_scheduled' AND next_attempt_at IS NOT NULL)
                    OR (disposition = 'dead_lettered' AND next_attempt_at IS NULL)
                )
            )
        )
);
CREATE UNIQUE INDEX uq_async_attempt_invocation_sequence
    ON cf_async_invocation_attempt (invocation_id, sequence_no);
CREATE UNIQUE INDEX uq_async_attempt_redrive_number
    ON cf_async_invocation_attempt (invocation_id, redrive_count, attempt_number);
CREATE UNIQUE INDEX uq_async_attempt_lease_token
    ON cf_async_invocation_attempt (lease_token);
CREATE INDEX idx_async_attempt_outcome_finished
    ON cf_async_invocation_attempt (outcome, finished_at);
