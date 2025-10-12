package com.dreamrela.bw;

import com.dreamrela.DreamPGs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;

// Minimal, defensive hook to BedWars1058 API to start a private match and assign two teams (RED/GREEN)
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
        // IMPORTANT: This is a best-effort placeholder because BedWars1058 API signatures can vary by version.
        // Strategy:
        // 1) Find a waiting arena with RED and GREEN teams.
        // 2) Send players to arena and alternate team assignment.
        // If any reflective call fails, we fallback to joining without team assignment.
        if (bedWarsApi == null) {
            players.forEach(p -> p.sendMessage("§cBW1058 not hooked."));
            return;
        }
        try {
            // bedWarsApi.getArenaUtil().getArenas() -> pick WAITING
            Object arenaUtil = invoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null) {
                players.forEach(p -> p.sendMessage("§cBW1058: Arena util not available."));
                return;
            }
            List<?> arenas = (List<?>) invoke(arenaUtil, "getArenas");
            if (arenas == null || arenas.isEmpty()) {
                players.forEach(p -> p.sendMessage("§cNo arenas available."));
                return;
            }

            Object chosenArena = null;
            for (Object a : arenas) {
                String status = String.valueOf(invoke(a, "getStatus"));
                if ("WAITING".equalsIgnoreCase(status) || "RESTARTING".equalsIgnoreCase(status)) {
                    chosenArena = a;
                    break;
                }
            }
            if (chosenArena == null) {
                players.forEach(p -> p.sendMessage("§cNo joinable arena found."));
                return;
            }

            // Join players and alternate teams
            boolean toggle = true; // RED first, then GREEN
            for (Player p : players) {
                // try: arena.addPlayer(p)
                try {
                    invoke(chosenArena, "addPlayer", p);
                } catch (Throwable ignored) {
                    // try alternative utility: bedWarsApi.getArenaUtil().sendPlayerToArena(p, chosenArena)
                    try { invoke(arenaUtil, "sendPlayerToArena", p, chosenArena); } catch (Throwable ignored2) {}
                }

                String teamName = toggle ? allowedTeams.get(0) : allowedTeams.get(1);
                toggle = !toggle;
                try {
                    // Find team by color name and set player's team
                    Object team = findTeamByName(chosenArena, teamName);
                    if (team != null) {
                        // team.addMember(p) or arena.assignTeam(p, team) depending on API
                        if (!tryInvoke(team, "addMember", p)) {
                            tryInvoke(chosenArena, "assignTeam", p, team);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            players.forEach(p -> p.sendMessage("§aSent to arena with RED/GREEN assignment (best-effort)."));
        } catch (Throwable t) {
            players.forEach(p -> p.sendMessage("§cFailed to start private match: " + t.getClass().getSimpleName()));
            plugin.getLogger().warning("BW1058 hook error: " + t.getMessage());
        }
    }

    // List arena names that appear joinable (WAITING/RESTARTING)
    public java.util.List<String> listJoinableArenas() {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (bedWarsApi == null) return names;
        try {
            Object arenaUtil = invoke(bedWarsApi, "getArenaUtil");
            if (arenaUtil == null) return names;
            java.util.List<?> arenas = (java.util.List<?>) invoke(arenaUtil, "getArenas");
            if (arenas == null) return names;
            for (Object a : arenas) {
                String status = String.valueOf(invoke(a, "getStatus"));
                if ("WAITING".equalsIgnoreCase(status) || "RESTARTING".equalsIgnoreCase(status)) {
                    Object name = null;
                    try { name = invoke(a, "getArenaName"); } catch (Throwable ignored) {}
                    if (name == null) try { name = invoke(a, "getName"); } catch (Throwable ignored2) {}
                    if (name != null) names.add(String.valueOf(name));
                }
            }
        } catch (Throwable ignored) {}
        return names;
    }

    // Start match in a specific arena and assign teams according to assignments map
    public boolean startPrivateMatchWithAssignments(String arenaName, java.util.List<Player> players, java.util.Map<java.util.UUID, String> assignments) {
        if (bedWarsApi == null) return false;
        try {
            Object arenaUtil = invoke(bedWarsApi, "getArenaUtil");
            java.util.List<?> arenas = (java.util.List<?>) invoke(arenaUtil, "getArenas");
            Object chosenArena = null;
            for (Object a : arenas) {
                Object nm = null;
                try { nm = invoke(a, "getArenaName"); } catch (Throwable ignored) {}
                if (nm == null) try { nm = invoke(a, "getName"); } catch (Throwable ignored2) {}
                if (nm != null && arenaName.equalsIgnoreCase(String.valueOf(nm))) { chosenArena = a; break; }
            }
            if (chosenArena == null) {
                players.forEach(p -> p.sendMessage("§cArena not found: §e" + arenaName));
                return false;
            }

            for (Player p : players) {
                try {
                    invoke(chosenArena, "addPlayer", p);
                } catch (Throwable ignored) {
                    try { invoke(arenaUtil, "sendPlayerToArena", p, chosenArena); } catch (Throwable ignored2) {}
                }
                String teamName = assignments.getOrDefault(p.getUniqueId(), null);
                if (teamName != null) {
                    Object team = findTeamByName(chosenArena, teamName);
                    if (team != null) {
                        if (!tryInvoke(team, "addMember", p)) {
                            tryInvoke(chosenArena, "assignTeam", p, team);
                        }
                    }
                }
            }
            players.forEach(p -> p.sendMessage("§aJoined arena §e" + arenaName + "§a with assigned teams."));
            return true;
        } catch (Throwable t) {
            players.forEach(p -> p.sendMessage("§cFailed to start in arena: " + t.getClass().getSimpleName()));
            plugin.getLogger().warning("BW1058 startWithAssignments error: " + t.getMessage());
            return false;
        }
    }

    private Object findTeamByName(Object arena, String teamName) {
        try {
            List<?> teams = (List<?>) invoke(arena, "getTeams");
            if (teams == null) return null;
            for (Object t : teams) {
                Object color = invoke(t, "getColor");
                String name = color != null ? String.valueOf(color) : String.valueOf(invoke(t, "getName"));
                if (teamName.equalsIgnoreCase(name)) return t;
            }
        } catch (Throwable ignored) {}
        return null;
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
            // simple lenient matching
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
