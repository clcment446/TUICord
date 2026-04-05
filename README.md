# Discord Bot – JDA + Javalin + MySQL

A monolithic Discord bot written in Java 17 with:

| Layer | Technology |
|---|---|
| Discord client | [JDA 5](https://github.com/discord-jda/JDA) |
| REST API | [Javalin 6](https://javalin.io/) on port **8080** |
| Database | MySQL 8 on port **3306** |
| Connection pool | [HikariCP](https://github.com/brettwohlberg/HikariCP) |
| Build | Gradle 8 (wrapper included) |

---

## Project layout

```
discord-bot/
├── bot/                              ← Gradle project root
│   ├── build.gradle
│   ├── gradlew  /  gradlew.bat
│   └── src/main/java/bot/
│       ├── DiscordBot.java           ← entry point
│       ├── config/BotConfig.java     ← .env loader
│       ├── db/
│       │   ├── DatabasePool.java     ← HikariCP singleton
│       │   ├── User.java             ← DTO
│       │   └── UserRepo.java         ← DAO (all SQL)
│       ├── api/
│       │   ├── ApiServer.java        ← Javalin bootstrap
│       │   └── routes/UserRoutes.java← REST handlers
│       └── commands/
│           ├── SlashCommand.java     ← interface
│           ├── CommandHandler.java   ← dispatcher
│           ├── PingCommand.java      ← /ping
│           └── UserCommand.java      ← /user register|info|delete
├── scripts/
│   ├── install_mysql.sh              ← Ubuntu MySQL bootstrap
│   └── schema.sql                    ← canonical schema
├── docker-compose.yml                ← local MySQL (Arch dev)
└── .env.example
```

---

## 1 – Prerequisites

| What | Arch (dev) | Ubuntu server (prod) |
|---|---|---|
| Java | `sudo pacman -S jdk17-openjdk` | `sudo apt install openjdk-17-jre-headless` |
| Docker (optional) | `sudo pacman -S docker docker-compose` | — |
| MySQL | via Docker (see below) | via `install_mysql.sh` |

---

## 2 – Configure `.env`

```bash
cp .env.example .env
# Edit .env with your real values:
#   DISCORD_TOKEN   – from https://discord.com/developers/applications
#   DB_PASSWORD     – printed by install_mysql.sh  (or set yourself for Docker)
```

Place the `.env` file in the directory **from which you run the JAR** (usually the repo root or `bot/`).

---

## 3a – Local dev on Arch Linux (Docker MySQL)

```bash
# Start MySQL container  (port 3306 on localhost)
docker compose up -d

# Build + run the bot (from bot/)
cd bot
./gradlew run
```

That's it.  The schema is auto-applied by the Docker entrypoint on first start.

---

## 3b – Production deploy on Ubuntu

```bash
# 1. Run the one-shot MySQL installer
sudo bash scripts/install_mysql.sh
# ➜ copy the printed DB_PASSWORD into .env

# 2. Build the fat JAR on your dev machine (or on the server)
cd bot
./gradlew jar

# 3. Copy to server & run
scp bot/build/libs/discord-bot-*.jar user@server:/opt/discord-bot/
# On the server:
cd /opt/discord-bot
java -jar discord-bot-*.jar
```

### Running as a systemd service (recommended)

```ini
# /etc/systemd/system/discord-bot.service
[Unit]
Description=Discord Bot
After=network-online.target mysql.service
Wants=network-online.target

[Service]
User=botuser
WorkingDirectory=/opt/discord-bot
ExecStart=/usr/bin/java -jar /opt/discord-bot/discord-bot.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now discord-bot
```

---

## 4 – REST API reference

Base URL: `http://localhost:8080`

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Liveness check → `{"status":"ok"}` |
| GET | `/api/users?limit=20&offset=0` | Paginated user list |
| POST | `/api/users` | Create user (`discord_id`, `username`) |
| GET | `/api/users/{id}` | Single user by PK |
| PUT | `/api/users/{id}` | Update username |
| DELETE | `/api/users/{id}` | Delete user |

### Example – create a user

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"discord_id": "123456789012345678", "username": "Alice#0042"}'
```

---

## 5 – Slash commands

| Command | Sub | What it does |
|---|---|---|
| `/ping` | — | Shows gateway latency |
| `/user` | `register` | Inserts the invoker into the DB |
| `/user` | `info` | Shows the invoker's stored row |
| `/user` | `delete` | Removes the invoker's row |

All `/user` replies are **ephemeral** (only the invoker sees them).

---

## 6 – Adding a new command

1. Create a class in `commands/` that implements `SlashCommand`.
2. Register it in the static block inside `CommandHandler`.
3. That's it – the dispatcher and Discord registration are automatic.

---

## 7 – Adding a new REST endpoint

1. Create a new `*Routes` class in `api/routes/`.
2. Call `YourRoutes.register(app)` inside `ApiServer`.

---

## Ports at a glance

| Service | Port |
|---|---|
| MySQL | 3306 |
| REST API | 8080 |
| Discord | outbound only (no inbound port required) |
