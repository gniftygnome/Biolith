package com.terraformersmc.biolith.impl.biome;

import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.joml.Vector2f;

import java.util.function.Consumer;

public class OverworldBiomePlacement extends DimensionBiomePlacement {
    /*
    Each replacement list is a mapping:
    from [source biome key] or [source biome tag] to [a list of target biome entries with user-assigned probability]

    Replacements lists must be stable (persisted and persistently ordered).  It's probably as good as anything we
    can do if at every run we add any new biomes to the end of the list in random order and never remove or reorder.

    Removed biomes become "holes" that fall through to the underlying vanilla biome.  Added biomes will crowd others,
    causing the most movement for those closest to them (the former newest additions).

    If any biome specifies full replacement, omit the vanilla biome.  Otherwise, add it to the middle of the list
    with a desired weight of (1 - highest_given_weight).  Scale the list into [0,1].

    From left to right, add each scaled value until the result exceeds the normalized noise.  Return the biome
    that put the total over the noise, or Optional.empty() if it was the vanilla biome.


    Keys are required at writeBiomeParameters time, then later replaced biome key -> pair of replacement entry and rate
    ... and some kind of system for associated sub-biomes.  I think, separately so they can replace vanilla biomes too.
    Also by keys at writeBiomeParameters, and also then later by entries for the multi-noise.

    Proposal -- data store carries both in a record and has a complete() method to set the entries,
    compute the ratios, etc.
     */

    /*
     * Known conditions in the getReplacement functions, validated by MixinMultiNoiseBiomeSource:
     * - original != null
     * - original.hasKeyAndValue()
     */

    @Override
    public RegistryEntry<Biome> getReplacement(int x, int y, int z, MultiNoiseUtil.NoiseValuePoint noisePoint, BiolithFittestNodes<RegistryEntry<Biome>> fittestNodes) {
        RegistryEntry<Biome> biomeEntry = fittestNodes.ultimate().value;
        RegistryKey<Biome> biomeKey = biomeEntry.getKey().orElseThrow();

        double localNoise = normalize(replacementNoise.sample((double)x / 256D, (double)z / 256D));
        Vector2f localRange = null;

        // select phase one -- direct replacements
        if (replacementRequests.containsKey(biomeKey)) {
            double locus = 0D;
            for (ReplacementRequest request : replacementRequests.get(biomeKey).requests) {
                locus += request.scaled();
                if (locus > localNoise) {

                    localRange = new Vector2f((float) (locus - request.scaled()), (float) (locus > 0.9999f ? 1f : locus));
                    if (!request.biome().equals(VANILLA_PLACEHOLDER)) {
                        biomeEntry = request.biomeEntry();
                        biomeKey = request.biome();
                    }
                    break;
                }
            }
        }

        // select phase two -- sub-biome replacements
        if (subBiomeRequests.containsKey(biomeKey)) {
            for (SubBiomeRequest subRequest : subBiomeRequests.get(biomeKey).requests) {
                if (subRequest.matcher().matches(fittestNodes, noisePoint, localRange, (float) localNoise)) {
                    biomeEntry = subRequest.biomeEntry();
                    biomeKey = subRequest.biome();
                    break;
                }
            }
        }

        /*
        // TODO: TESTING
        double ratio;
        if (fittestNodes.ultimateDistance() == 0) {
            ratio = 0D;
        } else {
            ratio = ((double) fittestNodes.ultimateDistance()) / ((double) fittestNodes.penultimateDistance());
        }
        if (ratio > 0.8D) {
            return biomeRegistry.getEntry(biomeRegistry.get(BiomeKeys.MUSHROOM_FIELDS));
        }

        // TODO: TESTING
        RegistryEntry<Biome> selected = replacement.orElse(original);
        if (selected.matchesKey(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrestria", "lush_desert")))) {
            if (MultiNoiseUtil.toFloat(noisePoint.weirdnessNoise()) > -0.250f) {
                return biomeRegistry.getEntry(biomeRegistry.get(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrestria", "oasis"))));
            }
        }
        */

        return biomeEntry;
    }

    // NOTE:  biomeRegistry is not yet available when writeBiomeParameters() is called by VanillaBiomeParameters.
    public void writeBiomeParameters(Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters) {
        biomesInjected = true;

        // Replacement biomes are placed out-of-range so they do not generate except as replacements.
        // This adds the biome to MultiNoiseBiomeSource and BiomeSource so features and structures will place.
        replacementRequests.values().stream()
                .flatMap(requestSet -> requestSet.requests.stream())
                .map(ReplacementRequest::biome).distinct()
                .forEach(biome -> {
                    // Theoretically unnecessary safety; replacementRequests.complete() has not been called yet.
                    if (!biome.equals(VANILLA_PLACEHOLDER)) {
                        parameters.accept(Pair.of(OUT_OF_RANGE, biome));
                    }
                });

        // TODO: TESTING
        parameters.accept(Pair.of(OUT_OF_RANGE, RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrestria", "oasis"))));
    }
}
