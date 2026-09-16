from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
visual = root / 'src/main/java/dev/denis/playerbots/client/PossessedBodyVisual.java'
mixin = root / 'src/main/java/dev/denis/playerbots/mixin/PlayerEntityRendererMixin.java'
state = (root / 'src/main/java/dev/denis/playerbots/client/ClientBotState.java').read_text(encoding='utf-8')
mixins = json.loads((root / 'src/main/resources/playerbots.mixins.json').read_text(encoding='utf-8'))

assert visual.exists(), 'Missing client-side anchored real-body visual while possessing a bot.'
text = visual.read_text(encoding='utf-8')
assert 'captureBeforePossession' in text, 'Real body position must be captured before possession begins.'
assert 'OtherClientPlayerEntity' in text, 'Anchored body should render with the real player profile/skin.'
assert 'renderAnchoredBody' in text, 'Anchored body must be rendered at its saved position.'
assert 'clear' in text, 'Anchored body visual must be removed after release/disconnect.'

assert 'PossessedBodyVisual.captureBeforePossession()' in state, 'Control request must capture the body anchor before switching to a bot.'
assert 'PossessedBodyVisual.syncFromSnapshot' in state, 'Possession snapshot must activate/deactivate the anchored body visual.'

assert mixin.exists(), 'Local client player renderer must be hidden while a bot is possessed.'
renderer = mixin.read_text(encoding='utf-8')
assert 'PlayerEntityRenderer' in renderer
assert 'ClientBotState.controlled()' in renderer
assert 'ci.cancel()' in renderer
assert 'PlayerEntityRendererMixin' in mixins.get('client', []), 'Player renderer mixin is not registered.'

print('REAL BODY ANCHOR REGRESSION TEST PASSED')
