package dev.kingdoms.orders;

import com.mojang.serialization.Codec;
import dev.kingdoms.world.Citizen.Profession;
import dev.kingdoms.world.Codecs;

import java.util.Locale;

/** What an order asks for, and which profession carries it out (GDD 3.3, 4.4). */
public enum OrderType {
    FARM(Profession.FARMER, false),
    CHOP(Profession.LUMBERJACK, true),
    MINE(Profession.MINER, true),
    HAUL(Profession.CARRIER, true),
    BUILD(Profession.BUILDER, true);

    public static final Codec<OrderType> CODEC = Codecs.enumByName(OrderType.class);

    private final Profession profession;
    private final boolean finishes;

    OrderType(Profession profession, boolean finishes) {
        this.profession = profession;
        this.finishes = finishes;
    }

    public Profession profession() {
        return profession;
    }

    /** Farming never finishes: crops grow back. The other orders are done when nothing is left in the area. */
    public boolean finishes() {
        return finishes;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
