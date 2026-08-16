package dev.minse.interiorveil;

import java.util.UUID;

record TransitionState(UUID barrierId, Direction direction, int ticksRemaining) {
    TransitionState tick() {
        return new TransitionState(barrierId, direction, ticksRemaining - 1);
    }

    enum Direction {
        ENTER,
        EXIT
    }
}
