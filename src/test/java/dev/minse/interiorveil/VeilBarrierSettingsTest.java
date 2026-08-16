package dev.minse.interiorveil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VeilBarrierSettingsTest {
    @Test
    void securityModePersistsThroughSettingsUpdate() {
        VeilBarrier barrier = barrierWithVersion(2);
        VeilBarrier secured = barrier.withSettings(
                "보안 결계",
                40,
                64,
                48,
                10,
                40,
                300,
                true,
                0x33FFFF,
                0xFFD23F,
                true,
                0xAA44FF,
                VeilAdvancedSettings.defaults()
        );

        assertTrue(secured.securityMode());
        assertEquals(4, secured.settingsVersion());
    }

    @Test
    void versionOneSettingsMigrateUnlockedWithoutResettingValues() {
        VeilBarrier old = barrierWithVersion(1);
        UUID id = old.id();

        VeilBarrier migrated = old.normalized();

        assertEquals(id, migrated.id());
        assertEquals(40, migrated.radius());
        assertEquals(48, migrated.fogMargin());
        assertFalse(migrated.securityMode());
        assertEquals(4, migrated.settingsVersion());
    }

    private static VeilBarrier barrierWithVersion(int settingsVersion) {
        return new VeilBarrier(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "minecraft:overworld",
                4,
                80,
                9,
                40,
                48,
                112,
                48,
                "기존 결계",
                48,
                10,
                40,
                300,
                true,
                0x33FFFF,
                0xFFD23F,
                false,
                0xFFFFFF,
                null,
                settingsVersion,
                null,
                null
        );
    }
}
