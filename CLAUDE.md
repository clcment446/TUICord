# TUICord – Agent Task Guide

> **Runtime assumption:** The Java backend runs as a persistent, long-lived process with **99% uptime**.
> All resilience, reconnection, and state-recovery logic must reflect this — the backend is a daemon, not a script.

---

## 1. Architecture Overview

TUICord is a terminal Discord client split into two independent layers:

| Layer       | Stack        | Responsibility                                                     |
|-------------|--------------|--------------------------------------------------------------------|
| **Backend** | Java + JDA   | Discord gateway, event handling, message cache, REST/WebSocket API |
| **Client**  | Python (TUI) | Rendering, navigation, input, scripting bridge                     |

The backend runs continuously and serves one or more TUI clients simultaneously. The client is stateless and reconnects
automatically on disconnect.

**Design rules:**

- Heavy lifting lives in the backend. The client renders and inputs only.
- The backend owns all Discord state — the client never talks to Discord directly.
- Bot token, credentials, and DB access are backend-only. The client holds only the backend URL.

---

## 2. Backend (Java / JDA)

### 2.1 Discord Gateway

- Connect to Discord via JDA with automatic reconnect on drop (JDA handles this; ensure `ReconnectQueue` is not
  disabled).
- Handle and dispatch the following events:
    - `MessageReceivedEvent`, `MessageUpdateEvent`, `MessageDeleteEvent`
    - `GuildMessageReactionAddEvent`, `GuildMessageReactionRemoveEvent`
    - `GuildJoinEvent`, `GuildLeaveEvent`, `GuildMemberJoinEvent`, `GuildMemberRemoveEvent`, `GuildMemberUpdateEvent`
    - `RoleCreateEvent`, `RoleUpdateEvent`, `RoleDeleteEvent`
    - `ChannelCreateEvent`, `ChannelDeleteEvent`, `ChannelUpdateEvent`
    - `UserTypingEvent`
- Log and recover from gateway errors without crashing the process.
- Emit all events to connected TUI clients via WebSocket/SSE.

### 2.2 Persistence & Caching Philosophy

**What gets persisted to MySQL:**

| Table                          | Why it's cached                                                                                                  |
|--------------------------------|------------------------------------------------------------------------------------------------------------------|
| `guilds`                       | Identity anchor for channels and messages — name + icon only                                                     |
| `channels`                     | Identity anchor for messages — name and type only                                                                |
| `users`                        | Message author display without a JDA call — username, global_name, avatar only                                   |
| `roles`                        | Needed to make `guild_member_roles` history readable after a role is deleted; soft-deleted with `deleted = TRUE` |
| `guild_member_roles`           | Historical log of role assignments — `assigned_at` + `removed_at`; the only member state in the DB               |
| `messages`                     | Core feature — full content, soft-deleted, FTS-indexed                                                           |
| `message_edits`                | Append-only edit history — one row per edit, content before the edit, idempotent on reconnect                    |
| `attachments`                  | Part of message history                                                                                          |
| `reactions` / `reaction_users` | Part of message history                                                                                          |
| `scripts` / `script_runs`      | Script registry and execution audit log                                                                          |

**What is intentionally NOT persisted (read from JDA at runtime):**

- Member profiles: nickname, guild avatar, guild banner, guild bio, boost status, timeout state
- Presence and online status
- Voice state
- Typing indicators
- Channel metadata: topic, position, slowmode, permission overwrites
- Guild metadata: owner, member count, features, boost tier
- Computed/effective permissions (resolve from `roles.permissions` bitfield via JDA)

**Write rules:**

- Write-through: every relevant Discord event updates the DB immediately.
- `guild_member_roles` is a log, not a current-state table. On `GuildMemberRoleAddEvent`: INSERT a new row. On
  `GuildMemberRoleRemoveEvent`: UPDATE `removed_at` on the open row (`removed_at IS NULL`).
- Roles deleted from Discord: set `roles.deleted = TRUE` rather than removing the row — historical assignments must
  remain readable.
- Soft-delete messages (`deleted = TRUE`, `deleted_at = now()`).
- FTS on `messages.content` via MySQL `FULLTEXT` index with `ngram` parser.
- On startup, backfill recent messages per channel (configurable, e.g. last 200).

### 2.3 API Layer

Expose a local API for the TUI client. Prefer **WebSocket** for events + **REST** for queries.

**REST endpoints:**

| Method   | Path                                    | Description                                                 |
|----------|-----------------------------------------|-------------------------------------------------------------|
| `GET`    | `/guilds`                               | List guilds (names from DB, live metadata from JDA)         |
| `GET`    | `/guilds/:id/channels`                  | List channels for a guild                                   |
| `GET`    | `/guilds/:id/roles`                     | List roles for a guild (from JDA; DB used only for history) |
| `GET`    | `/guilds/:id/members?limit=&after=`     | Live member list from JDA                                   |
| `GET`    | `/channels/:id/messages?limit=&before=` | Paginated message history from DB                           |
| `POST`   | `/channels/:id/messages`                | Send a message                                              |
| `POST`   | `/channels/:id/typing`                  | Trigger typing indicator                                    |
| `GET`    | `/messages/:id/edits`                   | Full edit history (from DB, ordered by revision ASC)        |
| `GET`    | `/search?q=&guild=&channel=&limit=`     | Full-text search across cached messages                     |
| `GET`    | `/users/:id`                            | Minimal identity from DB; live profile fields from JDA      |
| `GET`    | `/users/:id/roles?guild=`               | Historical role assignments from `guild_member_roles`       |
| `POST`   | `/guilds/join`                          | Join a guild by invite code                                 |
| `POST`   | `/guilds/:id/leave`                     | Leave a guild                                               |
| `DELETE` | `/messages/:id`                         | Delete a message                                            |
| `PATCH`  | `/messages/:id`                         | Edit a message                                              |

**WebSocket events (server → client):**

```
MESSAGE_CREATE, MESSAGE_UPDATE, MESSAGE_DELETE
REACTION_ADD, REACTION_REMOVE
TYPING_START
GUILD_UPDATE, CHANNEL_UPDATE
MEMBER_JOIN, MEMBER_LEAVE, MEMBER_UPDATE
ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE
```

### 2.4 Resilience (99% Uptime)

Because the backend runs indefinitely:

- Wrap all event handlers in `try/catch` — an unhandled exception must never kill the JVM process.
- Use a supervisor thread or framework (e.g., `ProcessBuilder` restart wrapper, systemd unit, or Docker
  `restart: always`) to auto-recover from fatal crashes.
- Persist all state to DB before acknowledging events so a restart doesn't lose data.
- Reconnect logic: JDA reconnects automatically, but log all reconnect attempts with timestamps.
- Expose a `/health` endpoint returning uptime, gateway status, DB connection status, and connected client count.
- Rate limit Discord API calls defensively — never rely on Discord's 429 response as the primary throttle.

### 2.5 MessageUpdateEvent Write Pattern

`MessageUpdateEvent` must be handled atomically to keep `messages` and `message_edits` consistent:

```
ON MessageUpdateEvent(event):
  existing = SELECT content, edit_count FROM messages WHERE id = event.messageId
  IF existing.content != event.newContent:          -- ignore no-op updates (embed resolution, etc.)
    BEGIN TRANSACTION
      INSERT INTO message_edits
        (message_id, revision, content, edited_at)
      VALUES
        (event.messageId, existing.edit_count + 1, existing.content, event.editedAt)
      ON DUPLICATE KEY IGNORE                        -- idempotency guard on reconnect replay

      UPDATE messages
        SET content    = event.newContent,
            edited_at  = event.editedAt,
            edit_count = edit_count + 1
        WHERE id = event.messageId
    COMMIT
    EMIT MESSAGE_UPDATE to connected clients
```

- The `UNIQUE KEY (message_id, revision)` on `message_edits` makes duplicate inserts safe on gateway reconnect.
- A no-content update (e.g. Discord resolving a link embed) must **not** increment `edit_count` or create a
  `message_edits` row.
- The TUI displays `(edited)` next to any message where `edit_count > 0`. Pressing `e` on a message fetches
  `/messages/:id/edits` and renders the full revision timeline inline.

## 3. Client (Python TUI)

### 3.1 Layout

```
┌─────────────────────────────────────────────────────┐
│  Guild list  │  Channel list  │  Message view        │
│  (sidebar)   │  (sidebar)     │  (scrollable)        │
│              │                │                      │
│              │                │ [typing indicator]   │
├──────────────┴────────────────┴──────────────────────┤
│  Input bar                              [send] [cmd] │
└─────────────────────────────────────────────────────┘
```

Use `textual` (preferred) or `urwid` for layout. Avoid raw `curses` unless necessary.

### 3.2 Features

- Guild and channel navigation (keyboard-driven, vim-style: `j/k`, `g/G`, `Enter`).
- Message scrolling with lazy loading (request older pages as user scrolls up).
- Mention highlighting (`@username` in accent color, tinted by the user's highest hoisted role color).
- Inline display of reactions with counts.
- Typing indicator shown in message view footer.
- Command bar (`:` prefix) for actions: `:join <invite>`, `:leave`, `:search <query>`, `:script <n>`.
- Unread indicators per channel.
- Edited messages marked with `(edited)` suffix; press `e` on a focused message to expand the full revision timeline
  inline, fetched from `/messages/:id/edits`.
- Member profile popover on `p`: shows global avatar/bio merged with guild-specific nick/avatar/bio and role list.

### 3.3 Backend Communication

- On startup, connect to the backend WebSocket and authenticate (shared secret or token).
- On disconnect: retry with exponential backoff (1s, 2s, 4s … up to 30s), then keep retrying indefinitely.
- REST calls for data queries; WebSocket for real-time events.
- Cache the last N messages per channel locally in memory to avoid redundant requests.

### 3.4 Config (`~/.config/tuicord/config.toml`)

```toml
[backend]
url = "http://localhost:8080"
secret = "changeme"

[ui]
theme = "dark"           # dark | light | custom
timestamp_format = "%H:%M"
max_messages = 200

[scripts]
directory = "~/.config/tuicord/scripts"
```

---

## 4. Scripting (Python & Lua)

### 4.1 Execution Model

Scripts are triggered by the user via the TUI command bar (`:script <name>`) or by backend event hooks (e.g., on
mention). They run in the **backend** process, not in the client.

Scripts receive a controlled API object — they do not get raw JDA or DB access.

### 4.2 Exposed Script API

```python
# Python
api.send_message(channel_id, content)
api.get_messages(channel_id, limit=20)
api.search(query, guild_id=None)
api.get_guilds()
api.log(message)          # output shown in TUI script panel
```

```lua
-- Lua (same surface)
api.send_message(channel_id, content)
api.get_messages(channel_id, limit)
api.search(query)
api.log(message)
```

### 4.3 Sandbox Requirements

- Python: use `RestrictedPython` or a subprocess with `seccomp` rules. No `import os`, `import subprocess`,
  `import socket`, or `open()`.
- Lua: use a stripped `lua` runtime without `io`, `os`, or `package` libraries loaded.
- Hard limits: 5-second wall-clock timeout, 64 MB memory cap, no network access outside the script API.
- Script output is streamed line-by-line to a dedicated TUI panel.

---

## 5. Priority Order

Work is sequenced so each step produces something runnable.

| #  | Task                                                          | Done |
|----|---------------------------------------------------------------|------|
| 1  | JDA bot connects, logs to console, stays alive on disconnect  | [ ]  |
| 2  | SQLite schema created on startup, messages written on receive | [ ]  |
| 3  | REST API: guild list, channel list, message history           | [ ]  |
| 4  | WebSocket: forward Discord events to connected clients        | [ ]  |
| 5  | `/health` endpoint reporting gateway + DB status              | [ ]  |
| 6  | Basic TUI: guild/channel nav, message view, input bar         | [ ]  |
| 7  | TUI sends messages via backend REST                           | [ ]  |
| 8  | TUI reconnects to backend automatically on drop               | [ ]  |
| 9  | Full-text search endpoint + TUI `:search` command             | [ ]  |
| 10 | Guild join/leave via TUI commands                             | [ ]  |
| 11 | Python scripting sandbox + `:script` command                  | [ ]  |
| 12 | Lua scripting sandbox                                         | [ ]  |

---

## 6. Project Structure

```
TUICord/
├── backend/
│   ├── src/main/java/tuicord/
│   │   ├── Bot.java              # JDA setup and lifecycle
│   │   ├── EventHandler.java     # Discord event dispatch
│   │   ├── db/                   # Schema, queries, migrations
│   │   ├── api/                  # REST + WebSocket server
│   │   └── scripting/            # Script runner and sandbox
│   └── pom.xml
├── client/
│   ├── tuicord/
│   │   ├── app.py                # Textual app entry point
│   │   ├── backend.py            # REST + WebSocket client
│   │   ├── widgets/              # TUI components
│   │   └── config.py             # Config loader
│   └── requirements.txt
├── examples/
│   ├── hello.py                  # Example Python script
│   └── hello.lua                 # Example Lua script
├── docs/
│   └── api.md                    # REST + WebSocket reference
├── CLAUDE.md
└── README.md
```

---

## 7. Non-Goals (explicitly out of scope)

- Voice/video support.
- Replacing the official Discord client for non-power users.
- Running the bot token on shared/cloud infrastructure (self-hosted only).
- Supporting multiple bot tokens simultaneously (single token per backend instance).