package dev.minse.interiorveil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

final class VeilStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type BARRIER_LIST = new TypeToken<List<VeilBarrier>>() { }.getType();

    private VeilStore() {
    }

    static List<VeilBarrier> load(MinecraftServer server) {
        Path path = dataPath(server);
        if (!Files.exists(path)) {
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            List<VeilBarrier> barriers = GSON.fromJson(reader, BARRIER_LIST);
            return barriers == null
                    ? List.of()
                    : barriers.stream().map(VeilBarrier::normalized).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (IOException | RuntimeException exception) {
            InteriorVeil.LOGGER.error("Failed to load veil data from {}", path, exception);
            return List.of();
        }
    }

    static void save(MinecraftServer server, Collection<VeilBarrier> barriers) {
        Path path = dataPath(server);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(barriers, BARRIER_LIST, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            InteriorVeil.LOGGER.error("Failed to save veil data to {}", path, exception);
        }
    }

    private static Path dataPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("interiorveil").resolve("barriers.json");
    }
}
