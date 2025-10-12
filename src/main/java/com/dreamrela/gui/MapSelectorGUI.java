package com.dreamrela.gui;

import com.dreamrela.DreamPGs;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MapSelectorGUI {

    public static String title() { return "PG: Select Map"; }

    public static Inventory build(DreamPGs plugin) {
        List<String> arenas = plugin.getBw1058Hook().listJoinableArenas();
        int size = ((arenas.size() / 9) + 1) * 9;
        if (size < 27) size = 27;
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size, title());
        int slot = 0;
        for (String name : arenas) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName("§e" + name);
            it.setItemMeta(im);
            inv.setItem(slot++, it);
        }
        inv.setItem(size - 1, named(new ItemStack(Material.BARRIER), "§cBack"));
        return inv;
    }

    private static ItemStack named(ItemStack base, String name) {
        ItemMeta im = base.getItemMeta();
        im.setDisplayName(name);
        base.setItemMeta(im);
        return base;
    }
}
