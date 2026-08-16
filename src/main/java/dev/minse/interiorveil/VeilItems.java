package dev.minse.interiorveil;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class VeilItems {
    public static final ResourceLocation VEIL_KEY_ID = InteriorVeil.id("veil_key");
    public static final ResourceKey<Item> VEIL_KEY_RESOURCE_KEY = ResourceKey.create(Registries.ITEM, VEIL_KEY_ID);
    public static final Item VEIL_KEY = Registry.register(
            BuiltInRegistries.ITEM,
            VEIL_KEY_RESOURCE_KEY,
            new Item(new Item.Properties().setId(VEIL_KEY_RESOURCE_KEY).stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final ResourceLocation TACTICAL_MAP_ID = InteriorVeil.id("tactical_map");
    public static final ResourceKey<Item> TACTICAL_MAP_RESOURCE_KEY = ResourceKey.create(Registries.ITEM, TACTICAL_MAP_ID);
    public static final Item TACTICAL_MAP = Registry.register(
            BuiltInRegistries.ITEM,
            TACTICAL_MAP_RESOURCE_KEY,
            new TacticalMapItem(new Item.Properties().setId(TACTICAL_MAP_RESOURCE_KEY).stacksTo(1).rarity(Rarity.RARE))
    );

    private VeilItems() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    entries.accept(VEIL_KEY);
                    entries.accept(TACTICAL_MAP);
                });
    }
}
