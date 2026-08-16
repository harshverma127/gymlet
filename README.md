# Gymlet 🏋️

> **My cute personal gym companion.**

A cozy, pixel-flavoured personal workout tracker. Open it before your workout and you immediately know:

1. What exercise you're doing
2. What weight/reps you did last time
3. What you need to do today
4. Which sets you've completed
5. How you're progressing

**Today → Log each set → Check it off → Finish → Review progress.** Everything else is secondary.

---

## Tech stack

| Layer     | Choice                                        |
| --------- | --------------------------------------------- |
| Frontend  | React 19 + TypeScript + Vite 7 + React Router |
| Backend   | Spring Boot 3.5 (Java 21) + Spring Data JPA   |
| Database  | PostgreSQL (Render/Supabase) · MySQL (docker-compose) · H2 (zero-config default) |
| API       | REST + JSON                                   |
| Auth      | Username + 4-digit PIN, BCrypt-hashed, server-side sessions |
| Charts    | Hand-rolled SVG (no chart library)            |

**Accounts are deliberately simple** — username + 4-digit PIN, no email, no OAuth, no reset flows.
Every account gets its own fully independent copy of the workout plan; one user can never see another's data (enforced by the backend).

---

## Project structure

```
gymlet/
├── backend/                     # Spring Boot REST API
│   ├── src/main/java/com/gymlet/
│   │   ├── GymletApplication.java
│   │   ├── config/              # AuthFilter, AuthConfig, DataSeeder, SchemaMigration
│   │   ├── domain/              # JPA entities (AppUser, AuthSession, WorkoutDay, …)
│   │   ├── repository/          # Spring Data repositories (user-scoped)
│   │   ├── service/             # Auth, Structure, Session, Stats, BodyWeight, Profile
│   │   └── web/                 # Controllers, DTOs, error handler
│   ├── src/main/resources/
│   │   ├── application.yml          # default profile → H2 (in-memory)
│   │   └── application-mysql.yml    # MySQL profile
│   ├── smoke_test.py            # end-to-end API smoke test (auth + isolation + migration)
│   ├── mvnw / mvnw.cmd          # Maven wrapper (no local Maven needed)
│   └── pom.xml
├── frontend/                    # React + Vite SPA
│   ├── src/
│   │   ├── api/client.ts        # typed fetch wrapper + session token handling
│   │   ├── components/          # icons, ui primitives, charts, calendar, toast
│   │   ├── lib/                 # units + date formatting
│   │   ├── pages/               # Auth, Today, History, HistoryDetail, Progress, Profile
│   │   ├── styles.css           # the cozy-pixel design system (mobile-first)
│   │   └── state.tsx            # auth + profile context
│   └── vite.config.ts           # dev proxy /api → localhost:8080
├── docker-compose.yml           # optional MySQL 8
└── README.md
```

---

## Quick start (zero-config, 2 terminals)

**Requirements:** Java 17+ (tested on 25) and Node 20+.

```bash
# 1. Backend — embedded H2 database; seeds the default 5-day split template
cd backend
./mvnw spring-boot:run
# (Windows: ./mvnw.cmd spring-boot:run)
```

```bash
# 2. Frontend
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** and create your account (username + 4-digit PIN) — you're in.
The dev server proxies `/api` to the backend, so no CORS setup is needed locally.

> **Local API URL:** the frontend talks to the deployed backend (`https://gymlet.onrender.com`)
> by default. For local development create `frontend/.env.local`:
> ```
> VITE_API_URL=http://localhost:8080
> ```
> (gitignored — the Render build keeps the deployed default.)

> Note: the default profile uses an in-memory H2 database — data resets on backend restart. For persistent data use MySQL or PostgreSQL below.

---

## Database setup (MySQL / PostgreSQL)

### MySQL (Docker — recommended for local persistence)

```bash
docker compose up -d          # starts MySQL 8 with database `gymlet`
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

### PostgreSQL (Render/Supabase — what the deployment uses)

Set environment variables and run with the `mysql` profile name (any profile works — the
datasource is driven entirely by `DB_URL`):

```bash
export DB_URL='jdbc:postgresql://<host>:5432/<db>?sslmode=require'
export DB_USER=<user>
export DB_PASSWORD=<password>
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

The schema is created automatically (`ddl-auto: update`); a startup migration drops the old
global `workout_day.day_number` unique constraint and claims pre-upgrade data (see below).

### Environment variables

| Variable       | Default                                  | Used in            |
| -------------- | ---------------------------------------- | ------------------ |
| `DB_URL`       | `jdbc:mysql://localhost:3306/gymlet?…`   | non-default profiles |
| `DB_USER`      | `gymlet`                                 | non-default profiles |
| `DB_PASSWORD`  | `gymlet`                                 | non-default profiles |
| `SERVER_PORT` / `PORT` | `8080`                            | both               |
| `VITE_API_URL` | `https://gymlet.onrender.com`            | frontend build     |
| `gymlet.seed-legacy-demo` | `false`                        | dev-only (see below) |

---

## Accounts & security

- **Register** with a unique username + 4-digit PIN → automatically logged in.
- **Login** with the same credentials; sessions last 30 days and survive refreshes (opaque
  server-side token stored in the browser, sent as `Authorization: Bearer <token>`).
- **PINs are never stored in plain text** — BCrypt-hashed on the backend.
- **Logout** is one tap (top bar on mobile, sidebar on desktop, or anywhere a 401 occurs).
- Errors are friendly and specific: *Username already exists*, *Incorrect username or PIN*,
  *PIN must contain exactly 4 digits*, *Session expired*.

### Data isolation

Every user-owned row carries a `user_id`; the backend resolves the authenticated user from
the session token on **every** request and queries only that user's rows. A `userId` sent by
the client is never trusted. Workout plans, exercises, sessions, sets, notes, bodyweight,
PRs, and stats are all per-account.

### Migrating the pre-auth single-user data

If the database already contains the old single-user world (one profile, existing history),
the first boot after upgrade **keeps all of it**: the legacy user gets a username (lowercased
from the profile name, e.g. `athlete`), an independent copy of the split is made for them,
and all existing sessions/notes/bodyweight are re-pointed to it. The login screen then shows
a one-time **“Claim your account”** step: enter that username and choose a PIN. Nothing is
deleted.

---

## What's in the box

- **Login / Sign-up** — minimal, mobile-first screens. Create an account or claim a legacy one in seconds.
- **Today** — the core screen. Rest-day previews the next workout; training days have a one-click **Start Workout** that pre-fills every set with last week's weights/reps. Log each set (weight, reps, optional RIR), tap the pixel checkbox, watch the progress bar fill. A subtle rest timer auto-starts after each set (2:30 compound / 1:30 isolation) — pause, reset, or dismiss it. **Finish Workout** shows a clean summary: duration, sets, volume, new personal records (with a small celebration), and an encouraging message.
- **History** — every completed workout with date, sets, duration, exercises, volume. Tap one for the full day, per-set breakdown including notes and RIR. Delete is available.
- **Progress** — consistency calendar (workout / missed / rest squares), per-exercise strength charts with “last 8 workouts” weight progression, best set, estimated 1RM and total volume, weekly muscle-group volume with sets + frequency, personal records with streaks, and bodyweight tracking with a trend graph.
- **Profile** — name, units (kg/lb), week start day, full workout-split editor (reorder / sets / remove / add), exercise library CRUD, JSON export, sample-data removal, and reset.

### The 5-day split

Seeded exactly as specified: **Day 1 Back + Chest A · Day 2 Shoulders + Arms A · Day 3 Legs + Abs · Day 4 Back + Chest B · Day 5 Shoulders + Arms B** (26 exercises, 115 sets/week). Days 1–5 map onto your chosen start day; the two remaining weekdays are rest days. Everything is editable in Profile — and only for your account.

### Demo data

Sample history is only present on accounts migrated from the pre-auth app (where it already
existed) — it is never seeded into new accounts. It shows a “sample” badge in History and can
be wiped with one click (**Profile → Remove sample data**, or `POST /api/demo/remove`).
Dev-only: `gymlet.seed-legacy-demo=true` recreates the old single-user world (legacy user +
sample history) so the migration/claim flow can be tested end-to-end.

---

## Sample API endpoints

> Every endpoint below (except the auth ones marked 🌐) requires
> `Authorization: Bearer <token>`.

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| 🌐 POST | `/api/auth/register` | `{username, pin}` → `{token, username, name}` |
| 🌐 POST | `/api/auth/login` | `{username, pin}` → `{token, …}` |
| 🌐 POST | `/api/auth/claim` | One-time legacy claim (keeps existing data) |
| 🌐 GET  | `/api/auth/status` | `{legacyUsername}` — shown when a legacy account awaits claiming |
| GET  | `/api/auth/me` | Current user from the token |
| POST | `/api/auth/logout` | Invalidates the session |
| GET  | `/api/today` | Today's workout: structure + last-time info + next-session suggestions |
| POST | `/api/sessions` | Start today's workout (creates session + all set rows, pre-filled) |
| GET  | `/api/sessions` | Workout history |
| GET  | `/api/sessions/{id}` | Full session detail (sets, notes) |
| PUT  | `/api/sessions/{sessionId}/sets/{setId}` | Log/update a set `{weight, reps, rir, completed}` |
| POST | `/api/sessions/{sessionId}/notes/{exerciseId}` | Save an exercise note |
| POST | `/api/sessions/{id}/finish` | Finish → returns summary + PRs + message |
| DELETE | `/api/sessions/{id}` | Delete a workout |
| GET  | `/api/workout-days` | The full split structure |
| GET  | `/api/exercises` | Exercise library |
| PUT  | `/api/profile` | Name / units / start day |
| GET  | `/api/stats/strength` | Per-exercise progression |
| GET  | `/api/stats/muscles` | Weekly muscle-group volume |
| GET  | `/api/stats/prs` | Personal records + streaks |
| GET  | `/api/stats/calendar?year=&month=` | Month consistency grid |
| GET/POST/DELETE | `/api/bodyweight…` | Bodyweight logs + summary |
| GET  | `/api/export` | Download everything as JSON |
| POST | `/api/data/reset` | Wipe workout history + bodyweight |

### Example — register and log a set

```bash
# register (or login) → token
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"harsh","pin":"1234"}'
# → { "token": "…", "username": "harsh", "name": "harsh" }

curl -X PUT http://localhost:8080/api/sessions/21/sets/461 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"weight":60,"reps":8,"rir":1,"completed":true}'

curl -X POST http://localhost:8080/api/sessions/21/finish \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'
# → { "completedSets": 23, "totalVolume": 18942.5, "prs": [ … ], "message": "Nice session! …" }
```

---

## How the smart progression suggestion works

No AI — a simple, understandable rule based on your **last completed session** for that exercise:

- Average reps (at your top weight) hit the **top of the rep range** → *“Try +2.5 kg next time.”*
- Average reps **below the rep range** → *“Keep the same weight, build reps first.”*
- Anything in between → *“Keep the same weight.”*

Weights are stored in kilograms and converted for display (kg ↔ lb); increments are 2.5 kg / 5 lb.

---

## Deployment (Docker + Render)

Both apps have Dockerfiles (`backend/Dockerfile`, `frontend/Dockerfile`). The frontend build
bakes `VITE_API_URL` (default `https://gymlet.onrender.com`) into the bundle — set it as a
build-time env var if the API host differs. CORS allows `https://gymlet-1.onrender.com`,
`https://gymlet.onrender.com`, and `http://localhost:5173` — nothing else.

## Implementation notes

- **Auth**: BCrypt (`spring-security-crypto` only — no full Spring Security filter chain).
  Sessions are rows in `auth_session` with a 30-day expiry; one active session per user
  (logging in replaces the previous one). A servlet filter enforces auth before controllers;
  `UserContext` resolves the authenticated user per request.
- **Isolation**: `user_id` columns on `app_user`-owned entities; every repository query is
  scoped by the authenticated user. Ownership is verified on every read/update (a user
  requesting another user's session/day gets 404).
- **Estimated 1RM** uses the Epley formula: `weight × (1 + reps/30)`.
- **PRs** are detected at finish time per exercise (heaviest weight, most reps, best est. 1RM) plus best session volume — no permanent PR table to go stale.
- **Rest timers**: compound movements default to 2:30, isolation to 1:30 (driven by each exercise's `compound` flag — editable in the library).
- **Set logging is instant**: session rows are pre-created at start, edits save on blur/checkbox, and finishing flushes any pending edits.
- **Mobile-first**: bottom navigation under 900px, sidebar on desktop; set-row inputs have large touch targets; no horizontal scroll at 360–430px.
- **API errors** return `{ "error": "…" }` with proper status codes (401 unauthenticated, 400 validation, 409 duplicate username); server exceptions are logged but never echoed to clients.
- **Smoke test**: `cd backend && python smoke_test.py` (fresh install: auth, isolation, session flow) and `python smoke_test.py legacy` (migration + claim) — boots the jar and asserts the full API.

## Scripts

| Task | Command |
| ---- | ------- |
| Run backend (H2) | `cd backend && ./mvnw spring-boot:run` |
| Run backend (MySQL/Postgres) | `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` |
| Run frontend | `cd frontend && npm run dev` |
| Build frontend | `cd frontend && npm run build` |
| Backend smoke tests | `cd backend && python smoke_test.py` / `python smoke_test.py legacy` |
