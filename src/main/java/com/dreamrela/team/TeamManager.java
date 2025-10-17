package com.dreamrela.team;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TeamManager {

    private final FileConfiguration config;
    private List<String> enabledTeams;

    public TeamManager(FileConfiguration config) {
        this.config = config;
        reload();
    }

    public void reload() {
        // Support both YAML list and comma-separated string at teams.enabled-teams
        List<String> list = config.getStringList("teams.enabled-teams");
        if (list == null || list.isEmpty()) {
            String raw = config.getString("teams.enabled-teams", "RED,GREEN");
            list = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        // Normalize to uppercase, preserve order, dedupe sequentially
        List<String> normalized = new ArrayList<>();
        for (String s : list) {
            String up = s.toUpperCase(Locale.ROOT);
            if (!normalized.contains(up)) normalized.add(up);
        }
        // Ensure we have at least 2 teams; fallback if needed
        if (normalized.size() < 2) {
            normalized = Arrays.asList("RED", "GREEN");
        }
        this.enabledTeams = Collections.unmodifiableList(normalized);
    }

    public List<String> getEnabledTeams() {
        return enabledTeams;
    }
}