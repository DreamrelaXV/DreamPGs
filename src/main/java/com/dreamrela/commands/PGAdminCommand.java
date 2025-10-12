package com.dreamrela.commands;

import com.dreamrela.DreamPGs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PGAdminCommand implements CommandExecutor, TabCompleter {

    private final DreamPGs plugin;

    public PGAdminCommand(DreamPGs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("PG.*")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§6PG Admin Commands:");
            player.sendMessage("§e/pg reload§7 - reload config");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig();
            player.sendMessage("§aDreamPGs config reloaded.");
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("reload");
        return Collections.emptyList();
    }
}
