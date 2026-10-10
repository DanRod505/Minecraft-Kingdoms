package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/** Coat of arms: two colors and a charge. Rendering arrives with the Kingdom Book (Phase 9). */
public record Emblem(HeraldicColor primary, HeraldicColor secondary, Symbol symbol) {
    public enum Symbol {
        CROWN, LION, EAGLE, TOWER, SWORD, TREE, STAR, WHEAT;

        public static final Codec<Symbol> CODEC = Codecs.enumByName(Symbol.class);

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Emblem DEFAULT = new Emblem(HeraldicColor.BLUE, HeraldicColor.YELLOW, Symbol.CROWN);

    public static final Codec<Emblem> CODEC = RecordCodecBuilder.create(i -> i.group(
        HeraldicColor.CODEC.fieldOf("primary").forGetter(Emblem::primary),
        HeraldicColor.CODEC.fieldOf("secondary").forGetter(Emblem::secondary),
        Symbol.CODEC.fieldOf("symbol").forGetter(Emblem::symbol)
    ).apply(i, Emblem::new));
}
