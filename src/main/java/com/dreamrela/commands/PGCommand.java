package com.dreamrela.commands;

import com.dreamrela.DreamPGs;
import com.dreamrela.privategame.PrivateGame;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class PGCommand implements CommandExecutor, TabCompleter {

    private final DreamPGs plugin;

    public PGCommand(DreamPGs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use PG commands.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                if (!player.hasPermission("PG.pg.base")) { player.sendMessage("§cNo permission."); return true; }
                sendHelp(player);
                return true;
            case "create":
                if (!player.hasPermission("PG.pg.create")) { player.sendMessage("§cNo permission."); return true; }
                // Require party with at least 2 members
                java.util.Optional<com.dreamrela.party.Party> optParty = plugin.getPartyManager().getParty(player.getUniqueId());
                if (!optParty.isPresent() || optParty.get().size() < 2) {
                    player.sendMessage("§cYou need a party to create a Private Game. Invite someone with §e/party invite <player>§c.");
                    return true;
                }
                com.dreamrela.privategame.PrivateGame created = plugin.getPrivateGameManager().create(player.getUniqueId());
                if (created == null) {
                    player.sendMessage("§cCould not create a Private Game. Make sure you're in a party.");
                    return true;
                }
                player.sendMessage("§aCreated Private Game in category §ePG§a with teams §cRED §aand §aGREEN§a.");
                // open team assignment GUI for the host (lists only party members)
                player.openInventory(com.dreamrela.gui.TeamAssignGUI.build(plugin, created));
                return true;
            case "start":
                if (!player.hasPermission("PG.pg.start")) { player.sendMessage("§cNo permission."); return true; }
                plugin.getPrivateGameManager().updateMembersFromParty(player.getUniqueId());
                plugin.getPrivateGameManager().start(player.getUniqueId());
                return true;
            case "disband":
                if (!player.hasPermission("PG.pg.disband")) { player.sendMessage("§cNo permission."); return true; }
                if (plugin.getPrivateGameManager().disband(player.getUniqueId())) {
                    player.sendMessage("§eDisbanded your Private Game.");
                } else {
                    player.sendMessage("§cYou don't have a Private Game.");
                }
                return true;
            case "info":
                if (!player.hasPermission("PG.pg.info")) { player.sendMessage("§cNo permission."); return true; }
                Optional<PrivateGame> opt = plugin.getPrivateGameManager().getByHost(player.getUniqueId());
                if (!opt.isPresent()) {
                    player.sendMessage("§cYou don't have a Private Game. Use §e/pg create§c.");
                    return true;
                }
                PrivateGame pg = opt.get();
                player.sendMessage("§6Private Game Info:");
                player.sendMessage("§7Category: §e" + pg.getCategory());
                player.sendMessage("§7Allowed Teams: §c" + String.join("§7, §a", pg.getAllowedTeams()));
                player.sendMessage("§7Members: §e" + pg.getMembers().size());
                return true;
            case "reload":
                if (!player.hasPermission("PG.pg.reload")) { player.sendMessage("§cNo permission."); return true; }
                plugin.reloadConfig();
                player.sendMessage("§aDreamPGs config reloaded.");
                return true;
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6PG Commands:");
        p.sendMessage("§e/pg create§7 - create a PG private game (opens team assign GUI)");
        p.sendMessage("§e/pg start§7 - start the private match with your party");
        p.sendMessage("§e/pg disband§7 - disband your private game");
        p.sendMessage("§e/pg info§7 - show your private game info");
        p.sendMessage("§e/pg reload§7 - reload the plugin config");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "create", "start", "disband", "info", "reload");
        }
        return Collections.emptyList();
    }
}
