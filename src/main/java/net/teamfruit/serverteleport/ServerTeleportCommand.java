package net.teamfruit.serverteleport;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder.Result;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerTeleportCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final Logger logger;

    private final String langPrefix;
    private final String langUsage;
    private final String langNoServer;
    private final String langPlayerNum;
    private final String langPlayerName;
    private final String langSuccess;

    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.builder().character('&').hexColors().build();

    public ServerTeleportCommand(ProxyServer proxy, Toml toml, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;

        // Localization
        Toml lang = toml.getTable("lang");
        this.langPrefix = lang.getString("prefix");
        this.langUsage = lang.getString("usage");
        this.langNoServer = lang.getString("noserver");
        this.langPlayerNum = lang.getString("player-num");
        this.langPlayerName = lang.getString("player-name");
        this.langSuccess = lang.getString("success");
    }

    private void warnResult(String player, String server, boolean result){
        if(!result) logger.warn("Cannot teleport player {} to server {}", player, server);
    }

    private List<String> candidate(String arg, List<String> candidates) {
        if (candidates.contains(arg)){
            candidates.remove(arg);
            return candidates;
        }
        return candidates;
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        final String[] args = invocation.arguments();
        // Source Suggestion
        if (args.length <= 1){
            List<String> list = new ArrayList<>();
            list.add("@a");
            list.addAll(this.getRegularCompletions());
            return CompletableFuture.completedFuture(list);
        }
        // Destination Suggestion
        if (args.length == 2){
            return CompletableFuture.supplyAsync(() -> candidate(args[0], this.getRegularCompletions()));
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private List<String> getRegularCompletions(){
        return Stream.concat(
            this.proxy.getAllServers().stream().map(sv -> "#"+sv.getServerInfo().getName()),
            this.proxy.getAllPlayers().stream().map(Player::getUsername)
        ).collect(Collectors.toList());
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // Argument Validation
        if (args.length < 2) {
            source.sendMessage(serializer.deserialize(langPrefix).append(serializer.deserialize(langUsage)));
            return;
        }
        String srcArg = args[0];
        String dstArg = args[1];

        // Destination Validation
        Optional<RegisteredServer> dstOptional = dstArg.startsWith("#")
            ? this.proxy.getServer(dstArg.substring(1))
            : this.proxy.getPlayer(dstArg).flatMap(Player::getCurrentServer).map(ServerConnection::getServer);
        if (dstOptional.isEmpty()) {
            source.sendMessage(serializer.deserialize(langPrefix).append(serializer.deserialize(langNoServer)));
            return;
        }
        RegisteredServer dst = dstOptional.get();

        if(srcArg.charAt(0) == '#'){
            Collection<Player> servers = proxy.getServer(srcArg.substring(1))
                .map(RegisteredServer::getPlayersConnected)
                .orElseGet(Collections::emptyList);
            sendSuccessMessage(source, dstArg, null, servers.size());
            servers.forEach(p ->
                p.createConnectionRequest(dst)
                    .connect()
                    .thenApply(Result::isSuccessful)
                    .thenAccept(result -> warnResult(p.getUsername(), dst.getServerInfo().getName(), result))
            );
        } else if("@a".equals(srcArg)){
            Collection<Player> players = proxy.getAllPlayers();
            sendSuccessMessage(source, dstArg, null, players.size());
            players.stream().filter(p -> !dstOptional.equals(p.getCurrentServer().map(ServerConnection::getServer))).collect(Collectors.toSet()).forEach(p ->
                p.createConnectionRequest(dst)
                    .connect()
                    .thenApply(Result::isSuccessful)
                    .thenAccept(result -> warnResult(p.getUsername(), dst.getServerInfo().getName(), result))
            );
        } else {
            Optional<Player> p = this.proxy.getPlayer(srcArg);
            if(p.isPresent()){
                Player player = p.get();
                sendSuccessMessage(source, dstArg, player.getUsername(), 1);
                player.createConnectionRequest(dst)
                    .connect()
                    .thenApply(Result::isSuccessful)
                    .thenAccept(result -> warnResult(player.getUsername(), dst.getServerInfo().getName(), result));
            } else {
                source.sendMessage(Component.text(String.format("Player %s has not been found", srcArg)));
            }
        }
    }

    private void sendSuccessMessage(Audience audience, String dstArg, String playerName, int size){
        audience.sendMessage(serializer.deserialize(langPrefix)
            .append(serializer.deserialize(String.format(langSuccess,
                dstArg,
                size == 1
                    ? String.format(langPlayerName, playerName)
                    : String.format(langPlayerNum, size)
            )))
        );
    }

    @Override
    public boolean hasPermission(Invocation invocation){
        return invocation.source().hasPermission("servertp");
    }
}
