package com.dreamrela.party;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID newLeader) {
        if (members.contains(newLeader)) {
            this.leader = newLeader;
        }
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean addMember(UUID uuid) {
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public int size() {
        return members.size();
    }
}
