package dev.kingdoms.network;

import dev.kingdoms.KingdomsMod;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

/**
 * Main network channel. The protocol version is checked at login on both sides (GDD 14.7, rule 20.7):
 * bump {@link #PROTOCOL_VERSION} whenever a message is added, removed or changes format.
 */
public final class KingdomsNetwork {
    public static final int PROTOCOL_VERSION = 1;

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "main"))
        .networkProtocolVersion(PROTOCOL_VERSION)
        .acceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
        .simpleChannel();

    private KingdomsNetwork() {}

    /** Forces class loading so the channel is registered during setup. Messages are added in later phases. */
    public static void init() {}
}
