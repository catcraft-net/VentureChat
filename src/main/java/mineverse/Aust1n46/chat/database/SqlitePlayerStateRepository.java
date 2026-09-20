package mineverse.Aust1n46.chat.database;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqlitePlayerStateRepository implements PlayerStateRepository {
    public static final int SCHEMA_VERSION = 1;

    private static final String COLUMNS = "uuid,name,current_channel,ignores_json,listening_json,mutes_json,"
            + "blocked_commands_json,host,party_uuid,filter_enabled,notifications,json_format,spy,command_spy,"
            + "ranged_spy,message_toggle,revision";
    private static final String DELETE_SQL = "DELETE FROM players WHERE uuid=? AND revision<=?";
    private static final String UPSERT_SQL = "INSERT INTO players(" + COLUMNS + ",updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,current_channel=excluded.current_channel,"
            + "ignores_json=excluded.ignores_json,listening_json=excluded.listening_json,mutes_json=excluded.mutes_json,"
            + "blocked_commands_json=excluded.blocked_commands_json,host=excluded.host,party_uuid=excluded.party_uuid,"
            + "filter_enabled=excluded.filter_enabled,notifications=excluded.notifications,json_format=excluded.json_format,"
            + "spy=excluded.spy,command_spy=excluded.command_spy,ranged_spy=excluded.ranged_spy,"
            + "message_toggle=excluded.message_toggle,revision=excluded.revision,updated_at=excluded.updated_at "
            + "WHERE excluded.revision>=players.revision";

    private final Path databasePath;
    private final String defaultChannel;
    private final Set<String> autojoinChannels;

    public SqlitePlayerStateRepository(Path databasePath, String defaultChannel, Set<String> autojoinChannels) {
        this.databasePath = databasePath.toAbsolutePath();
        this.defaultChannel = defaultChannel;
        this.autojoinChannels = Set.copyOf(autojoinChannels);
    }

    @Override
    public void initialize() throws Exception {
        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS storage_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                    + "uuid TEXT PRIMARY KEY,"
                    + "name TEXT NOT NULL COLLATE NOCASE,"
                    + "current_channel TEXT NOT NULL,"
                    + "ignores_json TEXT NOT NULL,"
                    + "listening_json TEXT NOT NULL,"
                    + "mutes_json TEXT NOT NULL,"
                    + "blocked_commands_json TEXT NOT NULL,"
                    + "host INTEGER NOT NULL,"
                    + "party_uuid TEXT,"
                    + "filter_enabled INTEGER NOT NULL,"
                    + "notifications INTEGER NOT NULL,"
                    + "json_format TEXT NOT NULL,"
                    + "spy INTEGER NOT NULL,"
                    + "command_spy INTEGER NOT NULL,"
                    + "ranged_spy INTEGER NOT NULL,"
                    + "message_toggle INTEGER NOT NULL,"
                    + "revision INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS players_name_idx ON players(name COLLATE NOCASE)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS players_party_idx ON players(party_uuid)");
            try (PreparedStatement metadata = connection.prepareStatement(
                    "INSERT INTO storage_metadata(key,value) VALUES('schema_version',?) "
                            + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
                metadata.setString(1, Integer.toString(SCHEMA_VERSION));
                metadata.executeUpdate();
            }
        }
    }

    @Override
    public Optional<PlayerStateSnapshot> findByUuid(UUID uuid) throws Exception {
        return findOne("SELECT " + COLUMNS + " FROM players WHERE uuid=?", uuid.toString());
    }

    @Override
    public Optional<PlayerStateSnapshot> findByName(String name) throws Exception {
        return findOne("SELECT " + COLUMNS + " FROM players WHERE name=? COLLATE NOCASE LIMIT 1", name);
    }

    @Override
    public List<PlayerStateSnapshot> findByParty(UUID party) throws Exception {
        List<PlayerStateSnapshot> result = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM players WHERE party_uuid=? ORDER BY uuid")) {
            statement.setString(1, party.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(read(rows));
                }
            }
        }
        return result;
    }

    @Override
    public void save(PlayerStateSnapshot state) throws Exception {
        try (Connection connection = connection();
                PreparedStatement delete = connection.prepareStatement(DELETE_SQL);
                PreparedStatement upsert = connection.prepareStatement(UPSERT_SQL)) {
            write(state, delete, upsert);
        }
    }

    @Override
    public void saveAll(List<PlayerStateSnapshot> states) throws Exception {
        if (states.isEmpty()) return;
        try (Connection connection = connection();
                PreparedStatement delete = connection.prepareStatement(DELETE_SQL);
                PreparedStatement upsert = connection.prepareStatement(UPSERT_SQL)) {
            connection.setAutoCommit(false);
            try {
                for (PlayerStateSnapshot state : states) {
                    write(state, delete, upsert);
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void write(PlayerStateSnapshot state, PreparedStatement delete, PreparedStatement upsert) throws Exception {
        if (state.isDefault(defaultChannel, autojoinChannels)) {
            delete.setString(1, state.uuid().toString());
            delete.setLong(2, state.revision());
            delete.executeUpdate();
            return;
        }

        int index = 1;
        upsert.setString(index++, state.uuid().toString());
        upsert.setString(index++, state.name());
        upsert.setString(index++, state.currentChannel());
        upsert.setString(index++, PlayerStateJsonCodec.encodeUuids(state.ignores()));
        upsert.setString(index++, PlayerStateJsonCodec.encodeStrings(state.listening()));
        upsert.setString(index++, PlayerStateJsonCodec.encodeMutes(state.mutes()));
        upsert.setString(index++, PlayerStateJsonCodec.encodeStrings(state.blockedCommands()));
        upsert.setInt(index++, state.host() ? 1 : 0);
        upsert.setString(index++, state.party() == null ? null : state.party().toString());
        upsert.setInt(index++, state.filter() ? 1 : 0);
        upsert.setInt(index++, state.notifications() ? 1 : 0);
        upsert.setString(index++, state.jsonFormat());
        upsert.setInt(index++, state.spy() ? 1 : 0);
        upsert.setInt(index++, state.commandSpy() ? 1 : 0);
        upsert.setInt(index++, state.rangedSpy() ? 1 : 0);
        upsert.setInt(index++, state.messageToggle() ? 1 : 0);
        upsert.setLong(index++, state.revision());
        upsert.setLong(index, System.currentTimeMillis());
        upsert.executeUpdate();
    }

    @Override
    public long count() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM players")) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    public Set<String> indexNames() throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='index'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    public String canonicalChecksum() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT " + COLUMNS + " FROM players ORDER BY uuid")) {
            while (rows.next()) {
                digest.update(PlayerStateJsonCodec.canonicalJson(read(rows)).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public void checkpoint() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    private Optional<PlayerStateSnapshot> findOne(String sql, String value) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private PlayerStateSnapshot read(ResultSet row) throws Exception {
        String partyValue = row.getString("party_uuid");
        return new PlayerStateSnapshot(
                UUID.fromString(row.getString("uuid")),
                row.getString("name"),
                row.getString("current_channel"),
                PlayerStateJsonCodec.decodeUuids(row.getString("ignores_json")),
                PlayerStateJsonCodec.decodeStrings(row.getString("listening_json")),
                PlayerStateJsonCodec.decodeMutes(row.getString("mutes_json")),
                PlayerStateJsonCodec.decodeStrings(row.getString("blocked_commands_json")),
                row.getInt("host") != 0,
                partyValue == null ? null : UUID.fromString(partyValue),
                row.getInt("filter_enabled") != 0,
                row.getInt("notifications") != 0,
                row.getString("json_format"),
                row.getInt("spy") != 0,
                row.getInt("command_spy") != 0,
                row.getInt("ranged_spy") != 0,
                row.getInt("message_toggle") != 0,
                row.getLong("revision"));
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    @Override
    public void close() {
        // Connections are deliberately short-lived so shutdown never waits on a pool.
    }
}
