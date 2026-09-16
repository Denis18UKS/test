from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
client = (root / 'src/main/java/dev/denis/playerbots/client/PlayerBotsClient.java').read_text(encoding='utf-8')
mixin_path = root / 'src/main/java/dev/denis/playerbots/mixin/MinecraftClientMixin.java'
mixins = json.loads((root / 'src/main/resources/playerbots.mixins.json').read_text(encoding='utf-8'))

assert 'options.useKey.wasPressed()' not in client, (
    'Configurator activation still depends on END_CLIENT_TICK useKey.wasPressed(), '
    'which vanilla consumes before this callback runs.'
)
assert mixin_path.exists(), 'MinecraftClientMixin is missing; right-click is not intercepted before vanilla item/block use.'
text = mixin_path.read_text(encoding='utf-8')
assert '@Mixin(MinecraftClient.class)' in text
assert 'method = "doItemUse"' in text
assert '@At("HEAD")' in text
assert 'cancellable = true' in text
assert 'BotConfiguratorScreen' in text
assert 'ci.cancel()' in text
assert 'MinecraftClientMixin' in mixins.get('client', []), 'Client mixin is not registered in playerbots.mixins.json.'

print('CONFIGURATOR ACTIVATION REGRESSION TEST PASSED')
