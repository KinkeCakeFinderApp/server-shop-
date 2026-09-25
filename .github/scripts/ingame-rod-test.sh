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
# FoliaShop (the /shop plugin) with this repository's shops.yml, for the /shop admin test.
SHOP_URL=$(curl -fsS "https://api.modrinth.com/v2/project/foliashop/version" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['files'][0]['url'])")
curl -fsSL -o plugins/FoliaShop.jar "$SHOP_URL"
mkdir -p plugins/FoliaShop
cp "$ROOT/shops.yml" plugins/FoliaShop/shops.yml
# Start from a config written by 3.0.0 (visual-less, no block damage) to prove it gets upgraded.
mkdir -p plugins/LegendaryAdditions
cat > plugins/LegendaryAdditions/config.yml <<'YML'
rod-effects:
  sounds: false
  particles: false
orbital-strike:
  warning-time-ticks: 20
  destroy-blocks: false
  block-damage-power: 5.0
nuke:
  warning-time-ticks: 0
  destroy-blocks: false
  block-damage-power: 10.0
YML
# CI test server only.
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
view-distance=10
simulation-distance=10
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
grep -q "Updated config.yml" server.log || { echo "old config was not upgraded"; BOT=1; }
grep -q "nuke.style: rings" server.log || { echo "old config did not get the nuke rings"; BOT=1; }
grep -q "orbital-strike.fuse-ticks: 0" server.log || { echo "old config did not get the instant stab"; BOT=1; }
grep -q "law-nuke.spawn-height" server.log || { echo "old config did not get the sky Law Nuke"; BOT=1; }
grep -q "sphere of arrows" server.log || { echo "old config did not get the arrow sphere"; BOT=1; }
grep -qE "ArrowTarget.*was shot by RodTester" server.log || { echo "the arrow rod's arrows are not owned by the player who used it"; BOT=1; }
grep -E "ArrowTarget" server.log || true
grep -i "max-tnt-per-tick" server.log || true
echo "===== legendaries.db ====="
python3 - <<'PY' || BOT=1
import sqlite3
rows = sqlite3.connect("plugins/LegendaryAdditions/legendaries.db").execute(
    "SELECT id, material, abilities, enchants FROM legendaries").fetchall()
print(rows)
assert any(r[0] == "testpick" and r[1] == "minecraft:netherite_pickaxe" and "VEIN_MINE=1" in r[2]
           and "TREE_CAPITATOR=1" in r[2] and "minecraft:efficiency=10" in r[3] for r in rows), "testpick row missing or wrong"
PY
echo "===== shops.yml entries added by /shop admin ====="
grep -n -A30 "^      testpick:" plugins/FoliaShop/shops.yml || { echo "testpick not in shops.yml"; BOT=1; }
grep -q "legendary_additions give %player% testpick" plugins/FoliaShop/shops.yml || { echo "no give command for testpick"; BOT=1; }
python3 - <<'PY' || BOT=1
import yaml
items = yaml.safe_load(open("plugins/FoliaShop/shops.yml", encoding="utf-8"))["shops"]["legendary"]["items"]
print("testpick:", items.get("testpick"))
print("diamond_block:", items.get("diamond_block"))
assert items["testpick"]["give-item"] is False
assert items["testpick"]["buy-price"]["amount"] == 50000
assert items["diamond_block"]["material"] == "minecraft:diamond_block"
assert items["diamond_block"]["sell-price"]["amount"] == 300
heart = items["heart_of_the_sea"]
print("heart_of_the_sea:", heart)
assert heart["give-item"] is False
assert heart["buy-commands"] == ["[console] la_shopbuy %player% legendary heart_of_the_sea"]
assert heart["buy-price"] == {"provider": "xp_points", "amount": 0.0}
assert len(heart["legendaryadditions"]["cost"]) == 1
assert "&e - 2x Diamond" in heart["lore"]
PY
grep -iE "FoliaShop|foliashop" server.log | head -20 || true
exit $BOT
