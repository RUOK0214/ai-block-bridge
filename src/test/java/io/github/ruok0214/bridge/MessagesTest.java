package io.github.ruok0214.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    private JsonObject language(String code) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/ai_block_bridge/lang/" + code + ".json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void languagesHaveMatchingKeysAndArguments() throws Exception {
        var en = language("en_us");
        var ko = language("ko_kr");
        assertEquals(en.keySet(), ko.keySet());
        for (String key : en.keySet()) {
            assertEquals(en.get(key).getAsString().split("%s", -1).length,
                ko.get(key).getAsString().split("%s", -1).length, key);
            assertFalse(en.get(key).getAsString().isBlank(), key);
            assertFalse(ko.get(key).getAsString().isBlank(), key);
        }
    }

    @Test void errorDetectionDoesNotDependOnRenderedLanguage() {
        String nested = Messages.text("ai_block_bridge.error.line", 5,
            Messages.text("ai_block_bridge.error.duplicate"));
        assertTrue(Messages.isEncoded(nested));
        assertTrue(Messages.isError(Messages.text("ai_block_bridge.error", nested)));
        assertFalse(Messages.isError(Messages.text("ai_block_bridge.timeline.started")));
        assertTrue(Messages.isError("오류: legacy server"));
        assertFalse(Messages.isError(null));
        assertFalse(Messages.isError("AI_BLOCK_BRIDGE_MESSAGE:broken JSON"));
    }

    @Test void quotedUserTextRoundTripsWithoutBecomingMessageStructure() {
        String name = "a\"b\\c\n한글 %s.txt";
        String encoded = Messages.text("ai_block_bridge.loaded", name);
        var value = JsonParser.parseString(encoded.substring(encoded.indexOf(':') + 1)).getAsJsonObject();
        assertEquals(name, value.getAsJsonArray("args").get(0).getAsString());
        assertFalse(Messages.isEncoded("AI_BLOCK_BRIDGE_MESSAGE:{\"key\":\"other.key\",\"args\":[]}"));
    }
}
