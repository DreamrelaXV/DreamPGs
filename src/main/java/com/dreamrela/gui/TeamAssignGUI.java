package com.dreamrela.gui;

import com.dreamrela.DreamPGs;
import com.dreamrela.privategame.PrivateGame;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamAssignGUI {

    public static String title(UUID host) {
        return "PG: Assign Teams";
    }

    public static Inventory build(DreamPGs plugin, PrivateGame pg) {
        List<Player> members = new ArrayList<>();
        for (UUID u : pg.getMembers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) members.add(p);
        }
        int size = ((members.size() / 9) + 1) * 9;
        if (size < 27) size = 27;
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size, title(pg.getHost()));

        // Add member skulls with current assignment
        int slot = 0;
        for (Player p : members) {
            inv.setItem(slot++, skullFor(p, pg));
        }

        // Controls: Select Map (compass), Confirm Start (emerald), Close (barrier)
        inv.setItem(size - 9, named(new ItemStack(Material.COMPASS), "§bSelect Map"));
        inv.setItem(size - 5, named(new ItemStack(Material.EMERALD), "§aConfirm & Start"));
        inv.setItem(size - 1, named(new ItemStack(Material.BARRIER), "§cClose"));

        return inv;
    }

    private static ItemStack skullFor(Player p, PrivateGame pg) {
        ItemStack it;
        try {
            // 1.13+
            it = new ItemStack(Material.valueOf("PLAYER_HEAD"));
        } catch (Throwable t) {
            // pre-1.13
            it = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        }
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        sm.setOwner(p.getName());
        String team = pg.getAssignments().get(p.getUniqueId());
        sm.setDisplayName((team == null ? "§7" : ("RED".equalsIgnoreCase(team) ? "§c" : "§a")) + p.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Click: Toggle RED/GREEN");
        lore.add("§7Shift-Click: Unassign");
        lore.add("§7Current: " + (team == null ? "§7UNASSIGNED" : ("RED".equalsIgnoreCase(team) ? "§cRED" : "§aGREEN")));
        sm.setLore(lore);
        it.setItemMeta(sm);
        return it;
    }

    private static ItemStack named(ItemStack base, String name) {
        ItemMeta im = base.getItemMeta();
        im.setDisplayName(name);
        base.setItemMeta(im);
        return base;
    }
}
