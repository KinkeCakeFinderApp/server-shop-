#!/usr/bin/env bash
# CI-only: starts Folia 26.1.2 (offline mode, flat world) with the plugin, lets a mineflayer bot
# join, ops it, and has it cast every rod. Fails if the bot reports any failed check.
set -euo pipefail

ROOT=$PWD
JAR=$(ls "$ROOT"/jar/legendary_additions-*.jar | head -1)
mkdir -p ingame/plugins
cd ingame
URL=$(curl -fsS https://fill.papermc.io/v3/projects/folia/versions/26.1.2/builds/latest \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['downloads']['server:default']['url'])")
curl -fsS -o folia.jar "$URL"
cp "$JAR" plugins/
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
PROPS

rm -f in && mkfifo in
exec 3<>in
java -Xmx2G -jar folia.jar --nogui <in > server.log 2>&1 &
PID=$!
for _ in $(seq 1 240); do
  grep -q "Done (" server.log && break
  kill -0 $PID 2>/dev/null || { tail -100 server.log; exit 1; }
  sleep 1
done
grep -q "Done (" server.log || { echo "server did not start"; tail -100 server.log; exit 1; }

( for _ in $(seq 1 120); do
    if grep -q "RodTester joined the game" server.log; then echo "op RodTester" >&3; break; fi
    sleep 1
  done ) &

set +e
node "$ROOT/.github/scripts/rod-bot/test.js" | tee bot.log
BOT=${PIPESTATUS[0]}
set -e

echo "stop" >&3
wait $PID || true
exec 3>&-

echo "===== plugin lines ====="
grep -E "LegendaryAdditions|RodTester" server.log || true
if grep -nE "Exception|Error" server.log | grep -iE "legendaryadditions|net\.srv"; then
  echo "plugin exception on the server"; BOT=1
fi
exit $BOT
