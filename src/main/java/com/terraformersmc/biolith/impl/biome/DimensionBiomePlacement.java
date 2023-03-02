package com.terraformersmc.biolith.impl.biome;

import com.mojang.datafixers.util.Pair;
import com.terraformersmc.biolith.api.biome.SubBiomeMatcher;
import com.terraformersmc.biolith.impl.Biolith;
import com.terraformersmc.biolith.impl.config.BiolithState;
import com.terraformersmc.terraform.noise.OpenSimplexNoise;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.*;
import java.util.function.Consumer;

public abstract class DimensionBiomePlacement {
    protected boolean biomesInjected = false;
    protected Registry<Biome> biomeRegistry;
    protected BiolithState state;
    protected OpenSimplexNoise replacementNoise;
    protected Random seedRandom;
    protected final HashMap<RegistryKey<Biome>, ReplacementRequestSet> replacementRequests = new HashMap<>(256);
    protected final HashMap<RegistryKey<Biome>, SubBiomeRequestSet> subBiomeRequests = new HashMap<>(256);

    public static final MultiNoiseUtil.ParameterRange DEFAULT_PARAMETER = MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f);
    public static final MultiNoiseUtil.NoiseHypercube OUT_OF_RANGE = MultiNoiseUtil.createNoiseHypercube(3.01f, 3.01f, 3.01f, 3.01f, 3.01f, 3.01f, 3.01f);

    public static final RegistryKey<Biome> VANILLA_PLACEHOLDER = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(Biolith.MOD_ID, "vanilla"));

    protected void serverReplaced(BiolithState state, long seed) {
        DynamicRegistryManager.Immutable registryManager = BiomeCoordinator.getRegistryManager();
        if (registryManager == null) {
            throw new IllegalStateException("Registry manager is null during biome replacement setup!");
        } else {
            biomeRegistry = registryManager.get(RegistryKeys.BIOME);
        }
        this.state = state;
        replacementNoise = new OpenSimplexNoise(seed);
        seedRandom = new Random(seed);
        replacementRequests.forEach((biomeKey, requestSet) -> requestSet.complete(biomeRegistry));
        subBiomeRequests.forEach((biomeKey, requestSet) -> requestSet.complete(biomeRegistry));
    }

    public void addReplacement(RegistryKey<Biome> target, RegistryKey<Biome> biome, double rate) {
        if (biomesInjected) {
            Biolith.LOGGER.warn("Biolith's BiomePlacement.addReplacement() called too late for biome: {}", biome.getValue());
        }

        replacementRequests.computeIfAbsent(target, ReplacementRequestSet::new).addRequest(biome, rate);
    }

    public void addSubBiome(RegistryKey<Biome> target, RegistryKey<Biome> biome, SubBiomeMatcher matcher) {
        if (biomesInjected) {
            Biolith.LOGGER.warn("Biolith's BiomePlacement.addSubBiome() called too late for biome: {}", biome.getValue());
        }

        subBiomeRequests.computeIfAbsent(target, SubBiomeRequestSet::new).addRequest(biome, matcher);
    }


    public abstract RegistryEntry<Biome> getReplacement(int x, int y, int z, MultiNoiseUtil.NoiseValuePoint noisePoint, BiolithFittestNodes<RegistryEntry<Biome>> fittestNodes);

    public abstract void writeBiomeParameters(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters);

    // This is a lazy, stupid, wrong approximation of normalizing simplex values in [-1,1] to unbiased values in [0,1].
    protected double normalize(double value) {
        return MathHelper.clamp((value / 0.6D + 1D) / 2D, 0D, 1D);
    }

    protected record ReplacementRequest(RegistryKey<Biome> biome, double rate, RegistryEntry<Biome> biomeEntry, double scaled, boolean isComplete) {
        static ReplacementRequest of(RegistryKey<Biome> biome, double rate) {
            return new ReplacementRequest(biome, rate, null, rate, false);
        }

        @Override
        public int hashCode() {
            return biome.hashCode();
        }

        ReplacementRequest complete(Registry<Biome> biomeRegistry, double scaled) {
            if (this.isComplete) {
                return this;
            } else {
                return new ReplacementRequest(biome, rate, biomeRegistry.getEntry(biomeRegistry.getOrThrow(biome)), scaled, true);
            }
        }
    }

    protected class ReplacementRequestSet {
        RegistryKey<Biome> target;
        List<ReplacementRequest> requests = new ArrayList<>(8);
        double scale = 0D;

        ReplacementRequestSet(RegistryKey<Biome> target) {
            this.target = target;
        }

        void addRequest(RegistryKey<Biome> biome, double rate) {
            addRequest(ReplacementRequest.of(biome, rate));
        }

        void addRequest(ReplacementRequest request) {
            if (requests.contains(request)) {
                Biolith.LOGGER.info("Ignoring request for duplicate biome replacement: {}", request.biome);
            } else {
                requests.add(request);
            }
        }

        // TODO: buggy at server restart (need to address re-submitting or re-finalizing entries)
        void complete(Registry<Biome> biomeRegistry) {
            double totalScale = 0D;

            // Calculate biome distribution scale.
            for (ReplacementRequest request : requests) {
                totalScale += request.rate;
                if (request.rate > scale) {
                    scale = request.rate;
                }
            }
            double fullScale = totalScale / scale;

            // Add a special request with a place-holder for the vanilla biome, if/when it still generates.
            requests.add(new ReplacementRequest(VANILLA_PLACEHOLDER, 0D, null, 1.0D - scale, true));

            // Update saved state with any additions and fetch the new order.
            Collections.shuffle(requests, seedRandom);
            state.addBiomeReplacements(target, requests.stream().map(ReplacementRequest::biome));
            List<RegistryKey<Biome>> sortOrder = state.getBiomeReplacements(target).toList();

            // Finalize the request list and store it in state order.
            requests = requests.stream()
                    .map(request -> request.complete(biomeRegistry, request.rate / fullScale))
                    .sorted(Comparator.comparingInt(request -> sortOrder.indexOf(request.biome)))
                    .toList();
        }
    }

    protected record SubBiomeRequest(RegistryKey<Biome> biome, SubBiomeMatcher matcher, RegistryEntry<Biome> biomeEntry, boolean isComplete) {
        static SubBiomeRequest of(RegistryKey<Biome> biome, SubBiomeMatcher matcher) {
            return new SubBiomeRequest(biome, matcher, null, false);
        }

        @Override
        public int hashCode() {
            // TODO: plus somehow consider the validity ranges
            return biome.hashCode();
        }

        SubBiomeRequest complete(Registry<Biome> biomeRegistry) {
            if (this.isComplete) {
                return this;
            } else {
                return new SubBiomeRequest(biome, matcher, biomeRegistry.getEntry(biomeRegistry.getOrThrow(biome)), true);
            }
        }
    }

    protected class SubBiomeRequestSet {
        RegistryKey<Biome> target;
        List<SubBiomeRequest> requests = new ArrayList<>(8);

        SubBiomeRequestSet(RegistryKey<Biome> target) {
            this.target = target;
        }

        void addRequest(RegistryKey<Biome> biome, SubBiomeMatcher matcher) {
            addRequest(SubBiomeRequest.of(biome, matcher));
        }

        void addRequest(SubBiomeRequest request) {
            // TODO: should we even check anything here?
            if (requests.contains(request)) {
                Biolith.LOGGER.info("Ignoring request for duplicate sub-biome: {} -> {}", target, request.biome);
            } else {
                requests.add(request);
            }
        }

        void complete(Registry<Biome> biomeRegistry) {
            // Finalize the request list and store it in a somewhat stable order.
            requests = requests.stream()
                    .map(request -> request.complete(biomeRegistry))
                    .sorted(Comparator.comparing(request -> request.biome.getValue()))
                    .toList();
        }
    }
}