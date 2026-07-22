CREATE TABLE mmorpg_content_revisions (
    revision_id BIGINT NOT NULL AUTO_INCREMENT,
    content_hash CHAR(64) NOT NULL,
    definition_count INT NOT NULL,
    loaded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (revision_id),
    UNIQUE KEY uq_mmorpg_content_revision_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_audit_log (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_uuid BINARY(16) NULL,
    action VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    KEY idx_mmorpg_audit_actor_created (actor_uuid, created_at),
    KEY idx_mmorpg_audit_action_created (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
