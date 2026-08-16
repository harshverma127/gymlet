"""End-to-end smoke test for Gymlet (multi-user).

Usage:
  python smoke_test.py            # tests the fresh-install auth + isolation flow
  python smoke_test.py legacy     # tests the legacy-data migration + claim flow
"""

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
passed = 0
failed = 0


def req(method, path, body=None, token=None, expect=None):
    global passed, failed
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r) as res:
            status = res.status
            raw = res.read().decode()
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read().decode()
    payload = None
    if raw:
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = raw
    if expect is not None:
        ok = status == expect
    else:
        ok = status < 400
    name = f"{method} {path}"
    if ok:
        passed += 1
        print(f"  ok  [{status}] {name}")
    else:
        failed += 1
        print(f"  FAIL [{status}] {name} -> {raw[:200]}")
    return status, payload


def check(name, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  ok  {name}")
    else:
        failed += 1
        print(f"  FAIL {name} {detail}")


def boot(extra_args=None):
    args = ["java", "-jar", "target/gymlet-backend-0.1.0.jar"]
    if extra_args:
        args += extra_args
    proc = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(60):
        try:
            req("GET", "/api/auth/status")
            return proc
        except Exception:
            time.sleep(1)
    raise RuntimeError("backend did not start")


def fresh_install_test():
    print("== fresh install: auth + isolation ==")
    req("GET", "/api/today", expect=401)
    req("GET", "/api/auth/me", expect=401)

    _, status = req("GET", "/api/auth/status")
    check("no legacy account on fresh install", status.get("legacyUsername") is None, str(status))

    _, me = req("POST", "/api/auth/register", {"username": "harsh", "pin": "12"}, expect=400)
    check("PIN must be 4 digits", me.get("error") == "PIN must contain exactly 4 digits", str(me))
    req("POST", "/api/auth/register", {"username": "harsh", "pin": "abcd"}, expect=400)
    req("POST", "/api/auth/register", {"username": "x", "pin": "1234"}, expect=400)

    _, r1 = req("POST", "/api/auth/register", {"username": "harsh", "pin": "1234"}, expect=200)
    token = r1["token"]
    check("register returns token", bool(token))
    req("POST", "/api/auth/register", {"username": "harsh", "pin": "0000"}, expect=400)
    _, dup = req("POST", "/api/auth/register", {"username": "HARSH", "pin": "0000"}, expect=400)
    check("duplicate username rejected (case-insensitive)", "exists" in dup.get("error", ""), str(dup))

    _, me = req("GET", "/api/auth/me", token=token)
    check("me returns username", me.get("username") == "harsh", str(me))

    _, today = req("GET", "/api/today", token=token)
    check("today has a plan", bool(today.get("workoutDayName")) and len(today.get("exercises", [])) >= 5, str(today)[:120])
    _, days = req("GET", "/api/workout-days", token=token)
    check("5 workout days copied", len(days) == 5, str(len(days)))
    _, exs = req("GET", "/api/exercises", token=token)
    check("exercise library copied", len(exs) >= 25, str(len(exs)))

    req("PUT", "/api/profile", {"name": "Harsh", "unit": "KG", "startDay": 7}, token=token, expect=200)
    _, s = req("POST", "/api/sessions", token=token, expect=200)
    session_id = s["id"]
    check("session started with sets", s.get("totalSets", 0) >= 20, str(s)[:100])
    set1 = s["sets"][0]
    req("PUT", f"/api/sessions/{session_id}/sets/{set1['id']}",
        {"weight": 60.0, "reps": 8, "rir": None, "completed": True}, token=token, expect=200)
    _, summary = req("POST", f"/api/sessions/{session_id}/finish", {"durationMinutes": 45}, token=token, expect=200)
    check("finish returns summary", summary.get("completedSets") == 1, str(summary)[:100])
    _, hist = req("GET", "/api/sessions", token=token)
    check("history has the session", len(hist) == 1, str(len(hist)))

    # second user
    _, r2 = req("POST", "/api/auth/register", {"username": "rahul", "pin": "5831"}, expect=200)
    token2 = r2["token"]
    _, hist2 = req("GET", "/api/sessions", token=token2)
    check("isolation: rahul sees no sessions", hist2 == [], str(hist2)[:120])
    _, days2 = req("GET", "/api/workout-days", token=token2)
    check("isolation: rahul has own plan", len(days2) == 5 and days2[0]["id"] != days[0]["id"], str(days2[0].get("id")))
    req("GET", f"/api/sessions/{session_id}", token=token2, expect=404)
    req("PUT", f"/api/sessions/{session_id}/sets/{set1['id']}",
        {"weight": 1, "reps": 1, "rir": None, "completed": True}, token=token2, expect=404)
    _, bw = req("GET", "/api/bodyweight", token=token2)
    check("isolation: rahul bodyweight empty", bw == [], str(bw))
    _, prs2 = req("GET", "/api/stats/prs", token=token2)
    check("isolation: rahul PR stats empty", prs2.get("totalWorkouts") == 0, str(prs2)[:100])

    # login / logout
    req("POST", "/api/auth/login", {"username": "harsh", "pin": "9999"}, expect=401)
    _, relog = req("POST", "/api/auth/login", {"username": "harsh", "pin": "1234"}, expect=200)
    # re-login replaces the previous session for the user
    req("GET", "/api/today", token=token, expect=401)
    _, still = req("GET", "/api/today", token=relog["token"])
    check("new session valid after re-login", bool(still.get("workoutDayName")), str(still)[:80])
    req("POST", "/api/auth/logout", token=relog["token"], expect=200)
    req("GET", "/api/today", token=relog["token"], expect=401)


def legacy_migration_test():
    print("== legacy migration + claim ==")
    # The web server accepts requests before the boot-time migration finishes.
    for _ in range(40):
        _, status = req("GET", "/api/auth/status")
        if status.get("legacyUsername") == "athlete":
            break
        time.sleep(1)
    check("legacy account offered for claim", status.get("legacyUsername") == "athlete", str(status))

    req("POST", "/api/auth/claim", {"username": "athlete", "pin": "12"}, expect=400)
    _, c = req("POST", "/api/auth/claim", {"username": "athlete", "pin": "1234"}, expect=200)
    token = c["token"]
    check("claim returns token", bool(token))

    _, today = req("GET", "/api/today", token=token)
    check("claimed account has a plan", today.get("workoutDayName"), str(today)[:100])
    _, hist = req("GET", "/api/sessions", token=token)
    check("demo history preserved", len(hist) >= 15, str(len(hist)))
    _, prof = req("GET", "/api/profile", token=token)
    check("profile preserved (Athlete)", prof.get("name") == "Athlete", str(prof))
    _, bw = req("GET", "/api/bodyweight/summary", token=token)
    check("bodyweight history preserved", bw.get("current") is not None, str(bw)[:80])
    _, exs = req("GET", "/api/exercises", token=token)
    check("exercise library claimed", len(exs) >= 25, str(len(exs)))

    req("POST", "/api/auth/register", {"username": "athlete", "pin": "9999"}, expect=400)
    req("POST", "/api/auth/claim", {"username": "athlete", "pin": "9999"}, expect=401)
    _, s = req("GET", "/api/auth/status")
    check("legacy account no longer offered", s.get("legacyUsername") is None, str(s))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "fresh"
    if mode == "legacy":
        proc = boot(["--gymlet.seed-legacy-demo=true"])
        try:
            legacy_migration_test()
        finally:
            proc.terminate()
    else:
        proc = boot([])
        try:
            fresh_install_test()
        finally:
            proc.terminate()
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
