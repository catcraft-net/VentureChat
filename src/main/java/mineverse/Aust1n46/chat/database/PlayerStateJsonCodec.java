package mineverse.Aust1n46.chat.database;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/** Stable JSON encoding used by SQLite, migration checksums, and recovery. */
public final class PlayerStateJsonCodec {
    private PlayerStateJsonCodec() {}

    public static String canonicalJson(PlayerStateSnapshot state) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("uuid", state.uuid().toString());
        json.put("name", state.name());
        json.put("current", state.currentChannel());
        json.put("ignores", uuidArray(state.ignores()));
        json.put("listening", stringArray(state.listening()));
        json.put("mutes", muteObject(state.mutes()));
        json.put("blockedCommands", stringArray(state.blockedCommands()));
        json.put("host", state.host());
        json.put("party", state.party() == null ? null : state.party().toString());
        json.put("filter", state.filter());
        json.put("notifications", state.notifications());
        json.put("jsonFormat", state.jsonFormat());
        json.put("spy", state.spy());
        json.put("commandSpy", state.commandSpy());
        json.put("rangedSpy", state.rangedSpy());
        json.put("messageToggle", state.messageToggle());
        // Omit the default to preserve checksums of existing migration journals.
        if (!state.personalFilter()) json.put("personalFilter", false);
        json.put("revision", state.revision());
        return JSONObject.toJSONString(json);
    }

    public static PlayerStateSnapshot fromCanonicalJson(String encoded) throws Exception {
        JSONObject json = (JSONObject) new JSONParser().parse(encoded);
        String partyValue = (String) json.get("party");
        return new PlayerStateSnapshot(
                UUID.fromString((String) json.get("uuid")),
                (String) json.get("name"),
                (String) json.get("current"),
                decodeUuids(json.get("ignores")),
                decodeStrings(json.get("listening")),
                decodeMutes(json.get("mutes")),
                decodeStrings(json.get("blockedCommands")),
                bool(json, "host", false),
                partyValue == null || partyValue.isBlank() ? null : UUID.fromString(partyValue),
                bool(json, "filter", true),
                bool(json, "notifications", true),
                (String) json.getOrDefault("jsonFormat", "Default"),
                bool(json, "spy", false),
                bool(json, "commandSpy", false),
                bool(json, "rangedSpy", false),
                bool(json, "messageToggle", true),
                bool(json, "personalFilter", true),
                ((Number) json.getOrDefault("revision", 0L)).longValue());
    }

    static String encodeUuids(Set<UUID> values) {
        return JSONArray.toJSONString(uuidArray(values));
    }

    static String encodeStrings(Set<String> values) {
        return JSONArray.toJSONString(stringArray(values));
    }

    static String encodeMutes(Map<String, PlayerStateSnapshot.MuteState> values) {
        return JSONObject.toJSONString(muteObject(values));
    }

    static Set<UUID> decodeUuids(String encoded) throws Exception {
        return decodeUuids(new JSONParser().parse(encoded));
    }

    static Set<String> decodeStrings(String encoded) throws Exception {
        return decodeStrings(new JSONParser().parse(encoded));
    }

    static Map<String, PlayerStateSnapshot.MuteState> decodeMutes(String encoded) throws Exception {
        return decodeMutes(new JSONParser().parse(encoded));
    }

    private static List<String> uuidArray(Set<UUID> values) {
        return values.stream().map(UUID::toString).sorted().toList();
    }

    private static List<String> stringArray(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static Map<String, Object> muteObject(Map<String, PlayerStateSnapshot.MuteState> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<Object> tuple = new ArrayList<>();
            tuple.add(entry.getValue().expiresAt());
            tuple.add(entry.getValue().reason());
            result.put(entry.getKey(), tuple);
        });
        return result;
    }

    private static Set<UUID> decodeUuids(Object value) {
        Set<UUID> result = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(UUID.fromString(String.valueOf(item)));
            }
        }
        return result;
    }

    private static Set<String> decodeStrings(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static Map<String, PlayerStateSnapshot.MuteState> decodeMutes(Object value) {
        Map<String, PlayerStateSnapshot.MuteState> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> {
                        List<?> tuple = (List<?>) entry.getValue();
                        long expiresAt = ((Number) tuple.get(0)).longValue();
                        String reason = tuple.size() > 1 && tuple.get(1) != null ? String.valueOf(tuple.get(1)) : "";
                        result.put(String.valueOf(entry.getKey()), new PlayerStateSnapshot.MuteState(expiresAt, reason));
                    });
        }
        return result;
    }

    private static boolean bool(JSONObject json, String key, boolean fallback) {
        Object value = json.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }
}
