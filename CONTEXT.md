# TUICord – Project Context

TUICord is a **self-hosted, terminal-based Discord client**. It is not a bot in the traditional sense — it uses a bot
token to act as the user's presence on Discord, with all interaction happening through a keyboard-driven TUI running in
the terminal.

---

## What we are building

Two components that talk to each other:

**1. Java backend (always running)**
A persistent JDA-powered process that stays connected to the Discord gateway. It owns all Discord state: it receives
every event, writes everything to a MySQL database, and exposes a local REST + WebSocket API for the TUI client to
consume. The bot token, database credentials, and all Discord communication live here exclusively. The backend is
expected to run with ~99% uptime (systemd / Docker).

**2. Python TUI client (connects to the backend)**
A terminal UI built with `textual`. It never talks to Discord directly — it only talks to the backend. It renders
guilds, channels, and messages; handles keyboard navigation; and lets the user send messages, search history, run
scripts, and view member profiles. Multiple clients can connect to the same backend simultaneously.

---

## Key design decisions already made

- **MySQL 8.0+** is the database. `utf8mb4` throughout for emoji support.
- **Discord snowflakes** are stored as `BIGINT UNSIGNED`.
- **JDA is the source of truth for all live state.** The DB is not a mirror of Discord — it is a selective,
  append-friendly archive. Anything that needs to be current right now (member profiles, presence, voice state, channel
  metadata, permission overwrites, guild details) is read directly from JDA's in-memory cache, never from MySQL.
- **The DB caches only:** message content and edit history, attachment metadata, reactions, minimal identity records for
  guilds/channels/users/roles, and a historical log of role assignments.
- **Member profiles are not persisted.** Nickname, guild avatar, guild banner, guild bio, boost status, and timeout
  state are read from JDA at runtime. Storing them would create staleness bugs with no self-correcting mechanism.
- **Role assignments are a historical log**, not a current-state table. `guild_member_roles` records when a role was
  granted (`assigned_at`) and when it was removed (`removed_at`). Current roles are read from JDA; the DB answers
  historical questions.
- **Roles are soft-deleted** (`deleted = TRUE`) so that historical assignments in `guild_member_roles` remain readable
  after a role is removed from Discord.
- **`role_permissions` does not exist.** Permission resolution uses JDA against the raw `roles.permissions` bitfield —
  no normalized per-flag table.
- **Message edit history is append-only**: `messages.content` is always the current version. Each edit event snapshots
  the *previous* content into `message_edits` with an incrementing `revision` number. `messages.edit_count` tracks the
  total. The write is transactional and idempotent (duplicate revision inserts are silently ignored on reconnect
  replay).
- **Soft deletes**: messages are never hard-deleted — `deleted = TRUE` and `deleted_at` are set instead.
- **No FK on `messages.author_id`**: a user may leave or be deleted; message history must survive regardless.
- **Scripting**: users can write Python or Lua scripts that run sandboxed in the backend process. Scripts get a
  controlled API (`send_message`, `get_messages`, `search`, `log`) and nothing else — no filesystem, no network,
  5-second timeout, 64 MB cap.

---

## Database tables (MySQL)

| Table                | Purpose                                                                        |
|----------------------|--------------------------------------------------------------------------------|
| `guilds`             | Minimal identity — name and icon only; live metadata from JDA                  |
| `channels`           | Minimal identity — name and type only; live metadata from JDA                  |
| `users`              | Minimal identity — username, global_name, avatar; live profile from JDA        |
| `roles`              | Role name, color, permissions bitfield; soft-deleted when removed from Discord |
| `guild_member_roles` | Historical log of role assignments — `assigned_at` + `removed_at`              |
| `messages`           | Cached messages; soft-deleted; FTS-indexed                                     |
| `message_edits`      | Append-only edit history (one row per edit, content before the edit)           |
| `attachments`        | Files attached to messages                                                     |
| `reactions`          | Aggregated reaction counts per message+emoji                                   |
| `reaction_users`     | Which users reacted with which emoji                                           |
| `scripts`            | Registry of user-defined Python/Lua scripts                                    |
| `script_runs`        | Execution log for scripts (output, errors, duration)                           |
| `schema_version`     | Migration tracker (current version: 4)                                         |

---

## Backend API surface

**REST** (queries and commands):

- `GET /guilds`, `GET /guilds/:id/channels`, `GET /guilds/:id/roles`, `GET /guilds/:id/members`
- `GET /channels/:id/messages`, `POST /channels/:id/messages`, `POST /channels/:id/typing`
- `GET /messages/:id/edits`, `PATCH /messages/:id`, `DELETE /messages/:id`
- `GET /search?q=&guild=&channel=`
- `GET /users/:id`, `GET /users/:id/profile?guild=`
- `POST /guilds/join`, `POST /guilds/:id/leave`
- `GET /health`

**WebSocket** (server → client events):
`MESSAGE_CREATE`, `MESSAGE_UPDATE`, `MESSAGE_DELETE`, `REACTION_ADD`, `REACTION_REMOVE`, `TYPING_START`, `GUILD_UPDATE`,
`CHANNEL_UPDATE`, `MEMBER_JOIN`, `MEMBER_LEAVE`, `MEMBER_UPDATE`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_DELETE`

---

## What is explicitly out of scope

- Voice or video of any kind.
- Running on shared/cloud infrastructure — strictly self-hosted.
- Multiple bot tokens per backend instance.
- Replacing Discord for non-technical users.

---

## Where to find more detail

| File         | Contents                                                                                                                                      |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `CLAUDE.md`  | Full agent task guide: gateway events, write patterns, resilience rules, TUI layout, scripting sandbox spec, priority checklist, project tree |
| `create.sql` | Complete MySQL schema with all indexes, constraints, and inline documentation                                                                 |