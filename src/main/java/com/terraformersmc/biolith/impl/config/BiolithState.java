package com.terraformersmc.biolith.impl.config;

import com.terraformersmc.biolith.impl.Biolith;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.biome.Biome;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BiolithState extends PersistentState {
    private final LinkedHashMap<RegistryKey<Biome>, Set<RegistryKey<Biome>>> biomeReplacements = new LinkedHashMap<>(64);
    private final ServerWorld world;

    private final String stateId;
    private static final int STATE_VERSION = 0;

    public BiolithState(ServerWorld world) {
        // All the 'the_'s annoy me so I don't use this one.  :P
        this(world, world.getDimensionKey().getValue().getPath());
    }

    public BiolithState(ServerWorld serverWorld, String name) {
        // Make sure we've got the server world stowed for state loads/saves.
        world = serverWorld;
        stateId = Biolith.MOD_ID + "_" + name + "_state";
        world.getPersistentStateManager().set(stateId, this);
        this.readState();
    }

    private void writeState() {
        this.markDirty();
        world.getPersistentStateManager().save();
    }

// TODO: lots of debug logging below
    private void readState() {
        NbtCompound nbt = null;
        NbtCompound nbtState = null;

        try {
            nbt = world.getPersistentStateManager().readNbt(stateId, STATE_VERSION);
        } catch (IOException e) {
            Biolith.LOGGER.debug("No saved state found for {}; starting anew...", stateId);
        }
        if (nbt != null && nbt.contains("data")) {
            int nbtVersion = nbt.getInt("DataVersion");
            nbtState = nbt.getCompound("data");
        }

Biolith.LOGGER.warn("{}: Starting read of state", stateId);
        biomeReplacements.clear();
        if (nbtState != null && !nbtState.isEmpty()) {
Biolith.LOGGER.warn("{}: State is not empty", stateId);
            NbtList biomeReplacementsNbt = nbtState.getList("BiomeReplacementsList", NbtList.LIST_TYPE);
            biomeReplacementsNbt.forEach(nbtElement -> {
Biolith.LOGGER.warn("{}: Reading state element: {}", stateId, nbtElement);
                NbtList replacementsNbt = (NbtList) nbtElement;
                RegistryKey<Biome> target = RegistryKey.of(RegistryKeys.BIOME, Identifier.tryParse(replacementsNbt.getString(0)));
                replacementsNbt.remove(0);
                biomeReplacements.put(target, replacementsNbt.stream()
                        .map(element -> RegistryKey.of(RegistryKeys.BIOME, Identifier.tryParse(element.asString())))
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
Biolith.LOGGER.warn("Resolved replacements list from NBT: {} -> {}", target, biomeReplacements.get(target));
            });
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
Biolith.LOGGER.warn("{}: Starting write of state", stateId);
        NbtList biomeReplacementsNbt = new NbtList();
        biomeReplacements.forEach((target, replacements) -> {
Biolith.LOGGER.warn("{}: Writing replacemnts for: {}", stateId, target.getValue());
            NbtList replacementsNbt = new NbtList();
            replacementsNbt.add(NbtString.of(target.getValue().toString()));
            replacementsNbt.addAll(replacements.stream().map(replacement -> NbtString.of(replacement.getValue().toString())).toList());
            biomeReplacementsNbt.add(replacementsNbt);
        });
Biolith.LOGGER.warn("{}: Describing biome replacemnts NBT:\n{}", stateId, biomeReplacementsNbt);
        nbt.put("BiomeReplacementsList", biomeReplacementsNbt);

        return nbt;
    }

    public Stream<RegistryKey<Biome>> getBiomeReplacements(RegistryKey<Biome> target) {
        if (biomeReplacements.containsKey(target)) {
            return biomeReplacements.get(target).stream();
        } else {
            return Stream.empty();
        }
    }

    public void addBiomeReplacements(RegistryKey<Biome> target, Stream<RegistryKey<Biome>> replacements) {
        if (biomeReplacements.containsKey(target)) {
            replacements.forEachOrdered(biomeReplacements.get(target)::add);
        } else {
            biomeReplacements.put(target, replacements.collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        this.markDirty();
    }
}
