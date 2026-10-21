package mineverse.Aust1n46.chat.settings;

import mineverse.Aust1n46.chat.filter.CensorResult;
import mineverse.Aust1n46.chat.filter.FilterDecision;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class CensoredChatTest {
    private CensorResult mask(String text) {
        return new CensorResult(text, text.replace("bad", "***"), FilterDecision.MATCH);
    }
    @Test public void legacyMaskPreservesColoursAndPrefix() {
        String body = " hello §ab§lad§r world";
        assertEquals("badSender: hello §a*§l**§r world",
                CensoredChat.suffix("badSender:" + body, body, mask(" hello bad world")));
        assertEquals(body, CensoredChat.legacy(body, mask("different bad")));
    }
    @Test public void jsonMaskSpansComponentsAndPreservesMetadata() throws Exception {
        String json = "[\"\",{\"text\":\"\",\"extra\":[{\"text\":\"badSender: \"}]},"
                + "{\"text\":\"hello b\",\"color\":\"red\"},{\"text\":\"ad world\",\"bold\":true,"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"bad item\"},"
                + "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/msg bad\"}}]";
        JSONArray output = (JSONArray) new JSONParser().parse(CensoredChat.json(json, "badSender: ", "ignored", this::mask));
        JSONArray original = (JSONArray) new JSONParser().parse(json);
        assertEquals(original.get(1), output.get(1));
        assertEquals("hello *", ((JSONObject)output.get(2)).get("text"));
        assertEquals("** world", ((JSONObject)output.get(3)).get("text"));
        JSONObject expected = (JSONObject)original.get(3);
        JSONObject actual = (JSONObject)output.get(3);
        assertEquals(expected.get("hoverEvent"), actual.get("hoverEvent"));
        assertEquals(expected.get("clickEvent"), actual.get("clickEvent"));
        assertEquals(true, actual.get("bold"));
        assertEquals("red", ((JSONObject)output.get(2)).get("color"));
    }
    @Test public void customLayoutUsesKnownBoundaryAndCleanJsonIsUnchanged() {
        String custom = "{\"text\":\"badSender: hello bad\"}";
        assertEquals("{\"text\":\"badSender: hello ***\"}", CensoredChat.json(custom, "badSender: ", "hello bad", this::mask));
        String clean = "[\"\", {\"text\":\"\",\"extra\":[{\"text\":\"badSender\"}]}, {\"text\":\"hello\"}]";
        assertEquals(clean, CensoredChat.json(clean, "badSender", "hello", this::mask));
        assertEquals(custom, CensoredChat.json(custom, "unknown", "unknown", this::mask));
        assertEquals("invalid", CensoredChat.json("invalid", "", "", this::mask));
    }
}
