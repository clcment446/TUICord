-- =============================================================================
-- TUICord – Database Schema
-- Target:  MySQL 8.0+
-- Charset: utf8mb4 (full Unicode + emoji support)
--
-- Caching philosophy:
--   Live state (presence, voice, member profiles, permission overwrites,
--   typing indicators) is NOT persisted — JDA's in-memory cache is the
--   source of truth for anything that needs to be current right now.
--   The DB stores only:
--     - Message content and full edit history
--     - Attachment metadata
--     - Reactions
--     - Historical role assignments (who held what role, and when)
--     - Minimal guild/channel/user identity needed to make messages readable
-- =============================================================================

CREATE DATABASE IF NOT EXISTS tuicord
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tuicord;

-- =============================================================================
-- GUILDS
-- Minimal identity record. Used to namespace channels and messages.
-- Do NOT store live state here (member count, owner, features, etc.) —
-- read those from JDA at runtime.
-- =============================================================================
CREATE TABLE IF NOT EXISTS guilds (
    id          BIGINT UNSIGNED     NOT NULL,               -- Discord snowflake
    name        VARCHAR(100)        NOT NULL,
    icon_url    VARCHAR(512)            NULL,
    created_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id)
) ENGINE=InnoDB;


-- =============================================================================
-- CHANNELS
-- Minimal identity record. Used to group messages.
-- Do NOT store position, topic, slowmode, overwrites, etc. — read from JDA.
-- =============================================================================
CREATE TABLE IF NOT EXISTS channels (
    id          BIGINT UNSIGNED     NOT NULL,               -- Discord snowflake
    guild_id    BIGINT UNSIGNED         NULL,               -- NULL for DM channels
    name        VARCHAR(100)        NOT NULL,
    type        TINYINT UNSIGNED    NOT NULL,               -- 0=TEXT 1=DM 5=NEWS 11=THREAD …
    created_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    CONSTRAINT fk_channels_guild
        FOREIGN KEY (guild_id) REFERENCES guilds (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_channels_guild (guild_id)
) ENGINE=InnoDB;


-- =============================================================================
-- USERS
-- Minimal identity record used to display message authors without a JDA call.
-- Do NOT store live state here (presence, status, flags, accent color, etc.)
-- Profile fields (nick, guild avatar, bio) are read from JDA at runtime.
-- =============================================================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT UNSIGNED     NOT NULL,           -- Discord snowflake
    username        VARCHAR(32)         NOT NULL,           -- global handle
    global_name     VARCHAR(32)             NULL,           -- display name (new username system)
    discriminator   CHAR(4)                 NULL,           -- legacy #tag; NULL for new usernames
    avatar_url      VARCHAR(512)            NULL,
    bot             BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_users_username (username)
) ENGINE=InnoDB;


-- =============================================================================
-- ROLES
-- Guild role identity and permission bitfield.
-- Stored so historical role assignments (guild_member_roles) remain readable
-- even after a role is deleted. Do NOT expand into a role_permissions table —
-- resolve permissions from the bitfield via JDA at runtime.
-- =============================================================================
CREATE TABLE IF NOT EXISTS roles (
    id          BIGINT UNSIGNED     NOT NULL,               -- Discord snowflake
    guild_id    BIGINT UNSIGNED     NOT NULL,
    name        VARCHAR(100)        NOT NULL,
    color       INT UNSIGNED        NOT NULL DEFAULT 0,     -- RGB hex int; 0 = no color
    permissions BIGINT UNSIGNED     NOT NULL DEFAULT 0,     -- raw Discord permission bitfield
    deleted     BOOLEAN             NOT NULL DEFAULT FALSE, -- TRUE when role is deleted from Discord
    created_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    CONSTRAINT fk_roles_guild
        FOREIGN KEY (guild_id) REFERENCES guilds (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_roles_guild (guild_id)
) ENGINE=InnoDB;


-- =============================================================================
-- GUILD_MEMBER_ROLES
-- Historical log of role assignments. A row is inserted when a member gains a
-- role and closed (removed_at set) when they lose it.
-- This is the only member-related state persisted to the DB.
--
-- Current role state → read from JDA (Member#getRoles()).
-- Historical role state → query this table.
-- =============================================================================
CREATE TABLE IF NOT EXISTS guild_member_roles (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED     NOT NULL,               -- no FK to users — user may have left
    guild_id    BIGINT UNSIGNED     NOT NULL,
    role_id     BIGINT UNSIGNED     NOT NULL,               -- no FK to roles — role may be deleted
    assigned_at DATETIME(3)         NOT NULL,               -- when the role was granted
    removed_at  DATETIME(3)             NULL,               -- NULL = role still held (or unknown)

    PRIMARY KEY (id),
    CONSTRAINT fk_gmr_guild
        FOREIGN KEY (guild_id) REFERENCES guilds (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- "What roles has user X held in guild G, and when?"
    INDEX idx_gmr_user_guild        (user_id, guild_id, assigned_at DESC),

    -- "Who has ever held role R?"
    INDEX idx_gmr_role              (role_id, assigned_at DESC),

    -- Idempotency: prevent duplicate open assignments for the same (user, guild, role).
    -- A new assignment is only valid if no row exists with removed_at IS NULL.
    -- Enforced in application logic; this index supports the lookup efficiently.
    INDEX idx_gmr_open_assignment   (user_id, guild_id, role_id, removed_at)
) ENGINE=InnoDB;


-- =============================================================================
-- MESSAGES
-- Cached Discord messages. Soft-deleted on MessageDeleteEvent.
-- author_id has no FK to users — a user may be deleted or uncached; message
-- history must never be lost because of it.
-- =============================================================================
CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT UNSIGNED     NOT NULL,           -- Discord snowflake (encodes timestamp)
    channel_id      BIGINT UNSIGNED     NOT NULL,
    author_id       BIGINT UNSIGNED     NOT NULL,           -- user snowflake (no FK by design)
    guild_id        BIGINT UNSIGNED         NULL,           -- denormalised for fast guild-wide search
    content         TEXT                    NULL,           -- NULL for embed-only / attachment-only
    type            TINYINT UNSIGNED    NOT NULL DEFAULT 0, -- 0=DEFAULT 6=REPLY 19=THREAD_STARTER …
    pinned          BOOLEAN             NOT NULL DEFAULT FALSE,
    tts             BOOLEAN             NOT NULL DEFAULT FALSE,
    referenced_id   BIGINT UNSIGNED         NULL,           -- replied-to message snowflake
    edit_count      SMALLINT UNSIGNED   NOT NULL DEFAULT 0, -- incremented on each edit event
    edited_at       DATETIME(3)             NULL,           -- timestamp of most recent edit; NULL if never edited
    deleted         BOOLEAN             NOT NULL DEFAULT FALSE,
    deleted_at      DATETIME(3)             NULL,
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    CONSTRAINT fk_messages_channel
        FOREIGN KEY (channel_id) REFERENCES channels (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Core access pattern: latest N messages in a channel
    INDEX idx_messages_channel_time (channel_id, id DESC),

    -- Guild-wide queries (search, audit)
    INDEX idx_messages_guild_time   (guild_id, id DESC),

    -- Author lookup (profile view, mod tools)
    INDEX idx_messages_author       (author_id, id DESC),

    -- Full-text search on message content
    FULLTEXT INDEX ft_messages_content (content) WITH PARSER ngram
) ENGINE=InnoDB;


-- =============================================================================
-- MESSAGE_EDITS
-- Append-only edit history. One row per MessageUpdateEvent that changes content.
--
-- Write pattern (must be atomic):
--   1. INSERT into message_edits: current messages.content, revision = edit_count + 1
--   2. UPDATE messages SET content = <new>, edited_at = <now>, edit_count = edit_count + 1
--
-- No-op updates (Discord resolving link embeds, etc.) must NOT create a row here.
-- The UNIQUE KEY on (message_id, revision) makes replay-on-reconnect idempotent.
-- =============================================================================
CREATE TABLE IF NOT EXISTS message_edits (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    message_id  BIGINT UNSIGNED     NOT NULL,
    revision    SMALLINT UNSIGNED   NOT NULL,               -- 1-based; content *before* this edit
    content     TEXT                    NULL,
    edited_at   DATETIME(3)         NOT NULL,               -- timestamp Discord reported

    PRIMARY KEY (id),
    CONSTRAINT fk_me_message
        FOREIGN KEY (message_id) REFERENCES messages (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    UNIQUE KEY uq_me_revision   (message_id, revision),
    INDEX idx_me_time           (message_id, edited_at ASC)
) ENGINE=InnoDB;


-- =============================================================================
-- ATTACHMENTS
-- =============================================================================
CREATE TABLE IF NOT EXISTS attachments (
    id              BIGINT UNSIGNED     NOT NULL,
    message_id      BIGINT UNSIGNED     NOT NULL,
    filename        VARCHAR(255)        NOT NULL,
    url             VARCHAR(512)        NOT NULL,
    proxy_url       VARCHAR(512)            NULL,
    size_bytes      INT UNSIGNED        NOT NULL,
    width           SMALLINT UNSIGNED       NULL,
    height          SMALLINT UNSIGNED       NULL,
    content_type    VARCHAR(128)            NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_attachments_message
        FOREIGN KEY (message_id) REFERENCES messages (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_attachments_message (message_id)
) ENGINE=InnoDB;


-- =============================================================================
-- REACTIONS
-- =============================================================================
CREATE TABLE IF NOT EXISTS reactions (
    message_id  BIGINT UNSIGNED     NOT NULL,
    emoji       VARCHAR(64)         NOT NULL,               -- unicode emoji or "name:id" for custom
    emoji_id    BIGINT UNSIGNED         NULL,               -- NULL for unicode emoji
    animated    BOOLEAN             NOT NULL DEFAULT FALSE,
    count       SMALLINT UNSIGNED   NOT NULL DEFAULT 1,
    updated_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (message_id, emoji),
    CONSTRAINT fk_reactions_message
        FOREIGN KEY (message_id) REFERENCES messages (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;


-- =============================================================================
-- REACTION_USERS
-- =============================================================================
CREATE TABLE IF NOT EXISTS reaction_users (
    message_id  BIGINT UNSIGNED     NOT NULL,
    emoji       VARCHAR(64)         NOT NULL,
    user_id     BIGINT UNSIGNED     NOT NULL,
    reacted_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (message_id, emoji, user_id),
    CONSTRAINT fk_ru_reaction
        FOREIGN KEY (message_id, emoji) REFERENCES reactions (message_id, emoji)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;


-- =============================================================================
-- SCRIPTS
-- =============================================================================
CREATE TABLE IF NOT EXISTS scripts (
    id          INT UNSIGNED        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)         NOT NULL,
    language    ENUM('python','lua') NOT NULL,
    filename    VARCHAR(255)        NOT NULL,
    description VARCHAR(512)            NULL,
    enabled     BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_scripts_name (name)
) ENGINE=InnoDB;


-- =============================================================================
-- SCRIPT_RUNS
-- =============================================================================
CREATE TABLE IF NOT EXISTS script_runs (
    id          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    script_id   INT UNSIGNED        NOT NULL,
    triggered_by BIGINT UNSIGNED        NULL,               -- user snowflake
    channel_id  BIGINT UNSIGNED         NULL,
    exit_status ENUM('ok','timeout','error') NOT NULL,
    output      TEXT                    NULL,
    error       TEXT                    NULL,
    duration_ms INT UNSIGNED            NULL,
    ran_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    CONSTRAINT fk_sr_script
        FOREIGN KEY (script_id) REFERENCES scripts (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_sr_script_time (script_id, ran_at DESC)
) ENGINE=InnoDB;


-- =============================================================================
-- SCHEMA VERSION
-- =============================================================================
CREATE TABLE IF NOT EXISTS schema_version (
    version     SMALLINT UNSIGNED   NOT NULL,
    applied_at  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    note        VARCHAR(255)            NULL,

    PRIMARY KEY (version)
) ENGINE=InnoDB;

INSERT INTO schema_version (version, note)
VALUES
    (1, 'Initial schema'),
    (2, 'Add users, guild_members, guild_member_roles, roles, role_permissions'),
    (3, 'Add message_edits and edit_count'),
    (4, 'Remove guild_members and role_permissions; guild_member_roles becomes historical log; slim guilds/channels/users/roles to identity-only');