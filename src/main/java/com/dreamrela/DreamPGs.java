package com.dreamrela;

import com.dreamrela.bw.BW1058Hook;
import com.dreamrela.commands.PGCommand;
import com.dreamrela.commands.PartyCommand;
import com.dreamrela.party.PartyManager;
import com.dreamrela.privategame.PrivateGameManager;
import com.dreamrela.storage.StorageManager;
import com.dreamrela.gui.GUIListener;
import com.dreamrela.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DreamPGs extends JavaPlugin {

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

        // Register GUI listener
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        getLogger().info("DreamPGs enabled.");
    }

    @Override
    public void onDisable() {
        if (privateGameManager != null) privateGameManager.shutdown();
        if (partyManager != null) partyManager.shutdown();
        getLogger().info("DreamPGs disabled.");
    }

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
