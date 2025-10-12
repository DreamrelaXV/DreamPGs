package com.dreamrela.privategame;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PrivateGame {
    private final UUID host;
    private final String category; // Always "PG" per your request
    private final Set<UUID> members = new LinkedHashSet<>();
    private final List<String> allowedTeams; // [RED, GREEN]

    // Team assignments per member: "RED" / "GREEN" / null
    private final Map<UUID, String> assignments = new java.util.HashMap<>();

    // Optional selected arena name (map)
    private String arenaName;

    public PrivateGame(UUID host, String category, List<String> allowedTeams) {
        this.host = host;
        this.category = category;
        this.allowedTeams = allowedTeams;
        this.members.add(host);
    }

    public UUID getHost() {
        return host;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getAllowedTeams() {
        return Collections.unmodifiableList(allowedTeams);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void setMembers(Set<UUID> m) {
        this.members.clear();
        this.members.addAll(m);
    }

    public void setTeam(UUID uuid, String team) {
        if (team == null) assignments.remove(uuid);
        else assignments.put(uuid, team);
    }

    public Map<UUID, String> getAssignments() {
        return java.util.Collections.unmodifiableMap(assignments);
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }

    public String getArenaName() {
        return arenaName;
    }
}
