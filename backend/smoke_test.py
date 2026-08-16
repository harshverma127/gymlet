"""End-to-end smoke test for the Gymlet API. Run against a live backend."""
import json
import sys
import urllib.request

BASE = "http://localhost:8080/api"
FAILED = 0


def req(method, path, body=None, expect=200):
    global FAILED
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(r) as resp:
            status = resp.status
            raw = resp.read().decode()
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read().decode()
    parsed = None
    try:
        parsed = json.loads(raw) if raw else None
    except Exception:
        pass
    if status != expect:
        FAILED += 1
        print(f"FAIL {method} {path} -> {status} (expected {expect}): {raw[:300]}")
    return parsed, status


def check(cond, msg):
    global FAILED
    if not cond:
        FAILED += 1
        print("FAIL:", msg)


# --- profile ---
p, _ = req("PUT", "/profile", {"name": "Athlete", "unit": "KG", "startDay": 7})
check(p and p["startDay"] == 7, "profile update")

# --- today ---
t, _ = req("GET", "/today")
check(t and not t["isRestDay"], "today is a training day")
check(t and t["dayNumber"] == 1 and t["workoutDayName"] == "Back + Chest A", "today day 1")
check(t and len(t["exercises"]) == 9, f"today has 9 exercises, got {len(t['exercises']) if t else 0}")
lat = next((e for e in t["exercises"] if e["name"] == "Lat Pulldown"), None)
check(lat is not None and lat["lastSets"] and lat["lastSets"][0]["weight"] > 0, "last time prefilled")

# --- start session ---
s, _ = req("POST", "/sessions", None, 201 if False else 200)
sid = s["id"]
check(s and s["workoutDayName"] == "Back + Chest A", "session started")
check(s and s["totalSets"] == 23, f"23 total sets, got {s['totalSets'] if s else 0}")
sets = s["sets"]
check(all(not x["completed"] for x in sets), "all sets start uncompleted")
check(all((x["weight"] or 0) > 0 for x in sets[:6]), "sets prefilled from last time")

# --- duplicate start should fail ---
_, status = req("POST", "/sessions", None, 400)
check(status == 400, "duplicate session rejected")

# --- update a set (complete it) ---
first = sets[0]
_, status = req("PUT", f"/sessions/{sid}/sets/{first['id']}",
                {"weight": 60, "reps": 8, "rir": 1, "completed": True})
check(status == 200, "set update ok")
_, status = req("PUT", f"/sessions/{sid}/sets/{sets[1]['id']}",
                {"weight": 60, "reps": 8, "rir": None, "completed": True})
check(status == 200, "set 2 update ok")
# completing without weight should fail
_, status = req("PUT", f"/sessions/{sid}/sets/{sets[2]['id']}",
                {"weight": None, "reps": None, "completed": True}, 400)
check(status == 400, "incomplete set rejected")

# --- notes ---
_, status = req("POST", f"/sessions/{sid}/notes/1", {"note": "Shoulder felt weird"})
check(status == 200, "note saved")
s2, _ = req("GET", f"/sessions/{sid}")
check(any(n["exerciseId"] == 1 and "weird" in n["note"] for n in s2["notes"]), "note in session")

# --- finish ---
f, status = req("POST", f"/sessions/{sid}/finish", {"durationMinutes": 48})
check(status == 200, "finish ok")
check(f and f["completedSets"] == 2 and f["totalSets"] == 23, "summary counts")
check(f and f["totalVolume"] > 0, "volume computed")
check(f and isinstance(f["prs"], list), "prs list present")
check(f and f["message"], "encouraging message")

# --- history ---
h, _ = req("GET", "/sessions")
check(h and h[0]["id"] == sid, "history newest first")
check(h and h[0]["setsCompleted"] == 2, "history set counts")
check(h and h[0]["durationMinutes"] == 48, "history duration")

# --- stats ---
st, _ = req("GET", "/stats/strength")
check(st and len(st) > 0, "strength progress")
ip = next((x for x in st if x["name"] == "Incline Bench"), None)
check(ip and len(ip["sessions"]) >= 1 and ip["bestSet"] is not None, "incline bench stats")
mus, _ = req("GET", "/stats/muscles")
check(mus and any(m["muscleGroup"] == "BACK" for m in mus), "muscle volume")
prs, _ = req("GET", "/stats/prs")
check(prs and prs["totalWorkouts"] > 0 and prs["bestSessionVolume"] > 0, "pr summary")
cal, _ = req("GET", "/stats/calendar?year=2026&month=8")
check(cal and any(d["status"] == "WORKOUT" for d in cal), "calendar")

# --- bodyweight ---
bw, _ = req("POST", "/bodyweight", {"date": "2026-08-10", "weightKg": 75.2})
check(bw and bw["weightKg"] == 75.2, "bodyweight added")
bws, _ = req("GET", "/bodyweight/summary")
check(bws and bws["current"] is not None and bws["current"] > 70, "bodyweight summary current")
check(bws and bws["weeklyAverage"] is not None and bws["weeklyAverage"] > 70, "bodyweight weekly avg")

# --- export ---
exp, status = req("GET", "/export")
check(status == 200, "export ok")

# --- cleanup: delete test session + bodyweight, restore profile ---
_, status = req("DELETE", f"/sessions/{sid}")
check(status == 200, "test session deleted")
_, status = req("DELETE", f"/bodyweight/{bw['id']}")
check(status == 200, "test bodyweight deleted")
p2, _ = req("PUT", "/profile", {"name": "Athlete", "unit": "KG", "startDay": 1})
check(p2 and p2["startDay"] == 1, "profile restored")

print("\n" + ("ALL PASSED" if FAILED == 0 else f"{FAILED} FAILURES"))
sys.exit(1 if FAILED else 0)
