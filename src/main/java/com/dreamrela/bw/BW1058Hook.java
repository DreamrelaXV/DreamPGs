package com.dreamrela.bw;

import com.dreamrela.DreamPGs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;

// Robust hook to BedWars1058 API with fallback to commands
public class BW1058Hook {

    private final DreamPGs plugin;
    private Object bedWarsApi; // com.andrei1058.bedwars.api.BedWars

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
    }

    public boolean isHooked() {
        return bedWarsApi != null;
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
            boolean toggle = true; // RED first, then GREEN
            for (Player p : players) {
                boolean joined = tryJoinPlayerToArena(arenaUtil, chosenArena, arenaName, p);
                if (joined) {
                    success++;
                    String teamName = allowedTeams != null && allowedTeams.size() >= 2
                            ? (toggle ? allowedTeams.get(0) : allowedTeams.get(1))
                            : null;
                    toggle = !toggle;
                    if (teamName != null) tryAssignTeam(chosenArena, p, teamName);
                }
            }

            for (Player p : players) {
                if (success > 0) {
                    p.sendMessage("§aSent " + success + "/" + players.size() + " player(s) to arena §e" + arenaName + "§a.");
                } else {
                    p.sendMessage("§cFailed to send players to an arena. Make sure BedWars1058 is installed and has joinable arenas.");
                }
            }
        } catch (Throwable t) {
            players.forEach(p -> p.sendMessage("§cFailed to start private match: " + t.getClass().getSimpleName()));
            plugin.getLogger().warning("BW1058 hook error: " + t.getMessage());
        }
    }

    // List arena names that appear joinable (WAITING/LOBBY/RESTARTING)
    public java.util.List<String> listJoinableArenas() {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (bedWarsApi == null) return names;
        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null) return names;
            java.util.List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            if (arenas == null) return names;
            for (Object a : arenas) {
                String status = String.valueOf(safeInvoke(a, "getStatus"));
                if (isJoinableStatus(status)) {
                    String nm = resolveArenaName(a);
                    if (nm != null) names.add(nm);
                }
            }
        } catch (Throwable ignored) {}
        return names;
    }

    // List arena names filtered by BedWars group/category name. Optionally only include joinable ones.
    public java.util.List<String> listArenasByGroup(String groupName, boolean onlyJoinable) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (bedWarsApi == null) return names;
        if (groupName == null || groupName.isEmpty()) return names;
        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null) return names;
            java.util.List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            if (arenas == null) return names;
            for (Object a : arenas) {
                String g = resolveGroupName(a);
                if (g == null || !g.equalsIgnoreCase(groupName)) continue;
                if (onlyJoinable) {
                    String status = String.valueOf(safeInvoke(a, "getStatus"));
                    if (!isJoinableStatus(status)) continue;
                }
                String nm = resolveArenaName(a);
                if (nm != null) names.add(nm);
            }
        } catch (Throwable ignored) {}
        return names;
    }

    private String resolveGroupName(Object arena) {
        if (arena == null) return null;
        Object group = safeInvoke(arena, "getGroup");
        if (group == null) group = safeInvoke(arena, "getArenaGroup");
        if (group == null) group = safeInvoke(arena, "getCategory");
        if (group == null) group = safeInvoke(arena, "getMode");
        if (group == null) {
            Object name = safeInvoke(arena, "getGroupName");
            if (name == null) name = safeInvoke(arena, "getCategoryName");
            if (name != null) return String.valueOf(name);
        }
        if (group == null) return null;
        if (group instanceof String) return (String) group;
        Object name = safeInvoke(group, "getName");
        return name != null ? String.valueOf(name) : String.valueOf(group);
    }

    // Start match in a specific arena and assign teams according to assignments map
    public boolean startPrivateMatchWithAssignments(String arenaName, java.util.List<Player> players, java.util.Map<java.util.UUID, String> assignments) {
        if (bedWarsApi == null) return false;
        try {
            Object arenaUtil = safeInvoke(bedWarsApi, "getArenaUtil");
            java.util.List<?> arenas = castList(safeInvoke(arenaUtil, "getArenas"));
            Object chosenArena = null;
            for (Object a : arenas) {
                String nm = resolveArenaName(a);
                if (nm != null && arenaName.equalsIgnoreCase(nm)) { chosenArena = a; break; }
            }
            if (chosenArena == null) {
                players.forEach(p -> p.sendMessage("§cArena not found: §e" + arenaName));
                return false;
            }

            int success = 0;
            for (Player p : players) {
                boolean joined = tryJoinPlayerToArena(arenaUtil, chosenArena, arenaName, p);
                if (joined) {
                    success++;
                    String teamName = assignments.getOrDefault(p.getUniqueId(), null);
                    if (teamName != null) tryAssignTeam(chosenArena, p, teamName);
                }
            }
            for (Player p : players) {
                if (success > 0) {
                    p.sendMessage("§aJoined arena §e" + arenaName + "§a (" + success + "/" + players.size() + ") with assigned teams where possible.");
                } else {
                    p.sendMessage("§cFailed to join arena §e" + arenaName + "§c. Ensure it is joinable.");
                }
            }
            return success > 0;
        } catch (Throwable t) {
            players.forEach(p -> p.sendMessage("§cFailed to start in arena: " + t.getClass().getSimpleName()));
            plugin.getLogger().warning("BW1058 startWithAssignments error: " + t.getMessage());
            return false;
        }
    }

    private void tryAssignTeam(Object arena, Player p, String teamName) {
        try {
            Object team = findTeamByName(arena, teamName);
            if (team != null) {
                if (!tryInvoke(team, "addMember", p)) {
                    tryInvoke(arena, "assignTeam", p, team);
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean tryJoinPlayerToArena(Object arenaUtil, Object arena, String arenaName, Player p) {
        // Try direct arena methods
        if (arena != null) {
            if (tryInvoke(arena, "addPlayer", p)) return true;
            if (tryInvoke(arena, "join", p)) return true;
        }
        // Try via arena util
        if (arenaUtil != null) {
            if (arena != null && tryInvoke(arenaUtil, "sendPlayerToArena", p, arena)) return true;
            if (arena != null && tryInvoke(arenaUtil, "joinArena", p, arena)) return true;
            if (arenaName != null && tryInvoke(arenaUtil, "sendPlayerToArena", p, arenaName)) return true;
            if (arenaName != null && tryInvoke(arenaUtil, "joinArena", p, arenaName)) return true;
        }
        // Try provider directly
        if (arena != null && tryInvoke(bedWarsApi, "joinArena", p, arena)) return true;
        if (arenaName != null && tryInvoke(bedWarsApi, "joinArena", p, arenaName)) return true;

        // Fallback to command execution as the player
        if (arenaName != null) {
            try { if (p.performCommand("bw join " + arenaName)) return true; } catch (Throwable ignored) {}
            try { if (p.performCommand("bedwars join " + arenaName)) return true; } catch (Throwable ignored) {}
            try { if (p.performCommand("bw1058 join " + arenaName)) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    private boolean isJoinableStatus(String status) {
        if (status == null) return false;
        String s = status.toUpperCase(java.util.Locale.ROOT);
        return s.contains("WAIT") || s.contains("LOBBY") || s.equals("RESTARTING");
    }

    private String resolveArenaName(Object arena) {
        Object name = null;
        try { name = safeInvoke(arena, "getArenaName"); } catch (Throwable ignored) {}
        if (name == null) try { name = safeInvoke(arena, "getName"); } catch (Throwable ignored2) {}
        return name != null ? String.valueOf(name) : null;
    }

    private Object findTeamByName(Object arena, String teamName) {
        try {
            List<?> teams = castList(safeInvoke(arena, "getTeams"));
            if (teams == null) return null;
            for (Object t : teams) {
                Object color = safeInvoke(t, "getColor");
                String name = color != null ? String.valueOf(color) : String.valueOf(safeInvoke(t, "getName"));
                if (teamName.equalsIgnoreCase(name)) return t;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object safeInvoke(Object target, String method, Object... args) {
        try {
            return invoke(target, method, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object obj) {
        try { return (List<T>) obj; } catch (Throwable ignored) { return null; }
    }

    private Object invoke(Object target, String method, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i].getClass();
        Method m = findMethod(target.getClass(), method, types);
        if (m == null) throw new NoSuchMethodException(method);
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
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != types.length) continue;
            boolean ok = true;
            Class<?>[] pts = m.getParameterTypes();
            for (int i = 0; i < pts.length; i++) {
                if (!pts[i].isAssignableFrom(types[i])) { ok = false; break; }
            }
            if (ok) return m;
        }
        return null;
    }
}
