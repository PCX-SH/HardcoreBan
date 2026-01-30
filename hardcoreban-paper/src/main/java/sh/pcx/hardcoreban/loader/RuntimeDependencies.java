package sh.pcx.hardcoreban.loader;

/**
 * Runtime dependency definitions for HardcoreBan.
 *
 * These dependencies are downloaded at runtime if not provided by the server.
 * Paper 1.21+ provides HikariCP, but Spigot does not.
 */
public final class RuntimeDependencies {
    private RuntimeDependencies() {}

    /**
     * Runtime dependencies: {groupId, artifactId, version, sha256}
     *
     * SHA-256 checksums are Base64-encoded.
     */
    public static final String[][] DEPENDENCIES = {
        {"com.zaxxer", "HikariCP", "6.2.1", "xlmgg3J86VkCdzV7jD9j+65nIHuwS+q8lkyPTxUwfbw="},
        {"com.mysql", "mysql-connector-j", "9.2.0", "fplBu9zKJE2Hjqlb//eI/Zumplr3V/JL5sYykw1hx+0="},
        {"org.slf4j", "slf4j-api", "2.0.16", "oSV43eG6AL2bgW04iguHmSjQC6s8g8JA9wE79BlsV5o="}
    };
}
