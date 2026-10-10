package dev.kingdoms.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Single logger for the mod. All messages are tagged with the "kingdoms" logger name. */
public final class KingdomsLog {
    public static final String NAME = "kingdoms";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private KingdomsLog() {}

    /** Applies the configured verbosity. Debug output is off by default to keep server logs clean. */
    public static void setDebug(boolean debug) {
        Configurator.setLevel(NAME, debug ? Level.DEBUG : Level.INFO);
        LOGGER.info("Log level set to {}", debug ? "DEBUG" : "INFO");
    }
}
