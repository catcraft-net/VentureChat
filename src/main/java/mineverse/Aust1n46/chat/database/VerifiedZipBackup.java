package mineverse.Aust1n46.chat.database;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class VerifiedZipBackup {
    private static final String MANIFEST = "manifest.sha256";

    private VerifiedZipBackup() {}

    public static Path create(Path dataFolder, List<Path> sources) throws Exception {
        Path backupDirectory = Files.createDirectories(dataFolder.resolve("backups"));
        Path destination = backupDirectory.resolve("player-data-migration-" + Instant.now().toEpochMilli() + ".zip");
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Map<String, String> manifest = new LinkedHashMap<>();

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            for (Path source : sources) {
                String name = dataFolder.relativize(source).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(source);
                manifest.put(name, sha256(bytes));
                zip.putNextEntry(new ZipEntry(name));
                zip.write(bytes);
                zip.closeEntry();
            }
            StringBuilder contents = new StringBuilder();
            manifest.forEach((name, hash) -> contents.append(hash).append("  ").append(name).append('\n'));
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(contents.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        moveAtomically(temporary, destination);
        if (!verify(destination)) {
            throw new IllegalStateException("Player data backup verification failed");
        }
        return destination;
    }

    public static boolean verify(Path backup) throws Exception {
        if (backup == null || !Files.isRegularFile(backup)) {
            return false;
        }
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(backup)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        byte[] manifestBytes = entries.remove(MANIFEST);
        if (manifestBytes == null) {
            return false;
        }
        String manifest = new String(manifestBytes, StandardCharsets.UTF_8);
        int listed = 0;
        for (String line : manifest.split("\n")) {
            if (line.isBlank()) continue;
            int separator = line.indexOf("  ");
            if (separator < 0) return false;
            String expected = line.substring(0, separator);
            byte[] value = entries.get(line.substring(separator + 2));
            if (value == null || !expected.equals(sha256(value))) return false;
            listed++;
        }
        return listed == entries.size();
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static void moveAtomically(Path from, Path to) throws Exception {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(from, to);
        }
    }
}
