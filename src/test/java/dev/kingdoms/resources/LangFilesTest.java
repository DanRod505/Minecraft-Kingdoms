package dev.kingdoms.resources;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every player-facing key exists in both languages (GDD 18.6: complete localization). */
class LangFilesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/kingdoms/lang");

    private static Set<String> keys(String lang) throws IOException {
        var json = JsonParser.parseString(Files.readString(LANG.resolve(lang + ".json"))).getAsJsonObject();
        return new TreeSet<>(json.keySet());
    }

    @Test
    void englishAndPortugueseHaveSameKeys() throws IOException {
        assertEquals(keys("en_us"), keys("pt_br"));
    }

    @Test
    void everyFullKeyUsedInCodeIsTranslated() throws IOException {
        var english = keys("en_us");
        var pattern = Pattern.compile("\"(kingdoms\\.[a-z_]+\\.[a-z_.]*[a-z_])\"");
        var missing = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(Files.readString(file));
                while (matcher.find())
                    if (!english.contains(matcher.group(1)))
                        missing.add(matcher.group(1));
            }
        }
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }
}
