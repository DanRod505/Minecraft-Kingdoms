package dev.kingdoms.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomsConfigTest {
    @Test
    void emptyConfigIsFilledWithDefaults() {
        var config = CommentedConfig.inMemory();
        assertFalse(KingdomsConfig.COMMON_SPEC.isCorrect(config));
        KingdomsConfig.COMMON_SPEC.correct(config);
        assertTrue(KingdomsConfig.COMMON_SPEC.isCorrect(config));
        assertEquals(Boolean.FALSE, config.get("logging.debug"));
    }

    @Test
    void invalidValueIsReplacedByDefault() {
        var config = CommentedConfig.inMemory();
        KingdomsConfig.COMMON_SPEC.correct(config);
        config.set("logging.debug", "not a boolean");
        assertFalse(KingdomsConfig.COMMON_SPEC.isCorrect(config));
        KingdomsConfig.COMMON_SPEC.correct(config);
        assertEquals(Boolean.FALSE, config.get("logging.debug"));
    }
}
