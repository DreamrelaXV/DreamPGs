package com.dreamrela.commands;

import com.dreamrela.DreamPGs;
import com.dreamrela.party.Party;
import com.dreamrela.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;
import java.util.stream.Collectors;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final DreamPGs plugin;

    public PartyCommand(DreamPGs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use party commands.");
            return true;
        }
        Player player = (Player) sender;
        PartyManager pm = plugin.getPartyManager();

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite":
            case "inv":
                if (!player.hasPermission("PG.party.invite")) { player.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { player.sendMessage("§eUsage: /party invite <player...>"); return true; }
                if (pm.getParty(player.getUniqueId()).isPresent() && !pm.isLeader(player.getUniqueId())) {
                    player.sendMessage("§cOnly the party leader can invite.");
                    return true;
                }
                // Ensure party exists for inviter
                pm.getParty(player.getUniqueId()).orElseGet(() -> pm.createParty(player.getUniqueId()));

                int invited = 0;
                List<String> notFound = new ArrayList<>();
                Set<UUID> alreadyInvited = new HashSet<>();

                for (int i = 1; i < args.length; i++) {
                    String name = args[i];
                    Player target = Bukkit.getPlayer(name);
                    if (target == null) { notFound.add(name); continue; }
                    if (alreadyInvited.contains(target.getUniqueId())) continue;
                    alreadyInvited.add(target.getUniqueId());

                    if (pm.invite(player.getUniqueId(), target.getUniqueId())) {
                        invited++;
                        player.sendMessage("§aInvited §e" + target.getName() + "§a to the party. (Click message sent)");
                        // Send clickable /party accept message to target
                        TextComponent base = new TextComponent("§6[Party] §e" + player.getName() + " §ainvited you. ");
                        TextComponent click = new TextComponent("§a[CLICK TO ACCEPT]");
                        click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept"));
                        click.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.BaseComponent[]{ new TextComponent("Click to run /party accept") }));
                        target.spigot().sendMessage(base, click);
                    } else {
                        player.sendMessage("§cCould not send invite to §e" + target.getName() + "§c.");
                    }
                }

                if (!notFound.isEmpty()) {
                    player.sendMessage("§eNot online: §7" + String.join("§7, §r", notFound));
                }
                if (invited == 0 && notFound.isEmpty()) {
                    player.sendMessage("§cNo valid players to invite.");
                }
                return true;
            case "accept":
                if (!player.hasPermission("PG.party.accept")) { player.sendMessage("§cNo permission."); return true; }
                if (!pm.hasInvite(player.getUniqueId())) { player.sendMessage("§cYou have no pending invites."); return true; }
                if (pm.acceptInvite(player.getUniqueId())) {
                    player.sendMessage("§aJoined the party.");
                } else {
                    player.sendMessage("§cFailed to join party.");
                }
                return true;
            case "leave":
                if (!player.hasPermission("PG.party.leave")) { player.sendMessage("§cNo permission."); return true; }
                if (pm.leave(player.getUniqueId())) {
                    player.sendMessage("§eYou left the party.");
                } else {
                    player.sendMessage("§cYou are not in a party.");
                }
                return true;
            case "disband":
                if (!player.hasPermission("PG.party.disband")) { player.sendMessage("§cNo permission."); return true; }
                Optional<Party> pp = pm.getParty(player.getUniqueId());
                if (!pp.isPresent()) { player.sendMessage("§cYou are not in a party."); return true; }
                if (!pm.isLeader(player.getUniqueId())) { player.sendMessage("§cOnly the leader can disband."); return true; }
                pm.broadcast(pp.get(), "§cParty disbanded by leader.");
                pm.disband(pp.get());
                return true;
            case "promote":
                if (!player.hasPermission("PG.party.promote")) { player.sendMessage("§cNo permission."); return true; }
                if (args.length < 2) { player.sendMessage("§eUsage: /party promote <player>"); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                if (op == null || op.getUniqueId() == null) { player.sendMessage("§cPlayer not found."); return true; }
                if (pm.promote(player.getUniqueId(), op.getUniqueId())) {
                    player.sendMessage("§aPromoted §e" + args[1] + "§a to leader.");
                } else {
                    player.sendMessage("§cFailed to promote. Are you the leader and is the target in your party?");
                }
                return true;
            case "list":
                if (!player.hasPermission("PG.party.list")) { player.sendMessage("§cNo permission."); return true; }
                Optional<Party> opt = pm.getParty(player.getUniqueId());
                if (!opt.isPresent()) { player.sendMessage("§cYou are not in a party."); return true; }
                Party party = opt.get();
                String list = party.getMembers().stream()
                        .map(uuid -> {
                            OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                            String n = p != null && p.getName() != null ? p.getName() : uuid.toString();
                            return (uuid.equals(party.getLeader()) ? "§6* " : "  ") + n;
                        })
                        .collect(Collectors.joining("§7, §r"));
                player.sendMessage("§6Party Members: §r" + list);
                return true;
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6Party Commands:");
        p.sendMessage("§e/party invite <player>§7 - invite a player");
        p.sendMessage("§e/party accept§7 - accept latest invite");
        p.sendMessage("§e/party leave§7 - leave your party");
        p.sendMessage("§e/party disband§7 - disband your party (leader)");
        p.sendMessage("§e/party list§7 - list members");
        p.sendMessage("§e/party promote <player>§7 - make leader");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("invite", "accept", "leave", "disband", "list", "promote");
        }
        if (args.length == 2 && ("invite".equalsIgnoreCase(args[0]) || "promote".equalsIgnoreCase(args[0]))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
