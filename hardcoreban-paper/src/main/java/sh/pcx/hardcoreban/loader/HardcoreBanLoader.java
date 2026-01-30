package sh.pcx.hardcoreban.loader;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

/**
 * Bootstrap loader for HardcoreBan.
 *
 * This class handles downloading and loading runtime dependencies before
 * the main plugin code is executed. It works on any server (Spigot, Paper,
 * Folia) without relying on the server's library loader implementation.
 *
 * The loader:
 * 1. Downloads required dependencies from Maven Central
 * 2. Verifies SHA-256 checksums
 * 3. Creates a custom classloader with the dependencies
 * 4. Loads and delegates to the actual plugin implementation
 */
public class HardcoreBanLoader extends JavaPlugin {

    private static final String BOOTSTRAP_CLASS = "sh.pcx.hardcoreban.HardcoreBanBootstrap";

    private static final String[] MAVEN_REPOS = {
        "https://repo1.maven.org/maven2",
        "https://repo.maven.apache.org/maven2"
    };

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;

    private Object bootstrap;
    private URLClassLoader isolatedClassLoader;

    @Override
    public void onLoad() {
        try {
            loadBootstrap();

            if (bootstrap != null) {
                bootstrap.getClass()
                    .getMethod("onLoad", JavaPlugin.class)
                    .invoke(bootstrap, this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load HardcoreBan", e);
            throw new RuntimeException("Failed to load HardcoreBan", e);
        }
    }

    @Override
    public void onEnable() {
        if (bootstrap == null) {
            getLogger().severe("Bootstrap not initialized! Plugin cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            bootstrap.getClass()
                .getMethod("onEnable", JavaPlugin.class)
                .invoke(bootstrap, this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable HardcoreBan", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            try {
                bootstrap.getClass()
                    .getMethod("onDisable", JavaPlugin.class)
                    .invoke(bootstrap, this);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error during HardcoreBan shutdown", e);
            }
        }

        if (isolatedClassLoader != null) {
            try {
                isolatedClassLoader.close();
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Failed to close classloader", e);
            }
        }
    }

    /**
     * Loads dependencies and initializes the bootstrap.
     */
    private void loadBootstrap() throws Exception {
        Path librariesDir = getDataFolder().toPath().resolve("libraries");
        Files.createDirectories(librariesDir);

        // Check if dependencies are provided by the server (Paper 1.21+)
        if (areDependenciesAvailable()) {
            getLogger().info("Using server-provided dependencies");
            Class<?> bootstrapClass = Class.forName(BOOTSTRAP_CLASS);
            Constructor<?> constructor = bootstrapClass.getConstructor();
            bootstrap = constructor.newInstance();
            return;
        }

        getLogger().info("Downloading runtime dependencies...");

        // Download dependencies
        List<URL> jarUrls = new ArrayList<>();
        for (String[] dep : RuntimeDependencies.DEPENDENCIES) {
            Path jarPath = downloadDependency(librariesDir, dep[0], dep[1], dep[2], dep[3]);
            jarUrls.add(jarPath.toUri().toURL());
        }

        // Add the plugin JAR itself so plugin classes are loaded with dependencies in the same classloader
        URL pluginJar = getClass().getProtectionDomain().getCodeSource().getLocation();
        jarUrls.add(pluginJar);

        getLogger().info("Creating isolated classloader with " + jarUrls.size() + " JARs (including plugin)");

        // Create isolated classloader with the plugin's parent classloader (to access Bukkit API)
        isolatedClassLoader = new IsolatedURLClassLoader(
            jarUrls.toArray(new URL[0]),
            getClass().getClassLoader()
        );

        // Load bootstrap class from the isolated classloader
        Class<?> bootstrapClass = isolatedClassLoader.loadClass(BOOTSTRAP_CLASS);
        Constructor<?> constructor = bootstrapClass.getConstructor();
        bootstrap = constructor.newInstance();
    }

    /**
     * Checks if core dependencies are already available (e.g., Paper 1.21+ provides HikariCP).
     */
    private boolean areDependenciesAvailable() {
        try {
            // Check for HikariCP
            Class.forName("com.zaxxer.hikari.HikariConfig");
            // Check for MySQL Connector
            Class.forName("com.mysql.cj.jdbc.Driver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Downloads a dependency from Maven repositories.
     */
    private Path downloadDependency(Path librariesDir, String groupId, String artifactId,
                                     String version, String expectedSha256) throws Exception {
        String fileName = artifactId + "-" + version + ".jar";
        Path targetPath = librariesDir.resolve(fileName);

        // Check if already downloaded and valid
        if (Files.exists(targetPath)) {
            if (verifyChecksum(targetPath, expectedSha256)) {
                getLogger().fine("Using cached: " + fileName);
                return targetPath;
            }
            getLogger().warning("Checksum mismatch for " + fileName + ", re-downloading...");
            Files.delete(targetPath);
        }

        // Build Maven path
        String mavenPath = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + fileName;

        // Try each repository
        Exception lastException = null;
        for (String repo : MAVEN_REPOS) {
            String url = repo + "/" + mavenPath;
            try {
                getLogger().info("Downloading " + fileName + "...");
                downloadFile(url, targetPath);

                if (verifyChecksum(targetPath, expectedSha256)) {
                    return targetPath;
                }

                getLogger().warning("Checksum verification failed for " + fileName);
                Files.deleteIfExists(targetPath);
            } catch (Exception e) {
                lastException = e;
                getLogger().fine("Failed to download from " + repo + ": " + e.getMessage());
            }
        }

        throw new RuntimeException("Failed to download " + fileName, lastException);
    }

    /**
     * Downloads a file from a URL.
     */
    private void downloadFile(String urlString, Path targetPath) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "HardcoreBan-Loader/1.0");

        try {
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode() + ": " + connection.getResponseMessage());
            }

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Verifies a file's SHA-256 checksum.
     */
    private boolean verifyChecksum(Path path, String expectedChecksum) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(path);
            byte[] hashBytes = digest.digest(fileBytes);
            String actualChecksum = Base64.getEncoder().encodeToString(hashBytes);
            return actualChecksum.equals(expectedChecksum);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to verify checksum", e);
            return false;
        }
    }

    /**
     * Custom URLClassLoader that loads dependency classes before delegating to parent.
     */
    private static class IsolatedURLClassLoader extends URLClassLoader {

        private static final String[] SELF_LOAD_PREFIXES = {
            "com.zaxxer.hikari.",
            "com.mysql.",
            "org.slf4j.",
            "sh.pcx.hardcoreban."  // Plugin classes (except loader)
        };

        IsolatedURLClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                // Check if already loaded
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    if (resolve) resolveClass(c);
                    return c;
                }

                // Load dependency classes from our JARs first
                if (shouldLoadFromSelf(name)) {
                    try {
                        c = findClass(name);
                        if (resolve) resolveClass(c);
                        return c;
                    } catch (ClassNotFoundException ignored) {
                        // Fall through to parent
                    }
                }

                // Delegate to parent
                return super.loadClass(name, resolve);
            }
        }

        private boolean shouldLoadFromSelf(String name) {
            // Don't load loader classes from self - they're the entry point
            if (name.startsWith("sh.pcx.hardcoreban.loader.")) {
                return false;
            }
            for (String prefix : SELF_LOAD_PREFIXES) {
                if (name.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
