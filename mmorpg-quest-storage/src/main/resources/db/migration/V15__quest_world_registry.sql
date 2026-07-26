CREATE TABLE quest_location (
    location_id VARCHAR(191) NOT NULL,
    world_uuid BINARY(16) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (location_id)
) ENGINE=InnoDB;

CREATE TABLE quest_world_object (
    world_uuid BINARY(16) NOT NULL,
    block_x INT NOT NULL,
    block_y INT NOT NULL,
    block_z INT NOT NULL,
    object_id VARCHAR(191) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (world_uuid, block_x, block_y, block_z),
    KEY idx_quest_world_object_id (object_id)
) ENGINE=InnoDB;
