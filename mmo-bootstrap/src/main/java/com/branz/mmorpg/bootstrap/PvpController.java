package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.social.pvp.PvpAdmission;
import com.branz.mmorpg.social.pvp.PvpCombatProfile;
import com.branz.mmorpg.social.pvp.PvpErrorCode;
import com.branz.mmorpg.social.pvp.PvpMatchEngine;
import com.branz.mmorpg.social.pvp.PvpMatchPhase;
import com.branz.mmorpg.social.pvp.PvpMatchResult;
import com.branz.mmorpg.social.pvp.PvpMatchRuntime;
import com.branz.mmorpg.social.pvp.PvpParticipant;
import com.branz.mmorpg.social.pvp.PvpParticipantStatus;
import com.branz.mmorpg.social.pvp.PvpTransition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Environment-gated live duel and arena adapter over the pure PvP state machine. */
final class PvpController implements Listener, PvpCombatPolicy {
    private final JavaPlugin plugin;
    private final CharacterSessionController characterSessions;
    private final CombatSessionController combatSessions;
    private final BossEncounterController bossEncounters;
    private final PvpMatchEngine engine = new PvpMatchEngine();
    private final PvpCombatProfile profile = PvpCombatProfile.canonical();
    private final Map<EncounterId, PvpMatchRuntime> active = new HashMap<>();
    private final Map<CharacterId, EncounterId> matchByParticipant = new HashMap<>();
    private final Map<EncounterId, Location> anchors = new HashMap<>();
    private final double arenaRadiusSquared;
    private int tickTaskId = -1;

    PvpController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            CombatSessionController combatSessions,
            BossEncounterController bossEncounters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.combatSessions = Objects.requireNonNull(combatSessions, "combatSessions");
        this.bossEncounters = Objects.requireNonNull(bossEncounters, "bossEncounters");
        double radius =
                Math.max(4.0, plugin.getConfig().getDouble("pvp.local-arena-radius-blocks", 24.0));
        arenaRadiusSquared = radius * radius;
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::advanceAll, 1L, 1L);
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        active.values().forEach(this::resetOnlineParticipants);
        active.clear();
        matchByParticipant.clear();
        anchors.clear();
    }

    void handleCommand(Player actor, String[] args) {
        if (args.length < 2) {
            usage(actor);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "challenge" -> challenge(actor, args);
            case "accept" -> accept(actor);
            case "decline" -> decline(actor);
            case "status" -> status(actor);
            case "surrender" -> surrender(actor);
            case "cancel" -> cancel(actor);
            case "arena" -> arena(actor, args);
            default -> usage(actor);
        }
    }

    @Override
    public Optional<PvpCombatProfile> profile(Player attacker, Player defender) {
        CharacterId source = characterId(Objects.requireNonNull(attacker, "attacker"));
        CharacterId target = characterId(Objects.requireNonNull(defender, "defender"));
        EncounterId matchId = matchByParticipant.get(source);
        if (matchId == null || !matchId.equals(matchByParticipant.get(target))) {
            return Optional.empty();
        }
        PvpMatchRuntime runtime = active.get(matchId);
        return runtime != null && engine.hostileAllowed(runtime, source, target)
                ? Optional.of(runtime.profile())
                : Optional.empty();
    }

    @Override
    public Optional<PvpCombatProfile> activeProfile(Player player) {
        PvpMatchRuntime runtime = runtimeFor(characterId(Objects.requireNonNull(player, "player")));
        return runtime != null && runtime.phase() == PvpMatchPhase.ACTIVE
                ? Optional.of(runtime.profile())
                : Optional.empty();
    }

    boolean suppressesDeathPouch(Player player) {
        PvpMatchRuntime runtime = runtimeFor(characterId(Objects.requireNonNull(player, "player")));
        return runtime != null && !runtime.profile().deathPouchAllowed();
    }

    LethalDamageDisposition interceptLethal(Player player) {
        CharacterId loser = characterId(Objects.requireNonNull(player, "player"));
        PvpMatchRuntime runtime = runtimeFor(loser);
        if (runtime == null || runtime.phase() != PvpMatchPhase.ACTIVE) {
            return LethalDamageDisposition.DEATH;
        }
        Result<PvpTransition, PvpErrorCode> result =
                engine.defeat(runtime, loser, UUID.randomUUID());
        if (!result.isSuccess()) {
            return LethalDamageDisposition.DEATH;
        }
        apply(((Result.Success<PvpTransition, PvpErrorCode>) result).value());
        return LethalDamageDisposition.SAFE_DEFEAT;
    }

    void onCharacterReady(Player player) {
        CharacterId participant = characterId(player);
        PvpMatchRuntime runtime = runtimeFor(participant);
        if (runtime == null || runtime.phase() != PvpMatchPhase.ACTIVE) {
            return;
        }
        PvpParticipant state = runtime.participants().get(participant);
        if (state == null || state.status() != PvpParticipantStatus.DISCONNECTED_GRACE) {
            return;
        }
        applyResult(
                player,
                engine.reconnect(
                        runtime,
                        participant,
                        UUID.randomUUID(),
                        plugin.getServer().getCurrentTick()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        CharacterId participant = characterId(event.getPlayer());
        PvpMatchRuntime runtime = runtimeFor(participant);
        if (runtime == null) {
            return;
        }
        if (runtime.phase() == PvpMatchPhase.ACTIVE) {
            applyResult(
                    null,
                    engine.disconnect(
                            runtime,
                            participant,
                            UUID.randomUUID(),
                            plugin.getServer().getCurrentTick()));
        } else {
            applyResult(null, engine.cancel(runtime, runtime.initiatedBy(), UUID.randomUUID()));
        }
    }

    private void challenge(Player actor, String[] args) {
        if (args.length != 3) {
            actor.sendMessage(
                    Component.text("Usage: /mmo pvp challenge <player>", NamedTextColor.YELLOW));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            actor.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return;
        }
        EncounterId matchId = new EncounterId(UUID.randomUUID());
        Result<PvpTransition, PvpErrorCode> result =
                engine.challengeDuel(
                        matchId,
                        characterId(actor),
                        characterId(target),
                        admission(actor, actor.getLocation()),
                        admission(target, actor.getLocation()),
                        profile,
                        UUID.randomUUID(),
                        plugin.getServer().getCurrentTick());
        if (applyResult(actor, result)) {
            anchors.put(matchId, actor.getLocation().clone());
            actor.sendMessage(
                    Component.text(
                            "Duel challenge sent to " + target.getName() + ".",
                            NamedTextColor.GOLD));
            target.sendMessage(
                    Component.text(
                            actor.getName()
                                    + " challenged you. Use /mmo pvp accept or /mmo pvp decline.",
                            NamedTextColor.GOLD));
        }
    }

    private void accept(Player actor) {
        PvpMatchRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            noMatch(actor);
            return;
        }
        Location anchor = anchors.get(runtime.matchId());
        boolean admissionValid =
                runtime.participants().keySet().stream()
                        .map(this::online)
                        .allMatch(player -> player != null && admission(player, anchor).accepted());
        if (!admissionValid) {
            actor.sendMessage(
                    Component.text("Duel admission is no longer valid.", NamedTextColor.RED));
            applyResult(actor, engine.cancel(runtime, runtime.initiatedBy(), UUID.randomUUID()));
            return;
        }
        applyResult(
                actor,
                engine.accept(
                        runtime,
                        characterId(actor),
                        UUID.randomUUID(),
                        plugin.getServer().getCurrentTick()));
    }

    private void decline(Player actor) {
        PvpMatchRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            noMatch(actor);
            return;
        }
        applyResult(actor, engine.decline(runtime, characterId(actor), UUID.randomUUID()));
    }

    private void surrender(Player actor) {
        PvpMatchRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            noMatch(actor);
            return;
        }
        applyResult(actor, engine.surrender(runtime, characterId(actor), UUID.randomUUID()));
    }

    private void cancel(Player actor) {
        PvpMatchRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            noMatch(actor);
            return;
        }
        applyResult(actor, engine.cancel(runtime, characterId(actor), UUID.randomUUID()));
    }

    private void arena(Player actor, String[] args) {
        if (args.length != 4) {
            actor.sendMessage(
                    Component.text(
                            "Usage: /mmo pvp arena <team-a-csv> <team-b-csv>",
                            NamedTextColor.YELLOW));
            return;
        }
        List<Player> teamA = resolveTeam(args[2]);
        List<Player> teamB = resolveTeam(args[3]);
        if (teamA == null || teamB == null || !teamA.contains(actor)) {
            actor.sendMessage(
                    Component.text(
                            "Both teams must contain distinct online players; team A must include you.",
                            NamedTextColor.RED));
            return;
        }
        LinkedHashMap<CharacterId, Integer> teams = new LinkedHashMap<>();
        teamA.forEach(player -> teams.put(characterId(player), 0));
        teamB.forEach(player -> teams.putIfAbsent(characterId(player), 1));
        if (teams.size() != teamA.size() + teamB.size()) {
            actor.sendMessage(
                    Component.text("A player cannot be on both teams.", NamedTextColor.RED));
            return;
        }
        LinkedHashMap<CharacterId, PvpAdmission> admissions = new LinkedHashMap<>();
        Location anchor = actor.getLocation();
        teamA.forEach(player -> admissions.put(characterId(player), admission(player, anchor)));
        teamB.forEach(player -> admissions.put(characterId(player), admission(player, anchor)));
        EncounterId matchId = new EncounterId(UUID.randomUUID());
        Result<PvpTransition, PvpErrorCode> result =
                engine.startArena(
                        matchId,
                        characterId(actor),
                        teams,
                        admissions,
                        profile,
                        UUID.randomUUID(),
                        plugin.getServer().getCurrentTick());
        if (applyResult(actor, result)) {
            anchors.put(matchId, anchor.clone());
        }
    }

    private List<Player> resolveTeam(String csv) {
        ArrayList<Player> result = new ArrayList<>();
        for (String name : csv.split(",")) {
            Player player = plugin.getServer().getPlayerExact(name.trim());
            if (player == null || !player.isOnline() || result.contains(player)) {
                return null;
            }
            result.add(player);
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private void status(Player actor) {
        PvpMatchRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            noMatch(actor);
            return;
        }
        PvpParticipant participant = runtime.participants().get(characterId(actor));
        actor.sendMessage(
                Component.text(
                        "PvP "
                                + runtime.mode()
                                + " | "
                                + runtime.phase()
                                + " | team="
                                + participant.team()
                                + " | status="
                                + participant.status(),
                        NamedTextColor.AQUA));
        actor.sendMessage(
                Component.text(
                        "Profile damage="
                                + profile.damageMultiplier()
                                + " heal="
                                + profile.healingMultiplier()
                                + " guard="
                                + profile.guardPressureMultiplier()
                                + " cc="
                                + profile.ccDurationMultiplier(),
                        NamedTextColor.GRAY));
    }

    private PvpAdmission admission(Player player, Location anchor) {
        boolean safe =
                anchor != null
                        && player.getWorld().equals(anchor.getWorld())
                        && player.getLocation().distanceSquared(anchor) <= arenaRadiusSquared;
        return new PvpAdmission(
                characterSessions.ready(player),
                combatSessions.engaged(player) || bossEncounters.suppressesDeathPouch(player),
                safe,
                characterSessions.valueMutationInFlight(player)
                        || matchByParticipant.containsKey(characterId(player)));
    }

    private void advanceAll() {
        long tick = plugin.getServer().getCurrentTick();
        for (EncounterId matchId : List.copyOf(active.keySet())) {
            PvpMatchRuntime runtime = active.get(matchId);
            if (runtime == null) {
                continue;
            }
            if (runtime.phase() == PvpMatchPhase.COUNTDOWN) {
                Location anchor = anchors.get(matchId);
                boolean invalid =
                        runtime.participants().keySet().stream()
                                .map(this::online)
                                .anyMatch(player -> player == null || !inside(player, anchor));
                if (invalid) {
                    applyResult(
                            null, engine.cancel(runtime, runtime.initiatedBy(), UUID.randomUUID()));
                    continue;
                }
            }
            if (runtime.phase() == PvpMatchPhase.ACTIVE) {
                Location anchor = anchors.get(matchId);
                Optional<CharacterId> outside =
                        runtime.participants().values().stream()
                                .filter(value -> value.status() == PvpParticipantStatus.READY)
                                .map(PvpParticipant::characterId)
                                .filter(
                                        id -> {
                                            Player player = online(id);
                                            return player != null && !inside(player, anchor);
                                        })
                                .findFirst();
                if (outside.isPresent()) {
                    applyResult(
                            null,
                            engine.boundaryForfeit(
                                    runtime, outside.orElseThrow(), UUID.randomUUID()));
                    continue;
                }
            }
            applyResult(null, engine.advance(runtime, UUID.randomUUID(), tick));
        }
    }

    private boolean inside(Player player, Location anchor) {
        return anchor != null
                && player.getWorld().equals(anchor.getWorld())
                && player.getLocation().distanceSquared(anchor) <= arenaRadiusSquared;
    }

    private boolean applyResult(Player actor, Result<PvpTransition, PvpErrorCode> result) {
        if (result instanceof Result.Failure<PvpTransition, PvpErrorCode> failure) {
            if (actor != null) {
                actor.sendMessage(Component.text(failure.detail(), NamedTextColor.RED));
            }
            return false;
        }
        apply(((Result.Success<PvpTransition, PvpErrorCode>) result).value());
        return true;
    }

    private void apply(PvpTransition transition) {
        PvpMatchRuntime runtime = transition.runtime();
        active.put(runtime.matchId(), runtime);
        runtime.participants()
                .keySet()
                .forEach(id -> matchByParticipant.put(id, runtime.matchId()));
        if (transition.newlyActive()) {
            resetOnlineParticipants(runtime);
            broadcast(runtime, "FIGHT", NamedTextColor.RED);
        }
        transition
                .newlyDefeated()
                .forEach(
                        id -> {
                            Player player = online(id);
                            if (player != null) {
                                player.sendMessage(
                                        Component.text(
                                                "You were defeated safely.",
                                                NamedTextColor.YELLOW));
                            }
                        });
        if (transition.completion().isPresent()) {
            complete(runtime, transition.completion().orElseThrow());
        } else if (transition.changed() && runtime.phase() == PvpMatchPhase.COUNTDOWN) {
            broadcast(runtime, "PvP starts in 5 seconds.", NamedTextColor.GOLD);
        }
    }

    private void complete(PvpMatchRuntime runtime, PvpMatchResult result) {
        String winner =
                result.winningTeam().isPresent()
                        ? " winner=team " + result.winningTeam().getAsInt()
                        : "";
        broadcast(runtime, "PvP ended: " + result.reason() + winner, NamedTextColor.AQUA);
        resetOnlineParticipants(runtime);
        active.remove(runtime.matchId());
        anchors.remove(runtime.matchId());
        runtime.participants().keySet().forEach(matchByParticipant::remove);
    }

    private void resetOnlineParticipants(PvpMatchRuntime runtime) {
        runtime.participants().keySet().stream()
                .map(this::online)
                .filter(Objects::nonNull)
                .forEach(combatSessions::resetPvpParticipant);
    }

    private void broadcast(PvpMatchRuntime runtime, String message, NamedTextColor color) {
        runtime.participants().keySet().stream()
                .map(this::online)
                .filter(Objects::nonNull)
                .forEach(player -> player.sendMessage(Component.text(message, color)));
    }

    private PvpMatchRuntime runtimeFor(CharacterId participant) {
        EncounterId matchId = matchByParticipant.get(participant);
        return matchId == null ? null : active.get(matchId);
    }

    private Player online(CharacterId characterId) {
        Player player = plugin.getServer().getPlayer(characterId.value());
        return player != null && player.isOnline() ? player : null;
    }

    private static CharacterId characterId(Player player) {
        return new CharacterId(player.getUniqueId());
    }

    private static void noMatch(Player actor) {
        actor.sendMessage(Component.text("You are not in a PvP match.", NamedTextColor.YELLOW));
    }

    private static void usage(Player actor) {
        actor.sendMessage(
                Component.text(
                        "Usage: /mmo pvp <challenge|accept|decline|status|surrender|cancel|arena>",
                        NamedTextColor.YELLOW));
    }
}
