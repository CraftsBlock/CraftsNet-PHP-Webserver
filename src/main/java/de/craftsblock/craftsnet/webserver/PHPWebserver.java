package de.craftsblock.craftsnet.webserver;

import de.craftsblock.craftscore.json.Json;
import de.craftsblock.craftscore.json.JsonParser;
import de.craftsblock.craftsnet.addon.Addon;

import javax.swing.*;
import java.io.File;

public class PHPWebserver extends Addon {

    private static PHPWebserver instance;

    private static File configFile;
    private static Json config;

    @Override
    public void onEnable() {
        instance = this;

        configFile = new File(getDataFolder(), "config.json");
        config = JsonParser.parse(configFile);

        routeRegistry().share("/", getDataFolder(), false);
        listenerRegistry().register(new ShareRequestListener());

        PHPBridge.startServer(getDataFolder(), "https://www.php.net/distributions/php-8.3.4.tar.gz");
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public static PHPWebserver getInstance() {
        return instance;
    }

    public static Json getConfig() {
        return config;
    }

    public static void saveConfig() {
        config.save(configFile);
    }

}
