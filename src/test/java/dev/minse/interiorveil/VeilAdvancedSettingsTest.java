package dev.minse.interiorveil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VeilAdvancedSettingsTest {
    @Test
    void overnightAccessWindowWrapsAcrossMidnight() {
        VeilAdvancedSettings settings = new VeilAdvancedSettings(
                1, null, 18000, 6000, 96, 1.25F, 3, 0.75F, true, false, 0x123456,
                false, 0, 64, 0, 20, false, false
        );

        assertTrue(settings.isAccessTime(19000));
        assertTrue(settings.isAccessTime(2000));
        assertFalse(settings.isAccessTime(12000));
    }

    @Test
    void accessAndKeyRevisionUpdatesAreImmutable() {
        UUID playerId = UUID.randomUUID();
        VeilAdvancedSettings original = VeilAdvancedSettings.defaults();
        VeilAdvancedSettings allowed = original.withAccess(playerId, "Player", true);
        VeilAdvancedSettings revoked = allowed.revokeAllKeys();

        assertTrue(original.allowedPlayers().isEmpty());
        assertEquals("Player", allowed.allowedPlayers().get(playerId));
        assertEquals(original.keyRevision() + 1, revoked.keyRevision());
        assertEquals(original.attackMode(), allowed.attackMode());
        assertEquals(original.attackTargetY(), revoked.attackTargetY());
    }
}
