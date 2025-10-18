package com.dreamrela.gui;

import com.dreamrela.DreamPGs;
import com.dreamrela.gui.MapSelectorGUI;
import com.dreamrela.gui.TeamAssignGUI;
import com.dreamrela.privategame.PrivateGame;
import com.dreamrela.privategame.PrivateGameManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;
import java.util.UUID;

public class GUIListener implements Listener {

    private final DreamPGs plugin;

    public GUIListener(DreamPGs plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) return;
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        Player p = (Player) he;
        String title = e.getView().getTitle();

        if (title.equals(TeamAssignGUI.title(p.getUniqueId()))) {
            e.setCancelled(true);
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            PrivateGameManager pm = plugin.getPrivateGameManager();
            PrivateGame pg = pm.getByHost(p.getUniqueId()).orElse(null);
            if (pg == null) { p.closeInventory(); return; }

            // Controls
            if (it.getType() == Material.COMPASS) {
                p.openInventory(MapSelectorGUI.build(plugin));
                return;
            } else if (it.getType() == Material.EMERALD) {
                // Confirm & Start
                p.closeInventory();
                pm.start(p.getUniqueId());
                return;
            } else if (it.getType() == Material.BARRIER) {
                p.closeInventory();
                return;
            }

            // Player skull toggling
            if ((it.getType() == Material.SKULL_ITEM || it.getType().name().equalsIgnoreCase("PLAYER_HEAD")) && it.getItemMeta() instanceof SkullMeta) {
                SkullMeta sm = (SkullMeta) it.getItemMeta();
                String targetName = sm.getOwner();
                if (targetName == null) return;
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) return;
                UUID tu = target.getUniqueId();
                String cur = pg.getAssignments().get(tu);
                if (e.isShiftClick()) {
                    pg.setTeam(tu, null);
                } else {
                    if (cur == null) pg.setTeam(tu, "RED");
                    else if ("RED".equalsIgnoreCase(cur)) pg.setTeam(tu, "GREEN");
                    else pg.setTeam(tu, "RED");
                }
                // refresh GUI
                p.openInventory(TeamAssignGUI.build(plugin, pg));
            }
        } else if (title.equals(MapSelectorGUI.title())) {
            e.setCancelled(true);
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            if (it.getType() == Material.BARRIER) {
                // Back
                plugin.getPrivateGameManager().getByHost(p.getUniqueId()).ifPresent(pg -> p.openInventory(TeamAssignGUI.build(plugin, pg)));
                return;
            }
            if (it.getType() == Material.PAPER) {
                ItemMeta im = it.getItemMeta();
                String name = im != null ? im.getDisplayName() : null;
                if (name != null && name.startsWith("§e")) name = name.substring(2);
                PrivateGame pg = plugin.getPrivateGameManager().getByHost(p.getUniqueId()).orElse(null);
                if (pg != null) {
                    pg.setArenaName(name);
                    p.sendMessage("§aSelected map: §e" + name);
                    p.openInventory(TeamAssignGUI.build(plugin, pg));
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // No persistent state to handle on close for now
    }
}
