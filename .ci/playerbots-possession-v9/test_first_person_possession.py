from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
renderer = (root / 'src/main/java/dev/denis/playerbots/mixin/PlayerEntityRendererMixin.java').read_text(encoding='utf-8')
view_path = root / 'src/main/java/dev/denis/playerbots/client/ControlledBotView.java'
held_path = root / 'src/main/java/dev/denis/playerbots/mixin/HeldItemRendererMixin.java'
mixins = json.loads((root / 'src/main/resources/playerbots.mixins.json').read_text(encoding='utf-8'))

assert view_path.exists(), 'Missing helper that resolves the currently controlled bot client entity.'
view = view_path.read_text(encoding='utf-8')
assert 'ClientBotState.controlled()' in view
assert 'AbstractClientPlayerEntity' in view
assert 'getUuid().equals(controlled.uuid())' in view

assert 'client.options.getPerspective().isFirstPerson()' in renderer, 'Controlled bot body must only be hidden from its own first-person camera.'
assert 'player.getUuid().equals(controlled.uuid())' in renderer, 'The tracked controlled bot entity must be hidden in first person to avoid rendering its face over the camera.'

assert held_path.exists(), 'Missing held-item renderer mixin for using the controlled bot skin/arm in first person.'
held = held_path.read_text(encoding='utf-8')
assert 'HeldItemRenderer' in held
assert 'renderRightArm' in held and 'renderLeftArm' in held
assert 'ControlledBotView.playerForFirstPersonArm' in held
assert 'HeldItemRendererMixin' in mixins.get('client', []), 'HeldItemRendererMixin is not registered.'

print('FIRST PERSON POSSESSION REGRESSION TEST PASSED')
