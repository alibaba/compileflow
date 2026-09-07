-- CompileFlow Deploy schema for MySQL 8.0.
--
-- This migration is the sole production DDL owner for the MySQL deploy provider.
-- utf8mb4_0900_bin keeps every persisted process identity exact and case-sensitive.

CREATE TABLE cf_process_version
(
    namespace     VARCHAR(128) NOT NULL DEFAULT 'default',
    code          VARCHAR(128) NOT NULL,
    version       VARCHAR(64)  NOT NULL,
    model_type    VARCHAR(16)  NOT NULL,
    content       LONGTEXT     NOT NULL,
    digest        CHAR(64)     NOT NULL,
    call_bindings LONGTEXT     NOT NULL,
    metadata      LONGTEXT,
    actor         VARCHAR(128) NOT NULL,
    created_at    BIGINT       NOT NULL,
    PRIMARY KEY (namespace, code, version),
    CONSTRAINT ck_process_version_model_type CHECK (model_type IN ('TBBPM', 'BPMN')),
    CONSTRAINT ck_process_version_digest CHECK (REGEXP_LIKE(digest, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_process_version_created_at CHECK (created_at > 0),
    INDEX idx_process_version_created (namespace, code, created_at DESC, version DESC)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TRIGGER trg_process_version_no_update
    BEFORE UPDATE ON cf_process_version
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cf_process_version is immutable';
CREATE TRIGGER trg_process_version_no_delete
    BEFORE DELETE ON cf_process_version
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cf_process_version is immutable';

CREATE TABLE cf_process_alias
(
    namespace            VARCHAR(128) NOT NULL DEFAULT 'default',
    code                 VARCHAR(128) NOT NULL,
    alias                VARCHAR(64)  NOT NULL,
    stable_version       VARCHAR(64)  NOT NULL,
    candidate_version    VARCHAR(64),
    candidate_weight_bps INT,
    targeting_policy     VARCHAR(256),
    targeting_parameters LONGTEXT     NOT NULL,
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
            OR (candidate_version IS NOT NULL AND candidate_weight_bps IS NOT NULL
            AND candidate_weight_bps BETWEEN 1 AND 9999
            AND candidate_version <> stable_version
            AND (targeting_policy IS NOT NULL OR targeting_parameters = '{}'))
        ),
    CONSTRAINT ck_alias_revision CHECK (revision > 0),
    CONSTRAINT ck_alias_timestamps CHECK (created_at > 0 AND updated_at >= created_at),
    INDEX idx_process_alias_process (namespace, code)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE cf_rollout
(
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    namespace            VARCHAR(128) NOT NULL DEFAULT 'default',
    code                 VARCHAR(128) NOT NULL,
    alias                VARCHAR(64)  NOT NULL,
    baseline_version     VARCHAR(64),
    target_version       VARCHAR(64)  NOT NULL,
    base_alias_revision  BIGINT       NOT NULL,
    alias_revision       BIGINT       NOT NULL,
    strategy             VARCHAR(32)  NOT NULL,
    phase                VARCHAR(32)  NOT NULL,
    canary_weight_bps    INT          NOT NULL,
    targeting_policy     VARCHAR(256),
    targeting_parameters LONGTEXT     NOT NULL,
    revision             BIGINT       NOT NULL,
    active_marker        INT,
    idempotency_key      VARCHAR(128) NOT NULL,
    operation_kind       VARCHAR(16)  NOT NULL,
    request_fingerprint  VARCHAR(64)  NOT NULL,
    created_by           VARCHAR(128) NOT NULL,
    notes                VARCHAR(2048),
    created_at           BIGINT       NOT NULL,
    updated_at           BIGINT       NOT NULL,
    completed_at         BIGINT,
    CONSTRAINT uq_rollout_idempotency UNIQUE
        (namespace, code, alias, operation_kind, idempotency_key),
    CONSTRAINT fk_rollout_target_version FOREIGN KEY (namespace, code, target_version)
        REFERENCES cf_process_version (namespace, code, version),
    CONSTRAINT fk_rollout_baseline_version FOREIGN KEY (namespace, code, baseline_version)
        REFERENCES cf_process_version (namespace, code, version),
    CONSTRAINT ck_rollout_strategy CHECK (strategy IN ('ALL_AT_ONCE', 'CANARY')),
    CONSTRAINT ck_rollout_operation_kind CHECK (operation_kind IN ('DEPLOY', 'ROLLBACK')),
    CONSTRAINT ck_rollout_phase CHECK (phase IN ('IN_PROGRESS', 'COMPLETED', 'ABORTED')),
    CONSTRAINT ck_rollout_canary_weight_bps CHECK (canary_weight_bps BETWEEN 0 AND 10000),
    CONSTRAINT ck_rollout_revision CHECK (revision > 0),
    CONSTRAINT ck_rollout_alias_revision CHECK
        (base_alias_revision >= 0 AND alias_revision > base_alias_revision),
    CONSTRAINT ck_rollout_active_marker CHECK (
        (phase = 'IN_PROGRESS' AND active_marker IS NOT NULL AND active_marker = 1)
            OR (phase <> 'IN_PROGRESS' AND active_marker IS NULL)
        ),
    CONSTRAINT ck_rollout_lifecycle CHECK (
        (phase = 'IN_PROGRESS' AND strategy = 'CANARY'
            AND canary_weight_bps BETWEEN 1 AND 9999 AND completed_at IS NULL)
            OR (phase = 'COMPLETED' AND canary_weight_bps = 10000 AND completed_at IS NOT NULL)
            OR (phase = 'ABORTED' AND strategy = 'CANARY'
            AND canary_weight_bps = 0 AND completed_at IS NOT NULL)
        ),
    CONSTRAINT ck_rollout_canary_baseline CHECK
        (strategy <> 'CANARY' OR (baseline_version IS NOT NULL AND baseline_version <> target_version)),
    CONSTRAINT ck_rollout_targeting CHECK (
        (targeting_policy IS NULL AND targeting_parameters = '{}')
            OR (strategy = 'CANARY' AND targeting_policy IS NOT NULL)
        ),
    CONSTRAINT ck_rollout_rollback_shape CHECK
        (operation_kind <> 'ROLLBACK' OR (strategy = 'ALL_AT_ONCE' AND phase = 'COMPLETED')),
    CONSTRAINT ck_rollout_timestamps CHECK (
        created_at > 0 AND updated_at >= created_at
            AND (completed_at IS NULL OR completed_at BETWEEN created_at AND updated_at)
        ),
    INDEX idx_rollout_process_created (namespace, code, created_at DESC, id DESC),
    INDEX idx_rollout_alias_created (namespace, code, alias, created_at DESC, id DESC),
    UNIQUE INDEX uq_rollout_active_alias (namespace, code, alias, active_marker)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE cf_rollout_event
(
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT ck_rollout_event_type CHECK (REGEXP_LIKE(event_type, '^[A-Z][A-Z0-9_]*$', 'c')),
    CONSTRAINT ck_rollout_event_transition CHECK (
        (sequence = 1 AND from_phase IS NULL AND to_phase IN ('IN_PROGRESS', 'COMPLETED'))
            OR (sequence > 1 AND from_phase IS NOT NULL AND from_phase = 'IN_PROGRESS')
        ),
    CONSTRAINT ck_rollout_event_to_phase CHECK (to_phase IN ('IN_PROGRESS', 'COMPLETED', 'ABORTED')),
    CONSTRAINT ck_rollout_event_created_at CHECK (created_at > 0),
    INDEX idx_rollout_event_created (rollout_id, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TRIGGER trg_rollout_event_no_update
    BEFORE UPDATE ON cf_rollout_event
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cf_rollout_event is append-only';
CREATE TRIGGER trg_rollout_event_no_delete
    BEFORE DELETE ON cf_rollout_event
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cf_rollout_event is append-only';

CREATE TABLE cf_routing_outbox
(
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    delivery_key    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    namespace       VARCHAR(128) NOT NULL,
    code            VARCHAR(128) NOT NULL,
    alias           VARCHAR(64)  NOT NULL,
    routing_key     VARCHAR(512) NOT NULL,
    payload         LONGTEXT     NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at BIGINT,
    last_error      LONGTEXT,
    lease_token     VARCHAR(128),
    lease_until     BIGINT,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_outbox_event_type CHECK (event_type = 'ALIAS_STATE'),
    CONSTRAINT ck_outbox_delivery_key CHECK (CHAR_LENGTH(delivery_key) = 64),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_delivery_state CHECK (
        (status = 'PENDING' AND lease_token IS NULL AND lease_until IS NULL)
            OR (status = 'PROCESSING' AND next_attempt_at IS NULL
            AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
            OR (status IN ('DELIVERED', 'FAILED') AND next_attempt_at IS NULL
            AND lease_token IS NULL AND lease_until IS NULL)
        ),
    CONSTRAINT ck_outbox_timestamps CHECK (created_at > 0 AND updated_at >= created_at),
    UNIQUE INDEX uq_outbox_delivery_key (delivery_key),
    INDEX idx_outbox_status_next (status, next_attempt_at, id),
    INDEX idx_outbox_status_lease (status, lease_until, id),
    INDEX idx_outbox_delivered_retention (status, updated_at, id),
    INDEX idx_outbox_process (namespace, code)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
