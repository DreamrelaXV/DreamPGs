package com.dreamrela.storage;

import com.dreamrela.DreamPGs;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StorageManager {

    private final DreamPGs plugin;
    private final File playersFile;
    private final FileConfiguration playersCfg;

    public StorageManager(DreamPGs plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try { playersFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.playersCfg = YamlConfiguration.loadConfiguration(playersFile);
    }

    public void save() {
        try { playersCfg.save(playersFile); } catch (IOException e) { plugin.getLogger().warning("Failed saving players.yml: " + e.getMessage()); }
    }

    private String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    public int getHostedCount(UUID uuid) {
        String date = playersCfg.getString("hostCounts." + uuid + ".date", "");
        if (!today().equals(date)) return 0;
        return playersCfg.getInt("hostCounts." + uuid + ".count", 0);
    }

    public void incrementHosted(UUID uuid) {
        String key = "hostCounts." + uuid;
        String date = playersCfg.getString(key + ".date", "");
        if (!today().equals(date)) {
            playersCfg.set(key + ".date", today());
            playersCfg.set(key + ".count", 1);
        } else {
            playersCfg.set(key + ".count", playersCfg.getInt(key + ".count", 0) + 1);
        }
        save();
    }

    public boolean canHostToday(Player p) {
        int limit = getHostLimitForPlayer(p);
        if (limit < 0) return true; // unlimited
        return getHostedCount(p.getUniqueId()) < limit;
    }

    public int getRemainingHosts(Player p) {
        int limit = getHostLimitForPlayer(p);
        if (limit < 0) return Integer.MAX_VALUE;
        return Math.max(0, limit - getHostedCount(p.getUniqueId()));
    }

    public int getHostLimitForPlayer(Player p) {
        // Config path: hosting.groups.<group>: value (number or "unlimited")
        // Resolve primary group via LuckPerms API if present, else fallback to "default".
        String group = "default";
        LuckPerms lp = getLuckPerms();
        if (lp != null) {
            User user = lp.getPlayerAdapter(Player.class).getUser(p);
            if (user != null) group = user.getPrimaryGroup();
        }
        String base = "hosting.groups." + group;
        if (plugin.getConfig().isString(base)) {
            String v = plugin.getConfig().getString(base, "unlimited");
            if (v.equalsIgnoreCase("unlimited") || v.equalsIgnoreCase("-1")) return -1;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return -1; }
        } else if (plugin.getConfig().isInt(base)) {
            return plugin.getConfig().getInt(base);
        }
        // fallback to default
        if (plugin.getConfig().isString("hosting.groups.default")) {
            String v = plugin.getConfig().getString("hosting.groups.default", "unlimited");
            if (v.equalsIgnoreCase("unlimited") || v.equalsIgnoreCase("-1")) return -1;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return -1; }
        }
        if (plugin.getConfig().isInt("hosting.groups.default")) {
            return plugin.getConfig().getInt("hosting.groups.default");
        }
        return -1; // default unlimited
    }

    private LuckPerms getLuckPerms() {
        try {
            return Bukkit.getServicesManager().load(LuckPerms.class);
        } catch (Throwable t) {
            return null;
        }
    }
}
