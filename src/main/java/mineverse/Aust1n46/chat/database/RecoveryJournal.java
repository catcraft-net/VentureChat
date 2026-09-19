package mineverse.Aust1n46.chat.database;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class RecoveryJournal {
    private static final String MAGIC = "VENTURECHAT-RECOVERY-1";

    private RecoveryJournal() {}

    public static void write(Path path, List<PlayerStateSnapshot> states) throws Exception {
        if (states.isEmpty()) {
            clear(path);
            return;
        }
        List<PlayerStateSnapshot> sorted = states.stream()
                .sorted(Comparator.comparing(state -> state.uuid().toString()))
                .toList();
        String payload = sorted.stream().map(PlayerStateJsonCodec::canonicalJson)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        String content = MAGIC + "\n" + sha256(payload) + "\n" + payload;
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        moveAtomically(temporary, path);
    }

    public static List<PlayerStateSnapshot> read(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        String[] parts = Files.readString(path, StandardCharsets.UTF_8).split("\n", 3);
        if (parts.length != 3 || !MAGIC.equals(parts[0]) || !sha256(parts[2]).equals(parts[1])) {
            throw new IllegalStateException("Recovery journal checksum is invalid");
        }
        List<PlayerStateSnapshot> states = new ArrayList<>();
        if (!parts[2].isBlank()) {
            for (String line : parts[2].split("\n")) {
                states.add(PlayerStateJsonCodec.fromCanonicalJson(line));
            }
        }
        return states;
    }

    public static void clear(Path path) throws Exception {
        Files.deleteIfExists(path);
        Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".tmp"));
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void moveAtomically(Path from, Path to) throws Exception {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
