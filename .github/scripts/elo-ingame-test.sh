#!/usr/bin/env bash
# CI-only: starts Folia 26.1.2 (offline mode, flat world) with EloRanks and LuckPerms, lets two
# mineflayer bots fight, claim kits and use the GUIs, restarts the server and checks that
# everything was saved. Fails if any check fails.
set -euo pipefail

ROOT=$PWD
JAR=$(ls "$ROOT"/jar/EloRanks-*.jar | head -1)
mkdir -p eloserver/plugins
cd eloserver
URL=$(curl -fsS https://fill.papermc.io/v3/projects/folia/versions/26.1.2/builds/latest \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['downloads']['server:default']['url'])")
curl -fsS -o folia.jar "$URL"
cp "$JAR" plugins/
# LuckPerms (Bukkit build, which supports Folia) from Modrinth.
LP_URL=$(curl -fsS 'https://api.modrinth.com/v2/project/luckperms/version?loaders=%5B%22bukkit%22%5D' \
  | python3 -c "
import json,sys
for v in json.load(sys.stdin):
    for f in v['files']:
        if 'bukkit' in f['filename'].lower() and 'legacy' not in f['filename'].lower():
            print(f['url']); sys.exit()
")
echo "LuckPerms: $LP_URL"
curl -fsSL -o plugins/LuckPerms.jar "$LP_URL"
# CI test server only.
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
view-distance=6
simulation-distance=6
spawn-protection=0
difficulty=easy
pvp=true
PROPS

START=0
boot() {
  local log=$1
  rm -f in && mkfifo in
  exec 3<>in
  java -Xmx2G -jar folia.jar --nogui <in > "$log" 2>&1 &
  PID=$!
  for _ in $(seq 1 240); do
    grep -q "Done (" "$log" && break
    kill -0 $PID 2>/dev/null || { tail -100 "$log"; exit 1; }
    sleep 1
  done
  grep -q "Done (" "$log" || { echo "server did not start"; tail -100 "$log"; exit 1; }
}
console() { echo "$*" >&3; }
stop_server() {
  console "stop"
  wait $PID || true
  exec 3>&-
}
# Prints what the console printed between two markers.
section() { awk "/\\[Server\\] BEGIN-$2/{f=1;next} /\\[Server\\] END-$2/{f=0} f" "$1"; }

RESULT=0
check() { if eval "$2"; then echo "CHECK PASS $1"; else echo "CHECK FAIL $1"; RESULT=1; fi; }

# ------------------------------------------------------------------ boot 1
boot boot1.log
( for _ in $(seq 1 120); do
    if grep -q "EloKiller joined the game" boot1.log; then
      console "op EloKiller"
      sleep 2
      # Unrelated LuckPerms data that EloRanks must leave alone.
      console "lp user EloKiller meta setprefix 50 &c[VIP]"
      console "lp user EloKiller permission set test.unrelated.permission true"
      break
    fi
    sleep 1
  done ) &

set +e
node "$ROOT/.github/scripts/elo-bot/test.js" one | tee bot1.log
BOT=${PIPESTATUS[0]}
set -e
[ "$BOT" = 0 ] || RESULT=1

sleep 3
console "say BEGIN-T1"
console "lp user EloKiller info"
console "lp user EloKiller meta info"
console "lp user EloKiller permission info"
sleep 5
console "say END-T1"
# Console admin command on an offline player: Tier 1 -> Tier 5.
console "elo set EloKiller 300"
sleep 4
console "say BEGIN-T5"
console "lp user EloKiller meta info"
sleep 5
console "say END-T5"
sleep 1
stop_server

echo "===== EloRanks / LuckPerms lines (boot 1) ====="
grep -E "EloRanks|LuckPerms|EloKiller|EloVictim" boot1.log | grep -v "lost connection" | head -80 || true
echo "===== LuckPerms at Tier 1 ====="; section boot1.log T1
echo "===== LuckPerms at Tier 5 ====="; section boot1.log T5

check "EloRanks enabled" 'grep -q "EloRanks enabled: 6 tiers, claim cooldown 2d 0h 0m" boot1.log'
check "LuckPerms hooked" 'grep -q "LuckPerms found: tiers are shown with a LuckPerms prefix" boot1.log'
check "no config problems" '! grep -q "\[EloRanks\] config.yml" boot1.log'
check "Tier 1 prefix set" 'section boot1.log T1 | grep -q "\[T1\]"'
check "VIP prefix kept" 'section boot1.log T1 | grep -q "\[VIP\]"'
check "unrelated permission kept" 'section boot1.log T1 | grep -q "test.unrelated.permission"'
check "tier meta set" 'section boot1.log T1 | grep -q "eloranks-tier"'
check "Tier 5 prefix replaced Tier 1 (no stacking)" 'section boot1.log T5 | grep -q "\[T5\]" && ! section boot1.log T5 | grep -q "\[T1\]"'
check "VIP prefix still kept" 'section boot1.log T5 | grep -q "\[VIP\]"'
check "combat log detected" 'grep -q "EloVictim disconnected in combat; counted as killed by EloKiller" boot1.log'

# ------------------------------------------------------------------ boot 2: persistence
boot boot2.log
set +e
node "$ROOT/.github/scripts/elo-bot/test.js" two | tee bot2.log
BOT=${PIPESTATUS[0]}
set -e
[ "$BOT" = 0 ] || RESULT=1
stop_server

echo "===== eloranks.db ====="
python3 - <<'PY' || RESULT=1
import sqlite3
db = sqlite3.connect("plugins/EloRanks/eloranks.db")
players = db.execute("SELECT name, elo, kills, deaths, elo_gained, elo_lost FROM players ORDER BY elo DESC").fetchall()
claims = db.execute("SELECT p.name, c.tier_id FROM claims c JOIN players p USING(uuid) ORDER BY 1, 2").fetchall()
unlocks = db.execute("SELECT p.name, u.tier_id FROM unlocks u JOIN players p USING(uuid) ORDER BY 1, 2").fetchall()
pending = db.execute("SELECT COUNT(*) FROM pending").fetchone()[0]
kills = db.execute("SELECT counted, reason FROM kills").fetchall()
print("players", players); print("claims", claims); print("unlocks", unlocks); print("pending", pending); print("kills", kills)
killer = [p for p in players if p[0] == "EloKiller"][0]
assert killer[1] == 300, killer
assert killer[2] == 1, "one counted kill"
assert all(p[1] >= 0 for p in players), "negative ELO"
assert len([u for u in unlocks if u[0] == "EloKiller"]) == 6, "all 6 tiers stay unlocked"
assert ("EloKiller", "tier-1") in claims and ("EloKiller", "tier-5") in claims
assert pending == 0, "every claimed reward was delivered"
assert (1, None) in kills and any(k[1] == "SAME_VICTIM_COOLDOWN" for k in kills)
print("DB OK")
PY

if grep -nE "Exception|Error" boot1.log boot2.log | grep -iE "eloranks|net\.srv"; then
  echo "EloRanks exception on the server"; RESULT=1
fi
exit $RESULT
