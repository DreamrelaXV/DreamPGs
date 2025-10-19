package com.dreamrela;

import com.dreamrela.bw.BW1058Hook;
import com.dreamrela.commands.PGCommand;
import com.dreamrela.commands.PartyCommand;
import com.dreamrela.party.PartyManager;
import com.dreamrela.privategame.PrivateGame;
import com.dreamrela.privategame.PrivateGameManager;
import com.dreamrela.storage.StorageManager;
import com.dreamrela.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class DreamPGs extends JavaPlugin implements Listener {

    private static DreamPGs instance;
    private PartyManager partyManager;
    private PrivateGameManager privateGameManager;
    private StorageManager storageManager;
    private BW1058Hook bw1058Hook;
    private TeamManager teamManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Hook BedWars1058 API via Bukkit services
        this.bw1058Hook = new BW1058Hook(this);
        if (bw1058Hook.isHooked()) {
            getLogger().info("Hooked into BedWars1058 API.");
        } else {
            getLogger().warning("BedWars1058 API not found. Private game start will be limited.");
        }

        this.teamManager = new TeamManager(getConfig());
        this.partyManager = new PartyManager(this);
        this.privateGameManager = new PrivateGameManager(this, bw1058Hook, teamManager);
        this.storageManager = new StorageManager(this);

        // Register commands
        PartyCommand partyCmd = new PartyCommand(this);
        getCommand("party").setExecutor(partyCmd);
        getCommand("party").setTabCompleter(partyCmd);

        PGCommand pgCmd = new PGCommand(this);
        getCommand("pg").setExecutor(pgCmd);
        getCommand("pg").setTabCompleter(pgCmd);

        // Register GUI listener - inline to avoid classloader issues
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("DreamPGs enabled.");
    }

    @Override
    public void onDisable() {
        if (privateGameManager != null)
            privateGameManager.shutdown();
        if (partyManager != null)
            partyManager.shutdown();
        getLogger().info("DreamPGs disabled.");
    }

    // ===== INLINE GUI LISTENER =====

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null)
            return;
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player))
            return;
        Player p = (Player) he;
        String title = e.getView().getTitle();

        if (title.equals(com.dreamrela.gui.TeamAssignGUI.title(p.getUniqueId()))) {
            e.setCancelled(true);
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR)
                return;
            PrivateGame pg = privateGameManager.getByHost(p.getUniqueId()).orElse(null);
            if (pg == null) {
                p.closeInventory();
                return;
            }

            // Controls
            if (it.getType() == Material.COMPASS) {
                p.openInventory(com.dreamrela.gui.MapSelectorGUI.build(this));
                return;
            } else if (it.getType() == Material.EMERALD) {
                // Confirm & Start
                p.closeInventory();
                privateGameManager.start(p.getUniqueId());
                return;
            } else if (it.getType() == Material.BARRIER) {
                p.closeInventory();
                return;
            }

            // Player skull toggling
            if ((it.getType() == Material.SKULL_ITEM || it.getType().name().equalsIgnoreCase("PLAYER_HEAD"))
                    && it.getItemMeta() instanceof SkullMeta) {
                SkullMeta sm = (SkullMeta) it.getItemMeta();
                String targetName = sm.getOwner();
                if (targetName == null)
                    return;
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null)
                    return;
                UUID tu = target.getUniqueId();
                String cur = pg.getAssignments().get(tu);
                if (e.isShiftClick()) {
                    pg.setTeam(tu, null);
                } else {
                    if (cur == null)
                        pg.setTeam(tu, "RED");
                    else if ("RED".equalsIgnoreCase(cur))
                        pg.setTeam(tu, "GREEN");
                    else
                        pg.setTeam(tu, "RED");
                }
                // refresh GUI
                p.openInventory(com.dreamrela.gui.TeamAssignGUI.build(this, pg));
            }
        } else if (title.equals(com.dreamrela.gui.MapSelectorGUI.title())) {
            e.setCancelled(true);
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR)
                return;
            if (it.getType() == Material.BARRIER) {
                // Back
                privateGameManager.getByHost(p.getUniqueId())
                        .ifPresent(pg -> p.openInventory(com.dreamrela.gui.TeamAssignGUI.build(this, pg)));
                return;
            }
            if (it.getType() == Material.PAPER) {
                ItemMeta im = it.getItemMeta();
                String name = im != null ? im.getDisplayName() : null;
                if (name != null && name.startsWith("§e"))
                    name = name.substring(2);
                PrivateGame pg = privateGameManager.getByHost(p.getUniqueId()).orElse(null);
                if (pg != null) {
                    pg.setArenaName(name);
                    p.sendMessage("§aSelected map: §e" + name);
                    p.openInventory(com.dreamrela.gui.TeamAssignGUI.build(this, pg));
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // No persistent state to handle on close for now
    }

    // ===== END INLINE GUI LISTENER =====

    public static DreamPGs get() {
        return instance;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public PrivateGameManager getPrivateGameManager() {
        return privateGameManager;
    }

    public BW1058Hook getBw1058Hook() {
        return bw1058Hook;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }
}