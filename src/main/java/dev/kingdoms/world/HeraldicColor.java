package dev.kingdoms.world;

import com.mojang.serialization.Codec;

import java.util.Locale;

/** The 16 dye colors, kept free of Minecraft classes so domain code stays unit-testable. */
public enum HeraldicColor {
    WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY,
    LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK;

    public static final Codec<HeraldicColor> CODEC = Codecs.enumByName(HeraldicColor.class);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
