from pathlib import Path

root = Path(__file__).resolve().parents[1]
possession = (root / 'src/main/java/dev/denis/playerbots/bot/PossessionManager.java').read_text(encoding='utf-8')
manager = (root / 'src/main/java/dev/denis/playerbots/bot/BotManager.java').read_text(encoding='utf-8')

for forbidden in [
    'target.getInventory().clone',
    'controller.getInventory().clone',
    'copyInventory',
    'cloneInventory',
]:
    assert forbidden not in possession + manager, f'Inventory separation broken by inventory copy operation: {forbidden}'

assert 'player.playerScreenHandler.syncState()' in possession, 'Target player inventory is not synced after possession switch.'
assert 'UpdateSelectedSlotS2CPacket' in possession, 'Target player selected hotbar slot is not synced after possession switch.'
assert 'player.getInventory().selectedSlot' in possession, 'Hotbar slot sync must come from the target bot/player inventory.'

print('INVENTORY SEPARATION REGRESSION TEST PASSED')
