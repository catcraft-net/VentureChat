package mineverse.Aust1n46.chat.database;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStateRepository extends AutoCloseable {
    void initialize() throws Exception;

    Optional<PlayerStateSnapshot> findByUuid(UUID uuid) throws Exception;

    Optional<PlayerStateSnapshot> findByName(String name) throws Exception;

    List<PlayerStateSnapshot> findByParty(UUID party) throws Exception;

    void save(PlayerStateSnapshot snapshot) throws Exception;

    default void saveAll(List<PlayerStateSnapshot> snapshots) throws Exception {
        for (PlayerStateSnapshot snapshot : snapshots) {
            save(snapshot);
        }
    }

    long count() throws Exception;

    @Override
    void close() throws Exception;
}
