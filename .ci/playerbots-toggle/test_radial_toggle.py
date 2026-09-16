from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = (root / 'src/main/java/dev/denis/playerbots/client/PlayerBotsClient.java').read_text(encoding='utf-8')
screen = (root / 'src/main/java/dev/denis/playerbots/client/screen/BotRadialScreen.java').read_text(encoding='utf-8')

assert 'radialKey.wasPressed()' in client, 'Radial menu must open from a discrete key press, not a held-key state.'
assert 'boolean held = radialKey.isPressed()' not in client, 'Hold-to-open logic is still present.'
assert 'wasHeld' not in client, 'Held-key edge tracking must be removed.'
assert 'isRadialKeyHeld' not in client, 'Radial screen must not depend on holding the key.'
assert 'matchesRadialKey' in client, 'Client must expose matching for the configurable radial key.'
assert 'PlayerBotsClient.matchesRadialKey(keyCode, scanCode)' in screen, 'Pressing the same configured key must close the radial menu.'
assert 'public void tick()' not in screen or 'isRadialKeyHeld' not in screen, 'Radial menu still auto-closes when the key is released.'
assert 'shouldCloseOnEsc' in screen, 'Escape close behavior should be explicit.'
print('RADIAL TOGGLE REGRESSION TEST PASSED')
