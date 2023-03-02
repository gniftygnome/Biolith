package com.terraformersmc.biolith.impl.mixin;

import com.terraformersmc.biolith.impl.biome.BiolithFittestNodes;
import com.terraformersmc.biolith.impl.biome.BiomeCoordinator;
import com.terraformersmc.biolith.impl.biome.InterfaceSearchTree;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(MultiNoiseBiomeSource.class)
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class MixinMultiNoiseBiomeSource {
    @Shadow
    @Final
    private MultiNoiseUtil.Entries<RegistryEntry<Biome>> biomeEntries;

    private boolean biolith$isNether;
    private boolean biolith$isOverworld;

    // We have to evaluate what world we are in a *lot* so we want these answers precomputed as booleans we can check.
    @Inject(method = "<init>(Lnet/minecraft/world/biome/source/util/MultiNoiseUtil$Entries;Ljava/util/Optional;)V", at = @At("RETURN"))
    private void biolith$MultiNoiseBiomeSource(MultiNoiseUtil.Entries<RegistryEntry<Biome>> biomeEntries, Optional<MultiNoiseBiomeSource.Instance> instance, CallbackInfo ci) {
        biolith$isNether = instance.isPresent() && instance.get().preset().id.equals(new Identifier("nether"));
        biolith$isOverworld = instance.isPresent() && instance.get().preset().id.equals(new Identifier("overworld"));
    }

    // We calculate the vanilla/datapack biome, then we apply any overlays.
    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void biolith$getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
        MultiNoiseUtil.NoiseValuePoint noisePoint = noise.sample(x, y, z);
        BiolithFittestNodes<RegistryEntry<Biome>> fittestNodes =
                ((InterfaceSearchTree<RegistryEntry<Biome>>)(Object) biomeEntries.tree)
                        .biolith$searchTreeGet(noisePoint, MultiNoiseUtil.SearchTree.TreeNode::getSquaredDistance);

        cir.setReturnValue((fittestNodes.ultimate()).value);

        if (biolith$isOverworld) {
            cir.setReturnValue(BiomeCoordinator.OVERWORLD.getReplacement(x, y, z, noisePoint, fittestNodes));
        } else if (biolith$isNether) {
            cir.setReturnValue(BiomeCoordinator.NETHER.getReplacement(x, y, z, noisePoint, fittestNodes));
        }
    }
}
