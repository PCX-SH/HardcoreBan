package sh.pcx.hardcoreban.loader;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Interface that the actual plugin implementation must implement.
 *
 * This allows the loader to instantiate and delegate lifecycle methods
 * to the real plugin without having a compile-time dependency on it.
 */
public interface LoaderBootstrap {

    /**
     * Called when the plugin is loaded (before enable).
     * Corresponds to {@link JavaPlugin#onLoad()}.
     *
     * @param loader The loader plugin instance
     */
    void onLoad(JavaPlugin loader);

    /**
     * Called when the plugin is enabled.
     * Corresponds to {@link JavaPlugin#onEnable()}.
     *
     * @param loader The loader plugin instance
     */
    void onEnable(JavaPlugin loader);

    /**
     * Called when the plugin is disabled.
     * Corresponds to {@link JavaPlugin#onDisable()}.
     *
     * @param loader The loader plugin instance
     */
    void onDisable(JavaPlugin loader);
}
