import java.lang.reflect.Method;

public final class PlayerBotsCoreTests {
    public static void main(String[] args) throws Exception {
        testNameRules();
        testRadialSelection();
        testPossessionRouting();
        testPossessionCollisionRouting();
        System.out.println("CORE TESTS PASSED");
    }

    private static void testNameRules() throws Exception {
        Class<?> rules;
        try { rules = Class.forName("dev.denis.playerbots.core.BotNameRules"); }
        catch (ClassNotFoundException e) { throw new AssertionError("BotNameRules production class is missing"); }
        Method valid = rules.getMethod("isValid", String.class);
        expect((boolean) valid.invoke(null, "Bot_01"), "valid name rejected");
        expect(!(boolean) valid.invoke(null, ""), "empty name accepted");
        expect(!(boolean) valid.invoke(null, "0123456789ABCDEFG"), "17-char name accepted");
        expect(!(boolean) valid.invoke(null, "bad name"), "space accepted");
        expect(!(boolean) valid.invoke(null, "бот"), "non-ASCII name accepted");
    }

    private static void testRadialSelection() throws Exception {
        Class<?> math;
        try { math = Class.forName("dev.denis.playerbots.core.RadialMath"); }
        catch (ClassNotFoundException e) { throw new AssertionError("RadialMath production class is missing"); }
        Method sector = math.getMethod("sectorAt", double.class, double.class, double.class, int.class);
        expect((int) sector.invoke(null, 0.0, -100.0, 20.0, 4) == 0, "top must be sector 0");
        expect((int) sector.invoke(null, 100.0, 0.0, 20.0, 4) == 1, "right must be sector 1");
        expect((int) sector.invoke(null, 0.0, 100.0, 20.0, 4) == 2, "bottom must be sector 2");
        expect((int) sector.invoke(null, -100.0, 0.0, 20.0, 4) == 3, "left must be sector 3");
        expect((int) sector.invoke(null, 2.0, 3.0, 20.0, 4) == -1, "dead-zone must return -1");
        expect((int) sector.invoke(null, 0.0, -100.0, 20.0, 0) == -1, "zero sectors must return -1");
    }

    private static void testPossessionRouting() throws Exception {
        Class<?> routing;
        try { routing = Class.forName("dev.denis.playerbots.core.PossessionRouting"); }
        catch (ClassNotFoundException e) { throw new AssertionError("PossessionRouting production class is missing"); }
        Method suppress = routing.getMethod("shouldSuppressTracking", java.util.UUID.class, java.util.UUID.class, java.util.Map.class);
        java.util.UUID controller = java.util.UUID.randomUUID();
        java.util.UUID bot = java.util.UUID.randomUUID();
        java.util.UUID other = java.util.UUID.randomUUID();
        java.util.Map<java.util.UUID, java.util.UUID> ownerByControlled = new java.util.HashMap<>();
        ownerByControlled.put(bot, controller);
        expect((boolean) suppress.invoke(null, controller, bot, ownerByControlled), "controller body must be hidden from its possessed bot client");
        expect(!(boolean) suppress.invoke(null, other, bot, ownerByControlled), "unrelated entity must not be hidden");
        expect(!(boolean) suppress.invoke(null, controller, other, ownerByControlled), "unrelated recipient must not hide controller");
        Method bridge = routing.getMethod("needsWorldBridge", String.class, String.class);
        expect((boolean) bridge.invoke(null, "minecraft:overworld", "minecraft:the_nether"), "different dimensions need a bridge");
        expect(!(boolean) bridge.invoke(null, "minecraft:overworld", "minecraft:overworld"), "same dimension must not need a bridge");
    }

    private static void testPossessionCollisionRouting() throws Exception {
        Class<?> routing = Class.forName("dev.denis.playerbots.core.PossessionRouting");
        Method ignore = routing.getMethod("shouldIgnorePush", java.util.UUID.class, java.util.UUID.class, java.util.Map.class);
        java.util.UUID controller = java.util.UUID.randomUUID();
        java.util.UUID bot = java.util.UUID.randomUUID();
        java.util.UUID other = java.util.UUID.randomUUID();
        java.util.Map<java.util.UUID, java.util.UUID> ownerByControlled = new java.util.HashMap<>();
        ownerByControlled.put(bot, controller);
        expect((boolean) ignore.invoke(null, controller, bot, ownerByControlled), "controller and its controlled bot must not push each other");
        expect((boolean) ignore.invoke(null, bot, controller, ownerByControlled), "controlled bot and controller push suppression must be symmetric");
        expect(!(boolean) ignore.invoke(null, bot, other, ownerByControlled), "unrelated player must still collide with bot");
        expect(!(boolean) ignore.invoke(null, controller, other, ownerByControlled), "unrelated player must still collide with controller");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
