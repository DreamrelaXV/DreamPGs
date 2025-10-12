package com.dreamrela.party;

import com.dreamrela.DreamPGs;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {
    private final DreamPGs plugin;

    // Maps any member UUID -> their party
    private final Map<UUID, Party> memberToParty = new ConcurrentHashMap<>();

    // Maps invitee -> inviter
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager(DreamPGs plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        memberToParty.clear();
        pendingInvites.clear();
    }

    public Optional<Party> getParty(UUID member) {
        return Optional.ofNullable(memberToParty.get(member));
    }

    public boolean isLeader(UUID uuid) {
        return getParty(uuid).map(p -> p.getLeader().equals(uuid)).orElse(false);
    }

    public Party createParty(UUID leader) {
        Party party = new Party(leader);
        memberToParty.put(leader, party);
        return party;
    }

    public void disband(Party party) {
        for (UUID m : new HashSet<>(party.getMembers())) {
            memberToParty.remove(m);
        }
    }

    public boolean invite(UUID inviter, UUID invitee) {
        if (inviter.equals(invitee)) return false;
        pendingInvites.put(invitee, inviter);
        return true;
    }

    public boolean hasInvite(UUID invitee) {
        return pendingInvites.containsKey(invitee);
    }

    public Optional<UUID> getInviter(UUID invitee) {
        return Optional.ofNullable(pendingInvites.get(invitee));
    }

    public boolean acceptInvite(UUID invitee) {
        UUID inviter = pendingInvites.remove(invitee);
        if (inviter == null) return false;

        Party party = getParty(inviter).orElseGet(() -> createParty(inviter));
        party.addMember(invitee);
        memberToParty.put(invitee, party);
        broadcast(party, "§a" + getName(invitee) + " joined the party.");
        return true;
    }

    public boolean leave(UUID uuid) {
        Optional<Party> opt = getParty(uuid);
        if (!opt.isPresent()) return false;
        Party p = opt.get();
        if (p.getLeader().equals(uuid)) {
            // Leader leaving: disband party
            broadcast(p, "§cParty disbanded by leader.");
            disband(p);
            return true;
        }
        boolean removed = p.removeMember(uuid);
        if (removed) {
            memberToParty.remove(uuid);
            broadcast(p, "§e" + getName(uuid) + " left the party.");
        }
        return removed;
    }

    public boolean promote(UUID leader, UUID target) {
        Optional<Party> opt = getParty(leader);
        if (!opt.isPresent()) return false;
        Party p = opt.get();
        if (!p.getLeader().equals(leader)) return false;
        if (!p.isMember(target)) return false;
        p.setLeader(target);
        broadcast(p, "§a" + getName(target) + " is now the party leader.");
        return true;
    }

    public Set<UUID> getMembers(UUID member) {
        return getParty(member).map(Party::getMembers).orElse(Collections.emptySet());
    }

    public void broadcast(Party party, String msg) {
        for (UUID m : party.getMembers()) {
            Player pl = Bukkit.getPlayer(m);
            if (pl != null) pl.sendMessage("§6[Party] §r" + msg);
        }
    }

    private String getName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op != null && op.getName() != null ? op.getName() : uuid.toString();
    }
}
