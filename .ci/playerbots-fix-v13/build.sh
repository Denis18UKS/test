#!/usr/bin/env bash
set -euxo pipefail

cat .ci/playerbots-src/chunk-* | base64 -d > /tmp/base.tgz
echo '0a4d7fd86b8b17795a99db95bd2a058c5c17e80af1bde69cffad3f90863436f3  /tmp/base.tgz' | sha256sum -c -
rm -rf work && mkdir work
tar -xzf /tmp/base.tgz -C work

cp .ci/playerbots-gui-fix/MinecraftClientMixin.java work/src/main/java/dev/denis/playerbots/mixin/MinecraftClientMixin.java
cp .ci/playerbots-toggle/BotRadialScreen.java work/src/main/java/dev/denis/playerbots/client/screen/BotRadialScreen.java
cp .ci/playerbots-body-anchor/PlayerBotsClient.java work/src/main/java/dev/denis/playerbots/client/PlayerBotsClient.java
cp .ci/playerbots-body-anchor/ClientBotState.java work/src/main/java/dev/denis/playerbots/client/ClientBotState.java
cp .ci/playerbots-body-anchor/PossessedBodyVisual.java work/src/main/java/dev/denis/playerbots/client/PossessedBodyVisual.java
cp .ci/playerbots-possession-v9/PossessionRouting.java work/src/main/java/dev/denis/playerbots/core/PossessionRouting.java
cp .ci/playerbots-possession-v9/PossessionManager.java work/src/main/java/dev/denis/playerbots/bot/PossessionManager.java
cp .ci/playerbots-possession-v9/EntityPushMixin.java work/src/main/java/dev/denis/playerbots/mixin/EntityPushMixin.java
cp .ci/playerbots-possession-v9/ControlledBotView.java work/src/main/java/dev/denis/playerbots/client/ControlledBotView.java
cp .ci/playerbots-possession-v9/PlayerEntityRendererMixin.java work/src/main/java/dev/denis/playerbots/mixin/PlayerEntityRendererMixin.java
cp .ci/playerbots-possession-v9/HeldItemRendererMixin.java work/src/main/java/dev/denis/playerbots/mixin/HeldItemRendererMixin.java
cp .ci/playerbots-possession-v9/playerbots.mixins.json work/src/main/resources/playerbots.mixins.json
cp .ci/playerbots-gui-fix/test_configurator_activation.py work/dev-tests/test_configurator_activation.py
cp .ci/playerbots-toggle/test_radial_toggle.py work/dev-tests/test_radial_toggle.py
cp .ci/playerbots-possession-v9/test_real_body_anchor.py work/dev-tests/test_real_body_anchor.py
cp .ci/playerbots-possession-v9/test_first_person_possession.py work/dev-tests/test_first_person_possession.py
cp .ci/playerbots-possession-v9/test_inventory_separation.py work/dev-tests/test_inventory_separation.py
cp .ci/playerbots-possession-v9/PlayerBotsCoreTests.java work/dev-tests/PlayerBotsCoreTests.java
sed -i 's/mod_version=1.0.0/mod_version=1.0.4/' work/gradle.properties

cat .ci/playerbots-v11-overlay/part-* | base64 -d > /tmp/playerbots110-overlay.tgz
echo 'e07e5a01094b8eb3bc8a11917f34437d49a851322d8202de9f4fa15910ff9822  /tmp/playerbots110-overlay.tgz' | sha256sum -c -
tar -xzf /tmp/playerbots110-overlay.tgz -C work

rm -f work/src/main/java/dev/denis/playerbots/client/PossessedBodyVisual.java
rm -f work/src/main/java/dev/denis/playerbots/mixin/EntityTrackerMixin.java
rm -f work/src/main/java/dev/denis/playerbots/mixin/ServerPlayNetworkHandlerAccessor.java

cp .ci/playerbots120/PlayerBotsClient.java work/src/main/java/dev/denis/playerbots/client/PlayerBotsClient.java
cp .ci/playerbots120/BotRadialScreen.java work/src/main/java/dev/denis/playerbots/client/screen/BotRadialScreen.java
cp .ci/playerbots120/BotConnection.java work/src/main/java/dev/denis/playerbots/bot/BotConnection.java
cp .ci/playerbots120/PossessionManager.java work/src/main/java/dev/denis/playerbots/bot/PossessionManager.java
cp .ci/playerbots120/ServerPlayNetworkHandlerMixin.java work/src/main/java/dev/denis/playerbots/mixin/ServerPlayNetworkHandlerMixin.java
cp .ci/playerbots120/playerbots.mixins.json work/src/main/resources/playerbots.mixins.json
cp .ci/playerbots120/PlayerBotsMod.java work/src/main/java/dev/denis/playerbots/PlayerBotsMod.java
sed -i 's/BotConnection connection = new BotConnection();/BotConnection connection = new BotConnection(server, id);/' work/src/main/java/dev/denis/playerbots/bot/BotManager.java
sed -i 's/mod_version=1.1.0/mod_version=1.2.0/' work/gradle.properties
rm -f work/src/main/java/dev/denis/playerbots/mixin/ClientPlayerInteractionManagerMixin.java

base64 -d .ci/playerbots-fix-v13/patch.gz.b64 | gunzip > /tmp/playerbots-v13.patch
patch -d work -p1 < /tmp/playerbots-v13.patch

grep -Fx 'mod_version=1.3.0' work/gradle.properties

grep -F 'ClientPlayNetworking.send(PlayerBotsNetworking.BOT_INPUT' work/src/main/java/dev/denis/playerbots/client/BotInputController.java
grep -F 'radialKey.wasPressed()' work/src/main/java/dev/denis/playerbots/client/PlayerBotsClient.java
! grep -Fq 'radialKeyHeld' work/src/main/java/dev/denis/playerbots/client/screen/BotRadialScreen.java
grep -F 'extends HandledScreen<PlayerScreenHandler>' work/src/main/java/dev/denis/playerbots/client/screen/BotInventoryScreen.java
! grep -Fq 'InventoryScreenMixin' work/src/main/resources/playerbots.mixins.json

echo '=== Python regression suite ==='
for t in work/dev-tests/test_*.py; do python3 "$t"; done

echo '=== Java core tests ==='
rm -rf /tmp/pbcore && mkdir /tmp/pbcore
javac -d /tmp/pbcore work/src/main/java/dev/denis/playerbots/core/*.java work/dev-tests/PlayerBotsCoreTests.java
java -cp /tmp/pbcore PlayerBotsCoreTests

cd work
gradle wrapper --gradle-version 8.8
./gradlew --stacktrace clean build

JAR=build/libs/player-bots-1.3.0.jar
test -f "$JAR"
jar tf "$JAR" > jar-contents.txt
grep -Fx 'dev/denis/playerbots/client/screen/BotInventoryScreen.class' jar-contents.txt
grep -Fx 'dev/denis/playerbots/client/BotInventoryMirror.class' jar-contents.txt
grep -Fx 'dev/denis/playerbots/client/BotInputController.class' jar-contents.txt
! grep -Fq 'InventoryScreenMixin.class' jar-contents.txt
unzip -p "$JAR" playerbots.mixins.json > built-mixins.json

mkdir -p run
echo eula=true > run/eula.txt
rm -f /tmp/pb130-cmd
mkfifo /tmp/pb130-cmd
exec 3<>/tmp/pb130-cmd
./gradlew runServer --no-daemon < /tmp/pb130-cmd > server-player-count.log 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; }
trap cleanup EXIT
for i in $(seq 1 240); do
  grep -Fq 'Done (' server-player-count.log && break
  kill -0 "$PID"
  sleep 0.5
done
grep -F 'Done (' server-player-count.log

echo 'bot create BotOne' >&3
sleep 2
echo 'list' >&3
for i in $(seq 1 40); do
  grep -Eq 'There are 1 of a max of [0-9]+ players online:.*BotOne' server-player-count.log && break
  sleep 0.5
done
grep -E 'There are 1 of a max of [0-9]+ players online:.*BotOne' server-player-count.log

echo 'bot create BotTwo' >&3
sleep 2
echo 'list' >&3
for i in $(seq 1 40); do
  grep -Eq 'There are 2 of a max of [0-9]+ players online:.*Bot(One|Two).*Bot(One|Two)' server-player-count.log && break
  sleep 0.5
done
grep -E 'There are 2 of a max of [0-9]+ players online:.*Bot(One|Two).*Bot(One|Two)' server-player-count.log
! grep -Fq 'Duplicate entity UUID' server-player-count.log
! grep -Fq 'Failed to validate profile key' server-player-count.log
echo stop >&3
wait "$PID"
trap - EXIT

set +e
timeout --signal=TERM --kill-after=10s 120s xvfb-run -a ./gradlew runClient --no-daemon > client-smoke.log 2>&1
code=$?
set -e
if [ "$code" -ne 0 ] && [ "$code" -ne 124 ]; then cat client-smoke.log; exit "$code"; fi
! grep -Eq 'Mixin apply failed|InjectionError|InvalidMixinException|NoSuchMethodError|NoSuchFieldError|ClassCastException' client-smoke.log
grep -F 'Setting user:' client-smoke.log

zip -qr player-bots-1.3.0-sources.zip . -x '.gradle/*' 'build/*' 'run/*' 'server-player-count.log' 'client-smoke.log' 'jar-contents.txt' 'built-mixins.json' 'SHA256SUMS.txt'
unzip -t player-bots-1.3.0-sources.zip
sha256sum build/libs/player-bots-1.3.0.jar player-bots-1.3.0-sources.zip > SHA256SUMS.txt
cat SHA256SUMS.txt
