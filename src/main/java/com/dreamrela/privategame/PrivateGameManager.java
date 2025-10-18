package com.dreamrela.privategame;

import com.dreamrela.DreamPGs;
import com.dreamrela.bw.BW1058Hook;
import com.dreamrela.party.PartyManager;
import com.dreamrela.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PrivateGameManager {
    private final DreamPGs plugin;
    private final BW1058Hook bwHook;
    private final TeamManager teamManager;

    // Host UUID -> PrivateGame
    private final Map<UUID, PrivateGame> games = new ConcurrentHashMap<>();

    public PrivateGameManager(DreamPGs plugin, BW1058Hook bwHook, TeamManager teamManager) {
        this.plugin = plugin;
        this.bwHook = bwHook;
        this.teamManager = teamManager;
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
        List<String> allowedTeams = new ArrayList<>(teamManager.getEnabledTeams());
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
        List<String> allowed = pg.getAllowedTeams();
        if (allowed == null || allowed.size() < 2) allowed = teamManager.getEnabledTeams();
        final java.util.Set<String> allowedSet = new java.util.HashSet<>();
        for (String t : allowed) if (t != null) allowedSet.add(t.toUpperCase(java.util.Locale.ROOT));

        if (pg.getArenaName() != null && !pg.getArenaName().isEmpty() && !pg.getAssignments().isEmpty()) {
            // Filter/normalize assignments to allowed teams only; unknowns are remapped round-robin to allowed
            java.util.Map<java.util.UUID, String> filtered = new java.util.LinkedHashMap<>();
            int rr = 0;
            for (Player p : onlineMembers) {
                String val = pg.getAssignments().get(p.getUniqueId());
                String norm = val == null ? null : val.trim().toUpperCase(java.util.Locale.ROOT);
                if (norm == null || !allowedSet.contains(norm)) {
                    norm = allowed.get(rr % allowed.size()).toUpperCase(java.util.Locale.ROOT);
                    rr++;
                }
                filtered.put(p.getUniqueId(), norm);
            }
            started = bwHook.startPrivateMatchWithAssignments(pg.getArenaName(), onlineMembers, filtered);
        }
        if (!started) {
            // Round-robin through configured teams to ensure proper distribution (limited strictly to allowed)
            bwHook.startPrivateMatchWithTwoTeams(onlineMembers, allowed);
        }

        // increment hosting count after start attempt
        if (h != null) plugin.getStorageManager().incrementHosted(h.getUniqueId());
    }
}
