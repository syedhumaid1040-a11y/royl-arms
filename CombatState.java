package dev.royalarms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived in-memory combat state (buffs, armed abilities, hit counters). */
public final class CombatState {

    public final Map<UUID, Long> chainArmed = new HashMap<>();
    public final Map<UUID, Long> lifestealUntil = new HashMap<>();
    public final Map<UUID, Long> bloodUntil = new HashMap<>();
    public final Map<UUID, Long> cloakUntil = new HashMap<>();
    public final Map<UUID, Integer> bloodSteals = new HashMap<>();
    public final Map<UUID, long[]> blitz = new HashMap<>();
    public final Map<UUID, Integer> hitCounter = new HashMap<>();
    /** Players whose damage is currently ability damage (must not trigger melee passives). */
    public final Set<UUID> suppressed = new HashSet<>();

    public boolean active(Map<UUID, Long> map, UUID id) {
        Long until = map.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public int blitzStacks(UUID id) {
        long[] b = blitz.get(id);
        return (b == null || b[1] < System.currentTimeMillis()) ? 0 : (int) b[0];
    }

    public void addBlitz(UUID id) {
        int stacks = Math.min(3, blitzStacks(id) + 1);
        blitz.put(id, new long[]{stacks, System.currentTimeMillis() + 10_000});
    }

    public void clear(UUID id) {
        chainArmed.remove(id);
        lifestealUntil.remove(id);
        bloodUntil.remove(id);
        cloakUntil.remove(id);
        bloodSteals.remove(id);
        blitz.remove(id);
        hitCounter.remove(id);
        suppressed.remove(id);
    }
}
