CREATE TABLE sys_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE route_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id        VARCHAR(128) NOT NULL,
    uri             VARCHAR(512) NOT NULL,
    order_no        INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    predicates_json VARCHAR(5000) NOT NULL,
    filters_json    VARCHAR(5000) NOT NULL,
    metadata_json   VARCHAR(5000) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_route_config_route_id UNIQUE (route_id)
);

CREATE TABLE audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_username VARCHAR(64) NOT NULL,
    action        VARCHAR(16) NOT NULL,
    route_id      VARCHAR(128),
    before_json   VARCHAR(5000),
    after_json    VARCHAR(5000),
    ip            VARCHAR(64),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_route_id ON audit_log (route_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
