CREATE TABLE mmorpg_party (
    party_uuid BINARY(16) NOT NULL,
    leader_uuid BINARY(16) NOT NULL,
    maximum_members INT NOT NULL,
    reward_range DOUBLE NOT NULL,
    rewards_same_world BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    party_revision BIGINT NOT NULL,
    PRIMARY KEY (party_uuid),
    CONSTRAINT ck_mmorpg_party_maximum CHECK (maximum_members >= 2),
    CONSTRAINT ck_mmorpg_party_range CHECK (reward_range > 0),
    CONSTRAINT ck_mmorpg_party_revision CHECK (party_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_party_member (
    party_uuid BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    PRIMARY KEY (party_uuid, player_uuid),
    UNIQUE KEY uq_mmorpg_party_member_player (player_uuid),
    CONSTRAINT fk_mmorpg_party_member FOREIGN KEY (party_uuid)
        REFERENCES mmorpg_party(party_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mmorpg_party_invitation (
    party_uuid BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (party_uuid, player_uuid),
    KEY idx_mmorpg_party_invitation_player (player_uuid, expires_at),
    CONSTRAINT fk_mmorpg_party_invitation FOREIGN KEY (party_uuid)
        REFERENCES mmorpg_party(party_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
