#!/usr/bin/env bash
# Starts Folia 26.1.2 with the built plugin, runs console commands, stops it, starts it again,
# and fails if the plugin self-test or persistence checks fail. Used only by CI.
set -euo pipefail

JAR=$(ls "$PWD"/jar/legendary_additions-*.jar | head -1)
mkdir -p server/plugins
cd server
URL=$(curl -fsS https://fill.papermc.io/v3/projects/folia/versions/26.1.2/builds/latest \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['downloads']['server:default']['url'])")
curl -fsS -o folia.jar "$URL"
cp "$JAR" plugins/
# CI test server only.
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
PROPS

boot() {
  local n=$1
  rm -f in && mkfifo in
  exec 3<>in
  java -Xmx2G -Dlegendaryadditions.selftest=true -jar folia.jar --nogui <in > "boot$n.log" 2>&1 &
  local pid=$!
  for _ in $(seq 1 240); do
    grep -q "SELFTEST COMPLETE" "boot$n.log" && break
    if ! kill -0 $pid 2>/dev/null; then echo "server $n exited early"; tail -100 "boot$n.log"; exit 1; fi
    sleep 1
  done
  grep -q "SELFTEST COMPLETE" "boot$n.log" || { echo "self-test $n did not finish"; tail -150 "boot$n.log"; exit 1; }
  for cmd in "stab" "stabshot" "nuke" "nukeshot" "lawnuke" "lawnukeshot" "withernuke" "withernukeshot" \
             "wolfrod" "wolfrod shot" "arrowrod" "arrowrodshot" "teleportshot" "stab NotOnline" "suggestions" "suggestionadmin" "admin"; do
    echo "$cmd" >&3
  done
  sleep 5
  echo "stop" >&3
  wait $pid || true
  exec 3>&-
}

boot 1
test -f plugins/LegendaryAdditions/rod-secret.key
KEY1=$(sha256sum plugins/LegendaryAdditions/rod-secret.key)
test -f plugins/LegendaryAdditions/suggestions.db
boot 2
KEY2=$(sha256sum plugins/LegendaryAdditions/rod-secret.key)

fail=0
for n in 1 2; do
  echo "===== boot $n: plugin lines ====="
  grep -E "LegendaryAdditions|SELFTEST|Admin\]" "boot$n.log" || true
  if grep -q "SELFTEST FAIL" "boot$n.log"; then echo "self-test failures in boot $n"; fail=1; fi
  grep -q "SELFTEST COMPLETE: all checks passed" "boot$n.log" || { echo "boot $n self-test not all passed"; fail=1; }
  if grep -nE "Exception|Error" "boot$n.log" | grep -iE "legendaryadditions|net\.srv" ; then echo "plugin exception in boot $n"; fail=1; fi
  # Every rod command must exist and reach the plugin (console gets a usage message, not "Unknown command").
  grep -q "Console must name a player" "boot$n.log" || { echo "rod commands did not respond in boot $n"; fail=1; }
  test "$(grep -c 'Console must name a player' "boot$n.log")" -ge 13 || { echo "not every rod command responded in boot $n"; fail=1; }
  grep -q "Player 'NotOnline' is not online" "boot$n.log" || { echo "player argument check missing in boot $n"; fail=1; }
  grep -q "Only players can open the suggestions GUI" "boot$n.log" || { echo "/suggestions missing in boot $n"; fail=1; }
  grep -q "Only players can open the suggestion admin GUI" "boot$n.log" || { echo "/suggestionadmin missing in boot $n"; fail=1; }
  grep -q "Only players can use /admin" "boot$n.log" || { echo "/admin missing in boot $n"; fail=1; }
  if grep -qi "Unknown or incomplete command" "boot$n.log"; then echo "an expected command is not registered in boot $n"; fail=1; fi
done
[ "$KEY1" = "$KEY2" ] || { echo "rod-secret.key changed across restart"; fail=1; }
ADMIN_DIR=$(find . -path '*dimensions/adminplugin/admin' -type d | head -1)
[ -n "$ADMIN_DIR" ] && echo "Admin dimension saved at $ADMIN_DIR" || { echo "Admin dimension folder not saved"; fail=1; }
exit $fail
