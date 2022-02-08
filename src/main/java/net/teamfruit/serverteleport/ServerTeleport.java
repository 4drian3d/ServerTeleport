package net.teamfruit.serverteleport;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(id = "serverteleport",
        name = "ServerTeleport",
        version = "${project.version}",
        description = "Move players between server",
        authors = {"Kamesuta"}
)
public class ServerTeleport {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folderPath;

    private Toml loadConfig() {
        if (!Files.exists(folderPath)) {
            try{
                Files.createDirectory(folderPath);
            } catch(IOException e){
                e.printStackTrace();
                return null;
            }
        }
        final Path file = folderPath.resolve("config.toml");

        if (!Files.exists(file)) {
            try (InputStream input = this.getClass().getClassLoader().getResourceAsStream("config.toml")) {
                Files.copy(input, file);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return new Toml().read(file.toFile());
    }

    @Inject
    public ServerTeleport(ProxyServer proxy, Logger logger, @DataDirectory Path folder) {
        this.proxy = proxy;
        this.logger = logger;
        this.folderPath = folder;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event){
        Toml toml = this.loadConfig();
        if (toml == null) {
            logger.warn("Failed to load config.toml. Shutting down.");
        } else {
            proxy.getCommandManager().register("servertp", new ServerTeleportCommand(proxy, toml, logger), "stp");
            logger.info("Plugin has enabled!");
        }
    }
}