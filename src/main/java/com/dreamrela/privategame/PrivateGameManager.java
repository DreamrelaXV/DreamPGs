package com.dreamrela.privategame;

import com.dreamrela.DreamPGs;
import com.dreamrela.bw.BW1058Hook;
import com.dreamrela.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PrivateGameManager {
    private final DreamPGs plugin;
    private final BW1058Hook bwHook;

    // Host UUID -> PrivateGame
    private final Map<UUID, PrivateGame> games = new ConcurrentHashMap<>();

    public PrivateGameManager(DreamPGs plugin, BW1058Hook bwHook) {
        this.plugin = plugin;
        this.bwHook = bwHook;
    }

    public void shutdown() {
        games.clear();
    }

    public Optional<PrivateGame> getByHost(UUID host) {
        return Optional.ofNullable(games.get(host));
    }

    public PrivateGame create(UUID host) {
        PartyManager pm = plugin.getPartyManager();
        java.util.Optional<com.dreamrela.party.Party> optParty = pm.getParty(host);
        if (!optParty.isPresent() || optParty.get().size() < 2) {
            // Must be in a party with at least 2 members to create a PG
            return null;
        }
        List<String> allowedTeams = Arrays.asList("RED", "GREEN");
        PrivateGame pg = new PrivateGame(host, "PG", allowedTeams);
        // sync members from party at create time (only party members)
        pg.setMembers(new java.util.LinkedHashSet<>(optParty.get().getMembers()));
        games.put(host, pg);
        return pg;
    }

    public boolean disband(UUID host) {
        return games.remove(host) != null;
    }

    public boolean updateMembersFromParty(UUID host) {
        Optional<PrivateGame> opt = getByHost(host);
        if (!opt.isPresent()) return false;
        Set<UUID> members = plugin.getPartyManager().getMembers(host);
        if (members.isEmpty()) members = Collections.singleton(host);
        opt.get().setMembers(members);
        return true;
    }

    public void start(UUID host) {
        Optional<PrivateGame> opt = getByHost(host);
        if (!opt.isPresent()) {
            Player h = Bukkit.getPlayer(host);
            if (h != null) h.sendMessage("§cNo private game. Use §e/pg create§c first.");
            return;
        }
        PrivateGame pg = opt.get();
        if (!bwHook.isHooked()) {
            Player h = Bukkit.getPlayer(host);
            if (h != null) h.sendMessage("§cBedWars1058 API not hooked. Cannot start.");
            return;
        }

        Player h = Bukkit.getPlayer(host);
        if (h != null) {
            if (!plugin.getStorageManager().canHostToday(h)) {
                int rem = plugin.getStorageManager().getRemainingHosts(h);
                h.sendMessage("§cYou have reached your daily hosting limit. Remaining: §e" + rem);
                return;
            }
        }

        List<Player> onlineMembers = pg.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (onlineMembers.size() < 1) {
            if (h != null) h.sendMessage("§cNo online members to start.");
            return;
        }

        // Try start using selected arena and explicit assignments; fallback to alternating
        boolean started = false;
        if (pg.getArenaName() != null && !pg.getArenaName().isEmpty() && !pg.getAssignments().isEmpty()) {
            started = bwHook.startPrivateMatchWithAssignments(pg.getArenaName(), onlineMembers, pg.getAssignments());
        }
        if (!started) {
            bwHook.startPrivateMatchWithTwoTeams(onlineMembers, pg.getAllowedTeams());
        }

        // increment hosting count after start attempt
        if (h != null) plugin.getStorageManager().incrementHosted(h.getUniqueId());
    }
}
