package dev.denis.playerbots.core;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PossessionRouting {
    private PossessionRouting() {}

    public static boolean shouldSuppressTracking(UUID trackedEntityId, UUID recipientId,
                                                 Map<UUID, UUID> controllerByControlled) {
        if (trackedEntityId == null || recipientId == null || controllerByControlled == null) return false;
        UUID controllerId = controllerByControlled.get(recipientId);
        return trackedEntityId.equals(controllerId);
    }

    public static boolean shouldIgnorePush(UUID firstEntityId, UUID secondEntityId,
                                           Map<UUID, UUID> controllerByControlled) {
        if (firstEntityId == null || secondEntityId == null || controllerByControlled == null) return false;
        UUID firstController = controllerByControlled.get(firstEntityId);
        UUID secondController = controllerByControlled.get(secondEntityId);
        return secondEntityId.equals(firstController) || firstEntityId.equals(secondController);
    }

    public static boolean needsWorldBridge(String currentDimensionId, String targetDimensionId) {
        return !Objects.equals(currentDimensionId, targetDimensionId);
    }
}
