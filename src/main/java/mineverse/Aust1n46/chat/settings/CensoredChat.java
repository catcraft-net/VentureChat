package mineverse.Aust1n46.chat.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.ChatColor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import mineverse.Aust1n46.chat.filter.CensorResult;

/** Projects a single recipient-local mask onto text without rebuilding chat metadata. */
public final class CensoredChat {
    private CensoredChat() { }

    public static String legacy(String text, CensorResult result) {
        if (!result.changed() || !result.original().equals(ChatColor.stripColor(text))) return text;
        StringBuilder out = new StringBuilder(text);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ChatColor.COLOR_CHAR && i + 1 < text.length()
                    && "0123456789abcdefklmnorx".indexOf(Character.toLowerCase(text.charAt(i + 1))) >= 0) {
                i++;
            } else {
                if (result.original().charAt(visible) != result.censored().charAt(visible)) out.setCharAt(i, '*');
                visible++;
            }
        }
        return out.toString();
    }

    public static String suffix(String formatted, String body, CensorResult result) {
        return formatted.endsWith(body)
                ? formatted.substring(0, formatted.length() - body.length()) + legacy(body, result) : formatted;
    }

    private record Text(String value, Consumer<String> replace) { }

    /** Native VentureChat preserves the prefix in element 1; other serializers need a known prefix/body boundary. */
    public static String json(String original, String prefix, String body, Function<String, CensorResult> censor) {
        if (original == null || original.length() > 65536) return original;
        try {
            Object root = new JSONParser().parse(original);
            List<Text> slots = new ArrayList<>();
            boolean nativeLayout = root instanceof JSONArray array && array.size() >= 3 && "".equals(array.get(0))
                    && array.get(1) instanceof JSONObject head && "".equals(head.get("text")) && head.get("extra") instanceof JSONArray;
            if (nativeLayout) {
                JSONArray array = (JSONArray) root;
                for (int i = 2; i < array.size(); i++) collectArrayElement(array, i, slots, 0);
            } else collect(root, slots, 0);
            String visible = slots.stream().map(Text::value).collect(java.util.stream.Collectors.joining());
            int offset = 0;
            if (!nativeLayout) {
                if (prefix != null && !prefix.isEmpty() && visible.startsWith(prefix)) offset = prefix.length();
                else if (body != null && !body.isEmpty() && visible.endsWith(body)) offset = visible.length() - body.length();
                else return original;
            }
            CensorResult result = censor.apply(visible.substring(offset));
            if (!result.changed()) return original;
            String masked = visible.substring(0, offset) + result.censored();
            int position = 0;
            for (Text slot : slots) {
                slot.replace().accept(masked.substring(position, position + slot.value().length()));
                position += slot.value().length();
            }
            return root.toString();
        } catch (org.json.simple.parser.ParseException | IllegalArgumentException ex) {
            // Foreign or unsupported component layouts keep their original delivery intact.
            return original;
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectArrayElement(JSONArray array, int i, List<Text> slots, int depth) {
        Object value = array.get(i);
        if (value instanceof String text) slots.add(new Text(text, replacement -> array.set(i, replacement)));
        else collect(value, slots, depth + 1);
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object node, List<Text> slots, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Component nesting limit");
        if (node instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) collectArrayElement(array, i, slots, depth);
        } else if (node instanceof JSONObject object) {
            if (object.get("text") instanceof String text)
                slots.add(new Text(text, replacement -> object.put("text", replacement)));
            // Never walk hover/click payloads, identifiers or item data.
            if (object.get("extra") instanceof JSONArray extra) collect(extra, slots, depth + 1);
        }
    }
}
