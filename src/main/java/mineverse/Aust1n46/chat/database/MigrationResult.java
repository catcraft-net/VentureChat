package mineverse.Aust1n46.chat.database;

import java.nio.file.Path;

public record MigrationResult(Status status, Path backup, int migratedPlayers, int skippedFiles, String detail) {
    public enum Status {
        ALREADY_PRESENT,
        CREATED_EMPTY,
        MIGRATED,
        FAILED_USING_YAML
    }
}
