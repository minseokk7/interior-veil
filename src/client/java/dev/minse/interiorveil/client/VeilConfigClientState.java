package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.VeilConfigPayload;

public final class VeilConfigClientState {
    private static VeilConfigPayload current;

    private VeilConfigClientState() {
    }

    public static void accept(VeilConfigPayload payload) {
        current = payload;
    }

    public static VeilConfigPayload current() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
