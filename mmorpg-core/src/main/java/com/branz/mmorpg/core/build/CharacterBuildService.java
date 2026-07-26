package com.branz.mmorpg.core.build;

import com.branz.mmorpg.api.build.BuildSnapshot;
import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.input.SkillSlot;
import com.branz.mmorpg.api.item.LoadoutService;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.character.CharacterClassProgressionService;
import com.branz.mmorpg.core.mastery.DefaultCombatMasteryService;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Derives a read-only active build exclusively from authoritative server state. */
public final class CharacterBuildService {
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final LoadoutService loadouts;
    private final CharacterClassProgressionService classProgression;
    private final DefaultCombatMasteryService mastery;
    private final GameClock clock;

    public CharacterBuildService(PlayerSessionService sessions, ContentService content,
                                 LoadoutService loadouts,
                                 CharacterClassProgressionService classProgression,
                                 DefaultCombatMasteryService mastery, GameClock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.loadouts = Objects.requireNonNull(loadouts, "loadouts");
        this.classProgression = Objects.requireNonNull(classProgression, "classProgression");
        this.mastery = Objects.requireNonNull(mastery, "mastery");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuildSnapshot snapshot(UUID playerId) {
        var session = sessions.requirePlayable(playerId);
        var contentSnapshot = content.snapshot();
        ContentId classId = session.profile().classId()
                .orElseThrow(() -> new IllegalStateException("permanent class must be selected"));
        CharacterClassDefinition characterClass = contentSnapshot.characterClasses().get(classId);
        WeaponDefinition weapon = loadouts.current(playerId)
                .orElseThrow(() -> new IllegalStateException("active loadout is unavailable"));
        if (characterClass == null || weapon.tags().stream()
                .noneMatch(characterClass.allowedWeaponTags()::contains)) {
            throw new IllegalStateException("active loadout violates permanent-class restrictions");
        }

        var unlocked = new HashSet<>(classProgression.unlockedSkills(playerId));
        unlocked.add(weapon.basicAttackSkillId());
        unlocked.addAll(weapon.activeSkillIds());
        Map<ContentId, MasterySnapshot> masteryProfile = mastery.profile(playerId);
        for (var node : contentSnapshot.masteryNodes().values()) {
            if (!node.masteryId().equals(weapon.familyMasteryId())
                    && !node.masteryId().equals(weapon.typeMasteryId())) continue;
            MasterySnapshot progress = masteryProfile.get(node.masteryId());
            if (progress != null && progress.rank(node.id()) > 0) {
                node.unlockedSkillId().ifPresent(unlocked::add);
            }
        }

        Map<SkillSlot, ContentId> bindings = new EnumMap<>(SkillSlot.class);
        bindings.put(SkillSlot.BASIC_ATTACK, weapon.basicAttackSkillId());
        if (!weapon.activeSkillIds().isEmpty()) {
            bindings.put(SkillSlot.WEAPON_SKILL_1, weapon.activeSkillIds().get(0));
        }
        if (weapon.activeSkillIds().size() > 1) {
            bindings.put(SkillSlot.WEAPON_SKILL_2, weapon.activeSkillIds().get(1));
        }
        bindings.put(SkillSlot.CLASS_SKILL_1, characterClass.classSkillIds().get(0));
        if (characterClass.classSkillIds().size() > 1) {
            bindings.put(SkillSlot.CLASS_SKILL_2, characterClass.classSkillIds().get(1));
        }
        bindings.put(SkillSlot.ULTIMATE, characterClass.ultimateSkillId());

        Map<ContentId, Integer> levels = new HashMap<>();
        levels.put(weapon.familyMasteryId(), masteryProfile.getOrDefault(
                weapon.familyMasteryId(), MasterySnapshot.untrained(
                        weapon.familyMasteryId(), clock.now())).level());
        levels.put(weapon.typeMasteryId(), masteryProfile.getOrDefault(
                weapon.typeMasteryId(), MasterySnapshot.untrained(
                        weapon.typeMasteryId(), clock.now())).level());
        Map<CharacterClassRole, Double> roles = new EnumMap<>(CharacterClassRole.class);
        characterClass.roles().forEach(role -> roles.put(role, 1.0));
        return new BuildSnapshot(playerId, loadouts.revision(playerId),
                contentSnapshot.revision(), classId, weapon.id(), weapon.familyMasteryId(),
                weapon.typeMasteryId(), characterClass.displayName() + " " + weapon.displayName(),
                bindings, unlocked, levels, roles, clock.now());
    }
}
