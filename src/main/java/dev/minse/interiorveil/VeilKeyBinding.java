package dev.minse.interiorveil;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;

final class VeilKeyBinding {
    private static final String BARRIER_ID = "interiorveil_barrier_id";
    private static final String KEY_REVISION = "interiorveil_key_revision";

    private VeilKeyBinding() {
    }

    static void bind(ItemStack stack, VeilBarrier barrier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(BARRIER_ID, barrier.id().toString());
            tag.putInt(KEY_REVISION, barrier.advanced().keyRevision());
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(barrier.name() + " 열쇠"));
    }

    static void unbind(ItemStack stack, UUID barrierId) {
        if (!barrierId(stack).filter(barrierId::equals).isPresent()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BARRIER_ID));
    }

    static Optional<UUID> barrierId(ItemStack stack) {
        if (!stack.is(VeilItems.VEIL_KEY) && !stack.is(VeilItems.TACTICAL_MAP)) {
            return Optional.empty();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getString(BARRIER_ID)
                .flatMap(VeilKeyBinding::parseUuid);
    }

    static int revision(ItemStack stack) {
        if (!stack.is(VeilItems.VEIL_KEY) && !stack.is(VeilItems.TACTICAL_MAP)) {
            return 0;
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getInt(KEY_REVISION)
                .orElse(1);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
