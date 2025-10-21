package com.dreamrela.bw;

import com.dreamrela.DreamPGs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;

public class BW1058Hook {

    private final DreamPGs plugin;
    private Object bedWarsApi;
    private java.util.Set<java.util.UUID> protectedPlayers = new java.util.HashSet<>();

    public BW1058Hook(DreamPGs plugin) {
        this.plugin = plugin;
        try {
            Class<?> bwClazz = Class.forName("com.andrei1058.bedwars.api.BedWars");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(bwClazz);
            if (rsp != null) {
                bedWarsApi = rsp.getProvider();
            }
        } catch (Throwable t) {
            bedWarsApi = null;
        }

        // Register protection listener
        registerProtectionListener();
    }

    public boolean isHooked() {
        return bedWarsApi != null;
    }

    private void registerProtectionListener() {
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
            public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
                if (!(e.getEntity() instanceof Player))
                    return;
                Player p = (Player) e.getEntity();

                if (protectedPlayers.contains(p.getUniqueId())) {
                    e.setCancelled(true);
                    plugin.getLogger().info("🛡️ Cancelled damage for protected player: " + p.getName());
                }
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
            public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
                Player p = e.getEntity();
                if (protectedPlayers.contains(p.getUniqueId())) {
                    plugin.getLogger().info("🛡️ Player " + p.getName() + " died but is protected - will respawn");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        p.spigot().respawn();
                        p.setHealth(20);
                        p.setFoodLevel(20);
                    }, 1L);
                }
            }
        }, plugin);
    }

    private void protectPlayer(Player p, long durationTicks) {
        protectedPlayers.add(p.getUniqueId());
        plugin.getLogger().info("🛡️ Protecting " + p.getName() + " for " + (durationTicks / 20) + " seconds");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            protectedPlayers.remove(p.getUniqueId());
            plugin.getLogger().info("🛡️ Protection removed for " + p.getName());
        }, durationTicks);
    }

    public void startPrivateMatchWithTwoTeams(List<Player> players, List<String> allowedTeams) {
        if (bedWarsApi == null) {
            players.forEach(p -> p.sendMessage("§cBW1058 not hooked."));
            return;
        }
        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null) {
                players.forEach(p -> p.sendMessage("§cBW1058: Arena util not available."));
                return;
            }
            List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            if (arenas == null || arenas.isEmpty()) {
                players.forEach(p -> p.sendMessage("§cNo arenas available."));
                return;
            }

            Object chosenArena = null;
            for (Object a : arenas) {
                String status = String.valueOf(safeInvoke(a, "getStatus"));
                if (isJoinableStatus(status)) {
                    chosenArena = a;
                    break;
                }
            }
            if (chosenArena == null) {
                players.forEach(p -> p.sendMessage("§cNo joinable arena found."));
                return;
            }

            String arenaName = resolveArenaName(chosenArena);

            int success = 0;
            int idx = 0;
            java.util.List<String> teams = new java.util.ArrayList<>();
            if (allowedTeams != null) {
                for (String t : allowedTeams)
                    if (t != null && !t.trim().isEmpty())
                        teams.add(t.trim().toUpperCase(java.util.Locale.ROOT));
            }
            if (teams.size() < 2)
                teams = java.util.Arrays.asList("RED", "GREEN");

            for (Player p : players) {
                String teamName = teams.get(idx % teams.size());
                boolean joined = tryJoinPlayerToArena(arenaUtil, chosenArena, arenaName, p);
                if (joined) {
                    success++;
                    idx++;

                    // CRITICAL: Protect player from death for 30 seconds
                    protectPlayer(p, 600L);

                    forceAssignTeamDelayed(chosenArena, p, teamName);
                }
            }

            for (Player p : players) {
                if (success > 0) {
                    p.sendMessage(
                            "§aSent " + success + "/" + players.size() + " player(s) to arena §e" + arenaName + "§a.");
                } else {
                    p.sendMessage("§cFailed to send players to an arena.");
                }
            }
        } catch (Throwable t) {
            players.forEach(p -> p.sendMessage("§cFailed to start private match: " + t.getClass().getSimpleName()));
            plugin.getLogger().warning("BW1058 hook error: " + t.getMessage());
        }
    }

    public java.util.List<String> listJoinableArenas() {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (bedWarsApi == null)
            return names;
        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null)
                return names;
            java.util.List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            if (arenas == null)
                return names;
            for (Object a : arenas) {
                String status = String.valueOf(safeInvoke(a, "getStatus"));
                if (isJoinableStatus(status)) {
                    String nm = resolveArenaName(a);
                    if (nm != null)
                        names.add(nm);
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    public java.util.List<String> listArenasByGroup(String groupName, boolean onlyJoinable) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (bedWarsApi == null)
            return names;
        if (groupName == null || groupName.isEmpty())
            return names;
        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null)
                return names;
            java.util.List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            if (arenas == null)
                return names;
            for (Object a : arenas) {
                String g = resolveGroupName(a);
                if (g == null || !g.equalsIgnoreCase(groupName))
                    continue;
                if (onlyJoinable) {
                    String status = String.valueOf(safeInvoke(a, "getStatus"));
                    if (!isJoinableStatus(status))
                        continue;
                }
                String nm = resolveArenaName(a);
                if (nm != null)
                    names.add(nm);
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private String resolveGroupName(Object arena) {
        if (arena == null)
            return null;
        Object group = safeInvoke(arena, "getGroup");
        if (group == null)
            group = safeInvoke(arena, "getArenaGroup");
        if (group == null)
            group = safeInvoke(arena, "getCategory");
        if (group == null)
            group = safeInvoke(arena, "getMode");
        if (group == null) {
            Object name = safeInvoke(arena, "getGroupName");
            if (name == null)
                name = safeInvoke(arena, "getCategoryName");
            if (name != null)
                return String.valueOf(name);
        }
        if (group == null)
            return null;
        if (group instanceof String)
            return (String) group;
        Object name = safeInvoke(group, "getName");
        return name != null ? String.valueOf(name) : String.valueOf(group);
    }

    public boolean startPrivateMatchWithAssignments(String arenaName, java.util.List<Player> players,
            java.util.Map<java.util.UUID, String> assignments) {
        if (bedWarsApi == null)
            return false;

        plugin.getLogger().info("=== STARTING PRIVATE MATCH WITH ASSIGNMENTS ===");
        plugin.getLogger().info("Arena: " + arenaName);
        for (java.util.Map.Entry<java.util.UUID, String> e : assignments.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                plugin.getLogger().info("Assignment: " + p.getName() + " -> " + e.getValue());
            }
        }

        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            java.util.List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            Object chosenArena = null;
            for (Object a : arenas) {
                String nm = resolveArenaName(a);
                if (nm != null && arenaName.equalsIgnoreCase(nm)) {
                    chosenArena = a;
                    plugin.getLogger().info("Found arena: " + nm);
                    break;
                }
            }

            if (chosenArena == null) {
                plugin.getLogger().severe("Arena not found: " + arenaName);
                plugin.getLogger().info("Available arenas:");
                for (Object a : arenas) {
                    String nm = resolveArenaName(a);
                    String status = String.valueOf(safeInvoke(a, "getStatus"));
                    plugin.getLogger().info("  - " + nm + " (status: " + status + ")");
                }
                players.forEach(p -> p.sendMessage("§cArena not found: §e" + arenaName));
                return false;
            }

            // Check arena status
            String status = String.valueOf(safeInvoke(chosenArena, "getStatus"));
            plugin.getLogger().info("Arena status: " + status);
            if (!isJoinableStatus(status)) {
                plugin.getLogger().warning("Arena " + arenaName + " is not joinable! Status: " + status);
                players.forEach(
                        p -> p.sendMessage("§cArena §e" + arenaName + "§c is not available (status: " + status + ")"));
                return false;
            }

            // Join all players first
            int success = 0;
            for (Player p : players) {
                boolean joined = tryJoinPlayerToArena(arenaUtil, chosenArena, arenaName, p);
                if (joined) {
                    success++;
                    plugin.getLogger().info("Player " + p.getName() + " joined arena");
                } else {
                    // Last resort: Try teleporting to arena spawn
                    plugin.getLogger().warning("API join failed for " + p.getName() + ", trying teleport fallback...");
                    if (tryTeleportToArena(chosenArena, p)) {
                        success++;
                        plugin.getLogger().info("Player " + p.getName() + " teleported to arena");
                    }
                }
            }

            if (success == 0) {
                players.forEach(p -> p.sendMessage("§cFailed to join arena §e" + arenaName));
                return false;
            }

            // Now force assign teams with EXTREME prejudice
            final Object finalArena = chosenArena;
            for (Player p : players) {
                String teamName = assignments.get(p.getUniqueId());
                if (teamName != null) {
                    plugin.getLogger().info("Force assigning " + p.getName() + " to " + teamName);

                    // CRITICAL: Protect player from death for 30 seconds
                    protectPlayer(p, 600L);

                    forceAssignTeamDelayed(finalArena, p, teamName);
                }
            }

            for (Player p : players) {
                p.sendMessage("§aJoined arena §e" + arenaName + "§a with team assignments.");
            }
            return true;
        } catch (Throwable t) {
            players.forEach(p -> p.sendMessage("§cFailed to start: " + t.getMessage()));
            plugin.getLogger().warning("BW1058 assignment error: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    // Force assign with verification attempts (no infinite loops)
    private void forceAssignTeamDelayed(Object arena, Player p, String teamName) {
        String normalized = normalizeTeamName(teamName);

        // Attempt immediately
        Bukkit.getScheduler().runTask(plugin, () -> {
            bruteForceAssign(arena, p, normalized);
            forceTeamMembership(arena, p, normalized);
        });

        // Schedule verification checks at specific intervals (stops after 15 seconds)
        long[] delays = { 5L, 10L, 15L, 20L, 30L, 40L, 60L, 80L, 100L, 150L, 200L, 300L };
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String current = getPlayerTeamName(arena, p);
                if (current == null || !normalized.equals(current)) {
                    plugin.getLogger().info("Reassigning " + p.getName() + " from " + current + " to " + normalized);
                    bruteForceAssign(arena, p, normalized);

                    // Add small delay before forcing membership
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        forceTeamMembership(arena, p, normalized);
                    }, 2L);
                } else {
                    plugin.getLogger().info("✓ " + p.getName() + " confirmed on team " + normalized);
                }
            }, delay);
        }
    }

    // Try EVERY possible method to assign a team
    private void bruteForceAssign(Object arena, Player p, String teamName) {
        try {
            Object team = findTeamByName(arena, teamName);
            if (team == null) {
                plugin.getLogger().severe("❌ TEAM NOT FOUND: " + teamName);
                plugin.getLogger().severe("Available teams in arena:");
                try {
                    List<?> teams = castList(safeInvoke(arena, "getTeams"));
                    if (teams != null) {
                        for (Object t : teams) {
                            String color = String.valueOf(safeInvoke(t, "getColor"));
                            String name = String.valueOf(safeInvoke(t, "getName"));
                            plugin.getLogger().severe("  - Color: " + color + ", Name: " + name);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return;
            }

            plugin.getLogger().info("🎯 Assigning " + p.getName() + " to team " + teamName);

            // Get BedWars player wrapper
            Object bwPlayer = getBedWarsPlayer(p);
            if (bwPlayer != null) {
                plugin.getLogger().info("✓ Got BedWarsPlayer wrapper for " + p.getName());
            } else {
                plugin.getLogger().warning("⚠ No BedWarsPlayer wrapper for " + p.getName());
            }

            // METHOD 0: Check current team first
            Object currentTeam = safeInvoke(arena, "getTeam", p.getUniqueId());
            if (currentTeam == null && bwPlayer != null) {
                currentTeam = safeInvoke(arena, "getTeam", bwPlayer);
            }

            if (currentTeam != null) {
                String currName = String.valueOf(safeInvoke(currentTeam, "getName"));
                plugin.getLogger().info("Player is currently on team: " + currName);

                if (currentTeam != team) {
                    plugin.getLogger().info("Attempting to remove from current team...");
                    // Try various removal methods
                    tryInvokeLogged(currentTeam, "removeMember", p);
                    tryInvokeLogged(currentTeam, "removePlayer", p);
                    tryInvokeLogged(currentTeam, "remove", p);
                    tryInvokeLogged(arena, "removePlayerFromTeam", p);
                }
            }

            boolean success = false;

            // METHOD 1: Try team.addPlayer() with Player
            if (tryInvokeLogged(team, "addPlayer", p)) {
                success = true;
            }

            // METHOD 2: Try with BedWarsPlayer wrapper
            if (bwPlayer != null && tryInvokeLogged(team, "addPlayer", bwPlayer)) {
                success = true;
            }

            // METHOD 3: Try arena level assignment
            if (tryInvokeLogged(arena, "addPlayerToTeam", p, team)) {
                success = true;
            }

            // METHOD 4: Try setTeam
            if (tryInvokeLogged(arena, "setTeam", p, team)) {
                success = true;
            }

            // METHOD 5: Try reJoin (for switching teams)
            if (tryInvokeLogged(team, "reJoin", p)) {
                success = true;
            }

            // METHOD 6: Try with UUID
            if (tryInvokeLogged(team, "addPlayer", p.getUniqueId())) {
                success = true;
            }

            // METHOD 7: Try firstSpawn (v23 specific)
            if (tryInvokeLogged(team, "firstSpawn", p)) {
                success = true;
            }

            // METHOD 8: Try spawn method
            if (tryInvokeLogged(team, "spawn", p)) {
                success = true;
            }

            // METHOD 9: Try defaultSword (ensures player has team equipment)
            tryInvokeLogged(team, "defaultSword", p);

            // METHOD 10: Try respawnPlayer (marks as alive team member)
            if (tryInvokeLogged(team, "respawnPlayer", p)) {
                success = true;
            }

            // METHOD 11: Try sendPlayerToTeam
            if (tryInvokeLogged(arena, "sendPlayerToTeam", p, team)) {
                success = true;
            }

            // METHOD 12: Try addSpectator then remove (sometimes fixes state)
            Object spectators = safeInvoke(arena, "getSpectators");
            if (spectators instanceof java.util.List) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> specList = (java.util.List<Object>) spectators;
                    specList.remove(p);
                    specList.remove(p.getUniqueId());
                    if (bwPlayer != null)
                        specList.remove(bwPlayer);
                } catch (Throwable ignored) {
                }
            }

            if (!success) {
                plugin.getLogger().severe("❌ ALL ASSIGNMENT METHODS FAILED for " + p.getName());
            } else {
                plugin.getLogger().info("✅ Successfully assigned " + p.getName() + " to " + teamName);
            }

        } catch (Throwable t) {
            plugin.getLogger().severe("❌ Brute force assign EXCEPTION: " + t.getMessage());
            t.printStackTrace();
        }
    }

    // NEW METHOD: Ensure player is in team's member list to prevent death on game
    // start
    private void forceTeamMembership(Object arena, Player p, String teamName) {
        try {
            Object team = findTeamByName(arena, teamName);
            if (team == null)
                return;

            Object bwPlayer = getBedWarsPlayer(p);

            // Try to add to members list using proper methods
            tryInvokeLogged(team, "addMember", p);
            if (bwPlayer != null)
                tryInvokeLogged(team, "addMember", bwPlayer);

            // Try spawning player at team spawn to reset their state
            tryInvokeLogged(team, "respawnPlayer", p);
            tryInvokeLogged(team, "spawnPlayer", p);
            tryInvokeLogged(team, "spawn", p);

            plugin.getLogger().info("🔧 Forced team membership for " + p.getName() + " in " + teamName);

        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to force team membership: " + t.getMessage());
        }
    }

    private boolean tryJoinPlayerToArena(Object arenaUtil, Object arena, String arenaName, Player p) {
        plugin.getLogger().info("Attempting to join " + p.getName() + " to arena " + arenaName);

        // METHOD 1: Try direct arena methods
        if (arena != null) {
            if (tryInvokeLogged(arena, "addPlayer", p))
                return true;
            if (tryInvokeLogged(arena, "addPlayer", p, true))
                return true;
            if (tryInvokeLogged(arena, "addPlayer", p, false))
                return true;
            if (tryInvokeLogged(arena, "join", p))
                return true;
            if (tryInvokeLogged(arena, "joinPlayer", p))
                return true;
        }

        // METHOD 2: Try via arena util
        if (arenaUtil != null) {
            if (arena != null) {
                if (tryInvokeLogged(arenaUtil, "joinArena", p, arena))
                    return true;
                if (tryInvokeLogged(arenaUtil, "addPlayer", p, arena))
                    return true;
            }
            if (arenaName != null) {
                if (tryInvokeLogged(arenaUtil, "joinArena", p, arenaName))
                    return true;
                if (tryInvokeLogged(arenaUtil, "addPlayer", p, arenaName))
                    return true;
            }
        }

        // METHOD 3: Try via API provider
        if (bedWarsApi != null) {
            if (arena != null && tryInvokeLogged(bedWarsApi, "joinArena", p, arena))
                return true;
            if (arenaName != null && tryInvokeLogged(bedWarsApi, "joinArena", p, arenaName))
                return true;
        }

        // METHOD 4: Command fallback (most reliable for BedWars1058)
        if (arenaName != null && !arenaName.isEmpty()) {
            plugin.getLogger().info("Trying command: /bw join " + arenaName);
            try {
                boolean result = p.performCommand("bw join " + arenaName);
                if (result) {
                    plugin.getLogger().info("✓ Command succeeded for " + p.getName());
                    // Small delay to ensure join completes
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                    return true;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Command failed: " + t.getMessage());
            }
        }

        plugin.getLogger().warning("Failed to join " + p.getName() + " - ALL methods failed!");
        return false;
    }

    private boolean tryInvokeLogged(Object target, String method, Object... args) {
        try {
            invoke(target, method, args);
            plugin.getLogger().info("✓ Successfully called: " + method);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().info("✗ Failed: " + method + " - " + t.getClass().getSimpleName());
            return false;
        }
    }

    // Fallback: Try to teleport player to arena if API join fails
    private boolean tryTeleportToArena(Object arena, Player p) {
        try {
            // Try to get spawn location
            Object spawnLoc = safeInvoke(arena, "getSpectatorLocation");
            if (spawnLoc == null)
                spawnLoc = safeInvoke(arena, "getWaitingLocation");
            if (spawnLoc == null)
                spawnLoc = safeInvoke(arena, "getLobbyLocation");

            if (spawnLoc instanceof org.bukkit.Location) {
                p.teleport((org.bukkit.Location) spawnLoc);
                plugin.getLogger().info("Teleported " + p.getName() + " to arena location");
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Teleport fallback failed: " + t.getMessage());
        }
        return false;
    }

    private boolean isJoinableStatus(String status) {
        if (status == null)
            return false;
        String s = status.toUpperCase(java.util.Locale.ROOT);
        return s.contains("WAIT") || s.contains("LOBBY") || s.equals("RESTARTING");
    }

    private String resolveArenaName(Object arena) {
        Object name = safeInvoke(arena, "getArenaName");
        if (name == null)
            name = safeInvoke(arena, "getName");
        return name != null ? String.valueOf(name) : null;
    }

    private Object findTeamByName(Object arena, String teamName) {
        String target = normalizeTeamName(teamName);
        try {
            List<?> teams = castList(safeInvoke(arena, "getTeams"));
            if (teams == null)
                return null;
            for (Object t : teams) {
                String byColor = normalizeCandidate(safeInvoke(t, "getColor"));
                String byName = normalizeCandidate(safeInvoke(t, "getName"));
                String byDisplay = normalizeCandidate(safeInvoke(t, "getDisplayName"));
                if (target.equals(byColor) || target.equals(byName) || target.equals(byDisplay)) {
                    return t;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object safeInvoke(Object target, String method, Object... args) {
        try {
            if (target == null)
                return null;
            return invoke(target, method, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object obj) {
        try {
            return (List<T>) obj;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object invoke(Object target, String method, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
            types[i] = args[i] != null ? args[i].getClass() : Object.class;
        Method m = findMethod(target.getClass(), method, types);
        if (m == null)
            throw new NoSuchMethodException(method);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private boolean tryInvoke(Object target, String method, Object... args) {
        try {
            invoke(target, method, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Method findMethod(Class<?> c, String name, Class<?>[] types) {
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name))
                continue;
            if (m.getParameterCount() != types.length)
                continue;
            boolean ok = true;
            Class<?>[] pts = m.getParameterTypes();
            for (int i = 0; i < pts.length; i++) {
                if (types[i] == Object.class)
                    continue;
                if (!pts[i].isAssignableFrom(types[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok)
                return m;
        }
        return null;
    }

    private Object getBedWarsPlayer(Player p) {
        try {
            Object pm = safeInvoke(bedWarsApi, "getPlayerManager");
            if (pm == null)
                pm = safeInvoke(bedWarsApi, "getPlayerUtil");
            Object bw = safeInvoke(pm, "getBedwarsPlayer", p);
            if (bw == null)
                bw = safeInvoke(pm, "getBedwarsPlayer", p.getUniqueId());
            if (bw == null)
                bw = safeInvoke(pm, "getPlayer", p);
            if (bw == null)
                bw = safeInvoke(pm, "getPlayer", p.getUniqueId());
            return bw;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String normalizeTeamName(String name) {
        if (name == null)
            return null;
        String n = name.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        if (n.equals("GREY"))
            n = "GRAY";
        if (n.equals("LIGHTBLUE") || n.equals("LIGHT_BLUE") || n.equals("CYAN"))
            n = "AQUA";
        return n;
    }

    private String normalizeCandidate(Object val) {
        if (val == null)
            return null;
        return normalizeTeamName(String.valueOf(val));
    }

    private String getPlayerTeamName(Object arena, Player p) {
        try {
            Object bwPlayer = getBedWarsPlayer(p);
            Object team = bwPlayer != null ? safeInvoke(arena, "getTeam", bwPlayer) : null;
            if (team == null)
                team = safeInvoke(arena, "getTeam", p.getUniqueId());
            if (team == null)
                team = bwPlayer != null ? safeInvoke(arena, "getPlayerTeam", bwPlayer) : null;
            if (team == null)
                team = safeInvoke(arena, "getPlayerTeam", p);
            if (team == null)
                return null;
            String byColor = normalizeCandidate(safeInvoke(team, "getColor"));
            String byName = normalizeCandidate(safeInvoke(team, "getName"));
            return byColor != null ? byColor : byName;
        } catch (Throwable t) {
            return null;
        }
    }
}