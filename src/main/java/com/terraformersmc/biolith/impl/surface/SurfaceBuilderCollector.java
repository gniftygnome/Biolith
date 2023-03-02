package com.terraformersmc.biolith.impl.surface;

import com.terraformersmc.biolith.api.surface.BiolithSurfaceBuilder;

import java.util.HashSet;
import java.util.Set;

public class SurfaceBuilderCollector {
    private static final Set<BiolithSurfaceBuilder> surfaceBuilders = new HashSet<>(64);

    public static boolean add(BiolithSurfaceBuilder surfaceBuilder) {
        return surfaceBuilders.add(surfaceBuilder);
    }

    public static Set<BiolithSurfaceBuilder> getBuilders() {
        return surfaceBuilders;
    }
}
