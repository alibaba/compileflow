-- H2 adapter for Deploy repository contract tests.
--
-- Production authority remains V1__deploy_baseline.sql in src/main/resources. Keep this file
-- limited to syntax differences required by H2.

CREATE TABLE IF NOT EXISTS cf_process_version
(
    namespace    VARCHAR(128) NOT NULL DEFAULT 'default',
    code         VARCHAR(128) NOT NULL,
    version      VARCHAR(64)  NOT NULL,
    model_type   VARCHAR(16)  NOT NULL,
    content      CLOB         NOT NULL,
    digest       CHAR(64)     NOT NULL,
    call_bindings CLOB        NOT NULL DEFAULT '[]',
    metadata     CLOB,
    actor        VARCHAR(128) NOT NULL,
    created_at   BIGINT       NOT NULL,
    PRIMARY KEY (namespace, code, version),
    CONSTRAINT ck_process_version_model_type CHECK (
        model_type IN ('TBBPM', 'BPMN')
        ),
    CONSTRAINT ck_process_version_digest CHECK (
        REGEXP_LIKE(digest, '^[0-9a-f]{64}$')
),
    CONSTRAINT ck_process_version_created_at CHECK (created_at > 0)
);
CREATE INDEX IF NOT EXISTS idx_process_version_created
    ON cf_process_version (namespace, code, created_at DESC, version DESC);

CREATE TABLE IF NOT EXISTS cf_process_alias
(
    namespace            VARCHAR(128) NOT NULL DEFAULT 'default',
    code                 VARCHAR(128) NOT NULL,
    alias                VARCHAR(64)  NOT NULL,
    stable_version       VARCHAR(64)  NOT NULL,
    candidate_version    VARCHAR(64),
    candidate_weight_bps INT,
    targeting_policy     VARCHAR(256),
    targeting_parameters VARCHAR(262144) NOT NULL DEFAULT '{}',
    revision             BIGINT       NOT NULL,
    created_by           VARCHAR(128) NOT NULL,
    updated_by           VARCHAR(128) NOT NULL,
    created_at           BIGINT       NOT NULL,
    updated_at           BIGINT       NOT NULL,
    PRIMARY KEY (namespace, code, alias),
    CONSTRAINT fk_alias_stable_version FOREIGN KEY (namespace, code, stable_version)
        REFERENCES cf_process_version (namespace, code, version),
    CONSTRAINT fk_alias_candidate_version FOREIGN KEY (namespace, code, candidate_version)
        REFERENCES cf_process_version (namespace, code, version),
    CONSTRAINT ck_alias_candidate CHECK (
        (candidate_version IS NULL AND candidate_weight_bps IS NULL
            AND targeting_policy IS NULL AND targeting_parameters = '{}')
            OR (candidate_version IS NOT NULL AND candidate_weight_bps BETWEEN 1 AND 9999
            AND candidate_version <> stable_version
            AND (targeting_policy IS NOT NULL OR targeting_parameters = '{}'))
        ),
    CONSTRAINT ck_alias_revision CHECK (revision > 0),
    CONSTRAINT ck_alias_timestamps CHECK (
        created_at > 0 AND updated_at >= created_at
        )
);
CREATE INDEX IF NOT EXISTS idx_process_alias_process
    ON cf_process_alias (namespace, code);

CREATE TABLE IF NOT EXISTS cf_rollout
(
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    namespace           VARCHAR(128) NOT NULL DEFAULT 'default',
    code                VARCHAR(128) NOT NULL,
    alias               VARCHAR(64)  NOT NULL,
    baseline_version    VARCHAR(64),
    target_version      VARCHAR(64)  NOT NULL,
    base_alias_revision BIGINT       NOT NULL,
    alias_revision      BIGINT       NOT NULL,
    strategy            VARCHAR(32)  NOT NULL,
    phase               VARCHAR(32)  NOT NULL,
    canary_weight_bps   INT          NOT NULL,
    targeting_policy    VARCHAR(256),
    targeting_parameters VARCHAR(262144) NOT NULL DEFAULT '{}',
    revision            BIGINT       NOT NULL,
    active_marker       INT,
    idempotency_key     VARCHAR(128) NOT NULL,
    operation_kind      VARCHAR(16)  NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    created_by          VARCHAR(128) NOT NULL,
    notes               VARCHAR(2048),
    created_at          BIGINT       NOT NULL,
    updated_at          BIGINT       NOT NULL,
    completed_at        BIGINT,
    CONSTRAINT uq_rollout_idempotency UNIQUE (
                                              namespace, code, alias, operation_kind, idempotency_key
        ),
    CONSTRAINT fk_rollout_target_version FOREIGN KEY (namespace, code, target_version)
        REFERENCES cf_process_version (namespace, code, version),
    CONSTRAINT fk_rollout_baseline_version FOREIGN KEY (namespace, code, baseline_version)
        REFERENCES cf_process_version (namespace, code, version),
    CONSTRAINT ck_rollout_strategy CHECK (strategy IN ('ALL_AT_ONCE', 'CANARY')),
    CONSTRAINT ck_rollout_operation_kind CHECK (
        operation_kind IN ('DEPLOY', 'ROLLBACK')
        ),
    CONSTRAINT ck_rollout_phase CHECK (
        phase IN ('IN_PROGRESS', 'COMPLETED', 'ABORTED')
        ),
    CONSTRAINT ck_rollout_canary_weight_bps CHECK (
        canary_weight_bps >= 0 AND canary_weight_bps <= 10000
        ),
    CONSTRAINT ck_rollout_revision CHECK (revision > 0),
    CONSTRAINT ck_rollout_alias_revision CHECK (
        base_alias_revision >= 0 AND alias_revision > base_alias_revision
        ),
    CONSTRAINT ck_rollout_active_marker CHECK (
        (phase = 'IN_PROGRESS' AND active_marker = 1)
            OR (phase <> 'IN_PROGRESS' AND active_marker IS NULL)
        ),
    CONSTRAINT ck_rollout_lifecycle CHECK (
        (phase = 'IN_PROGRESS' AND strategy = 'CANARY'
            AND canary_weight_bps BETWEEN 1 AND 9999 AND completed_at IS NULL)
            OR (phase = 'COMPLETED' AND canary_weight_bps = 10000
            AND completed_at IS NOT NULL)
            OR (phase = 'ABORTED' AND strategy = 'CANARY'
            AND canary_weight_bps = 0 AND completed_at IS NOT NULL)
        ),
    CONSTRAINT ck_rollout_canary_baseline CHECK (
        strategy <> 'CANARY'
            OR (baseline_version IS NOT NULL AND baseline_version <> target_version)
        ),
    CONSTRAINT ck_rollout_targeting CHECK (
        (targeting_policy IS NULL AND targeting_parameters = '{}')
            OR (strategy = 'CANARY' AND targeting_policy IS NOT NULL)
        ),
    CONSTRAINT ck_rollout_rollback_shape CHECK (
        operation_kind <> 'ROLLBACK'
            OR (strategy = 'ALL_AT_ONCE' AND phase = 'COMPLETED')
        ),
    CONSTRAINT ck_rollout_timestamps CHECK (
        created_at > 0 AND updated_at >= created_at
            AND (completed_at IS NULL
            OR completed_at BETWEEN created_at AND updated_at)
        )
);
CREATE INDEX IF NOT EXISTS idx_rollout_process_created
    ON cf_rollout (namespace, code, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_rollout_alias_created
    ON cf_rollout (namespace, code, alias, created_at DESC, id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rollout_active_alias
    ON cf_rollout (namespace, code, alias, active_marker);

CREATE TABLE IF NOT EXISTS cf_rollout_event
(
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    rollout_id VARCHAR(64)  NOT NULL,
    sequence   BIGINT       NOT NULL,
    event_type VARCHAR(64)  NOT NULL,
    from_phase VARCHAR(32),
    to_phase   VARCHAR(32)  NOT NULL,
    actor      VARCHAR(128) NOT NULL,
    reason     VARCHAR(2048),
    created_at BIGINT       NOT NULL,
    CONSTRAINT uq_rollout_event_sequence UNIQUE (rollout_id, sequence),
    CONSTRAINT fk_rollout_event_rollout FOREIGN KEY (rollout_id) REFERENCES cf_rollout (id),
    CONSTRAINT ck_rollout_event_sequence CHECK (sequence > 0),
    CONSTRAINT ck_rollout_event_type CHECK (
        REGEXP_LIKE(event_type, '^[A-Z][A-Z0-9_]*$')
),
    CONSTRAINT ck_rollout_event_transition CHECK (
        (sequence = 1 AND from_phase IS NULL AND to_phase IN ('IN_PROGRESS', 'COMPLETED'))
        OR (sequence > 1 AND from_phase = 'IN_PROGRESS')
    ),
    CONSTRAINT ck_rollout_event_to_phase CHECK (
        to_phase IN ('IN_PROGRESS', 'COMPLETED', 'ABORTED')
    ),
    CONSTRAINT ck_rollout_event_created_at CHECK (created_at > 0)
);
CREATE INDEX IF NOT EXISTS idx_rollout_event_created
    ON cf_rollout_event (rollout_id, created_at);



CREATE TABLE IF NOT EXISTS cf_routing_outbox
(
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    delivery_key    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    namespace       VARCHAR(128) NOT NULL,
    code            VARCHAR(128) NOT NULL,
    alias           VARCHAR(64)  NOT NULL,
    routing_key     VARCHAR(512) NOT NULL,
    payload         CLOB         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at BIGINT,
    last_error      CLOB,
    lease_token     VARCHAR(128),
    lease_until     BIGINT,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (
        status = 'PENDING' OR status = 'PROCESSING'
            OR status = 'DELIVERED' OR status = 'FAILED'
        ),
    CONSTRAINT ck_outbox_event_type CHECK (event_type = 'ALIAS_STATE'),
    CONSTRAINT ck_outbox_delivery_key CHECK (CHAR_LENGTH(delivery_key) = 64),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_delivery_state CHECK (
        (status = 'PENDING' AND lease_token IS NULL AND lease_until IS NULL)
            OR (status = 'PROCESSING' AND next_attempt_at IS NULL
            AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
            OR ((status = 'DELIVERED' OR status = 'FAILED') AND next_attempt_at IS NULL
            AND lease_token IS NULL AND lease_until IS NULL)
        ),
    CONSTRAINT ck_outbox_timestamps CHECK (
        created_at > 0 AND updated_at >= created_at
        )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_delivery_key
    ON cf_routing_outbox (delivery_key);
CREATE INDEX IF NOT EXISTS idx_outbox_status_next
    ON cf_routing_outbox (status, next_attempt_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_lease
    ON cf_routing_outbox (status, lease_until, id);
CREATE INDEX IF NOT EXISTS idx_outbox_delivered_retention
    ON cf_routing_outbox (status, updated_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_process
    ON cf_routing_outbox (namespace, code);
