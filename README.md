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
| Database  | MySQL 8 (primary) · H2 (zero-config default)  |
| API       | REST + JSON                                   |
| Charts    | Hand-rolled SVG (no chart library)            |

No auth, no social, no subscriptions — it's a single-user app that just works locally.

---

## Project structure

```
gymlet/
├── backend/                     # Spring Boot REST API
│   ├── src/main/java/com/gymlet/
│   │   ├── GymletApplication.java
│   │   ├── config/              # DataSeeder (split + demo data)
│   │   ├── domain/              # JPA entities (User, WorkoutDay, Exercise, …)
│   │   ├── repository/          # Spring Data repositories
│   │   ├── service/             # Structure, Session, Stats, BodyWeight, Profile
│   │   └── web/                 # Controllers, DTOs, error handler
│   ├── src/main/resources/
│   │   ├── application.yml          # default profile → H2 (in-memory)
│   │   └── application-mysql.yml    # MySQL profile
│   ├── smoke_test.py            # end-to-end API smoke test
│   ├── mvnw / mvnw.cmd          # Maven wrapper (no local Maven needed)
│   └── pom.xml
├── frontend/                    # React + Vite SPA
│   ├── src/
│   │   ├── api/client.ts        # typed fetch wrapper
│   │   ├── components/          # icons, ui primitives, charts, calendar, toast
│   │   ├── lib/                 # units + date formatting
│   │   ├── pages/               # Today, History, HistoryDetail, Progress, Profile
│   │   ├── styles.css           # the cozy-pixel design system
│   │   └── state.tsx            # profile/units context
│   └── vite.config.ts           # dev proxy /api → localhost:8080
├── docker-compose.yml           # optional MySQL 8
└── README.md
```

---

## Quick start (zero-config, 2 terminals)

**Requirements:** Java 17+ (tested on 25) and Node 20+.

```bash
# 1. Backend — uses an embedded H2 database, seeds the split + demo data automatically
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

Open **http://localhost:5173** — done. The dev server proxies `/api` to the backend, so no CORS setup needed.

> Note: the default profile uses an in-memory H2 database — data resets on backend restart. For persistent data use MySQL below.

---

## Database setup (MySQL)

### Option A — Docker (recommended)

```bash
docker compose up -d          # starts MySQL 8 with database `gymlet`
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

### Option B — existing MySQL server

Create a database (e.g. `gymlet`) and a user, then override via environment variables:

```bash
export DB_URL='jdbc:mysql://localhost:3306/gymlet?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER=gymlet
export DB_PASSWORD=yourpassword
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

The schema is created automatically (`ddl-auto: update`) — there are no migration files to run.

### Environment variables

| Variable     | Default                                        | Used in |
| ------------ | ---------------------------------------------- | ------- |
| `DB_URL`     | `jdbc:mysql://localhost:3306/gymlet?…`         | mysql profile |
| `DB_USER`    | `gymlet`                                       | mysql profile |
| `DB_PASSWORD`| `gymlet`                                       | mysql profile |
| `SERVER_PORT`| `8080`                                         | both    |

---

## What's in the box

- **Today** — the core screen. Rest-day previews the next workout; training days have a one-click **Start Workout** that pre-fills every set with last week's weights/reps. Log each set (weight, reps, optional RIR), tap the pixel checkbox, watch the progress bar fill. A subtle rest timer auto-starts after each set (2:30 compound / 1:30 isolation) — pause, reset, or dismiss it. **Finish Workout** shows a clean summary: duration, sets, volume, new personal records (with a small celebration), and an encouraging message.
- **History** — every completed workout with date, sets, duration, exercises, volume. Tap one for the full day, per-set breakdown including notes and RIR. Delete is available.
- **Progress** — consistency calendar (workout / missed / rest squares), per-exercise strength charts with "last 8 workouts" weight progression, best set, estimated 1RM and total volume, weekly muscle-group volume with sets + frequency, personal records with streaks, and bodyweight tracking with a trend graph.
- **Profile** — name, units (kg/lb), week start day, full workout-split editor (reorder / sets / remove / add), exercise library CRUD, JSON export, sample-data removal, and reset.

### The 5-day split

Seeded exactly as specified: **Day 1 Back + Chest A · Day 2 Shoulders + Arms A · Day 3 Legs + Abs · Day 4 Back + Chest B · Day 5 Shoulders + Arms B** (26 exercises, 115 sets/week). Days 1–5 map onto your chosen start day; the two remaining weekdays are rest days. Everything is editable in Profile.

### Demo data

On first launch the app seeds ~4 weeks of realistic sample history and ~2 months of bodyweight so the analytics look alive. Every sample session/entry is flagged — it shows a "sample" badge in History and can be wiped with one click (**Profile → Remove sample data**, or `POST /api/demo/remove`). The workout split itself is your real template, not demo data.

---

## Sample API endpoints

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
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

### Example — logging a set

```bash
curl -X PUT http://localhost:8080/api/sessions/21/sets/461 \
  -H "Content-Type: application/json" \
  -d '{"weight":60,"reps":8,"rir":1,"completed":true}'
```

```bash
curl -X POST http://localhost:8080/api/sessions/21/finish \
  -H "Content-Type: application/json" \
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

## Implementation notes

- **Estimated 1RM** uses the Epley formula: `weight × (1 + reps/30)`.
- **PRs** are detected at finish time per exercise (heaviest weight, most reps, best est. 1RM) plus best session volume — no permanent PR table to go stale.
- **Rest timers**: compound movements default to 2:30, isolation to 1:30 (driven by each exercise's `compound` flag — editable in the library).
- **Set logging is instant**: session rows are pre-created at start, edits save on blur/checkbox, and finishing flushes any pending edits.
- **Single user**: `app_user` holds name, units, and week-start day; no accounts.
- **API errors** return `{ "error": "…" }` with proper status codes; the frontend surfaces them as toasts with friendly copy.
- **A11y**: keyboard-focusable controls, labelled inputs, `prefers-reduced-motion` support, semantic landmarks.
- **Smoke test**: `python backend/smoke_test.py` (with the backend running) exercises the whole API flow: profile, today, start, log sets, notes, finish, history, stats, bodyweight, export, cleanup.

## Scripts

| Task | Command |
| ---- | ------- |
| Run backend (H2) | `cd backend && ./mvnw spring-boot:run` |
| Run backend (MySQL) | `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` |
| Run frontend | `cd frontend && npm run dev` |
| Build frontend | `cd frontend && npm run build` |
| Backend tests | `cd backend && ./mvnw test` (see `smoke_test.py`) |
