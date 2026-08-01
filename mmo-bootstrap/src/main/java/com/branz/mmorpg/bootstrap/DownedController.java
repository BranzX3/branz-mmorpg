package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.social.downed.DownedEncounterEngine;
import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import com.branz.mmorpg.social.downed.DownedErrorCode;
import com.branz.mmorpg.social.downed.DownedParticipant;
import com.branz.mmorpg.social.downed.DownedTransition;
import com.branz.mmorpg.social.downed.EncounterLifeState;
import com.branz.mmorpg.social.downed.ReviveChannel;
import java.util.HashMap;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread party-PvE downed/revive adapter for active boss lab attempts. */
final class DownedController implements Listener {
    private static final double MOVEMENT_EPSILON_SQUARED = 1.0e-6;

    private final JavaPlugin plugin;
    private final CombatSessionController combatSessions;
    private final BossEncounterController bossEncounters;
    private final DownedEncounterEngine engine = new DownedEncounterEngine();
    private final Map<AttemptKey, DownedEncounterRuntime> runtimes = new HashMap<>();
    private final Map<CharacterId, Location> reviveOrigins = new HashMap<>();
    private int tickTaskId = -1;

    DownedController(
            JavaPlugin plugin,
            CombatSessionController combatSessions,
            BossEncounterController bossEncounters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.combatSessions = Objects.requireNonNull(combatSessions, "combatSessions");
        this.bossEncounters = Objects.requireNonNull(bossEncounters, "bossEncounters");
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::advance, 1L, 1L);
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        reviveOrigins.clear();
        runtimes.clear();
    }

    boolean interceptLethal(Player player) {
        PartyEncounterContext context = bossEncounters.partyEncounter(player).orElse(null);
        if (context == null) {
            return false;
        }
        AttemptKey key = AttemptKey.from(context);
        DownedEncounterRuntime runtime = runtime(key, context);
        Result<DownedTransition, DownedErrorCode> result =
                engine.lethalDamage(
                        runtime, characterId(player), false, UUID.randomUUID(), currentTick());
        DownedTransition transition = transition(result, player);
        if (transition == null) {
            return false;
        }
        runtimes.put(key, transition.runtime());
        clearFinishedChannels(runtime, transition.runtime());
        if (transition.newlyDowned().contains(characterId(player))) {
            broadcast(
                    context,
                    player.getName() + " is DOWNED for 15s. An active ally has 4s to revive them.",
                    NamedTextColor.YELLOW);
            return true;
        }
        if (transition.newlyDead().contains(characterId(player))) {
            broadcast(
                    context,
                    player.getName() + " was defeated; no revive remains.",
                    NamedTextColor.RED);
        }
        return false;
    }

    boolean protectedFromDamage(Player player) {
        RuntimeView view = view(player).orElse(null);
        if (view == null) {
            return false;
        }
        DownedParticipant participant = view.runtime().participants().get(characterId(player));
        return participant != null && participant.protectedAt(currentTick());
    }

    void observeHostileAction(Player player, String reason) {
        interruptOwnedChannel(player, reason);
        RuntimeView view = view(player).orElse(null);
        if (view == null) {
            return;
        }
        Result<DownedTransition, DownedErrorCode> result =
                engine.hostileAction(
                        view.runtime(), characterId(player), UUID.randomUUID(), currentTick());
        DownedTransition transition = transition(result, player);
        if (transition != null && transition.changed()) {
            runtimes.put(view.key(), transition.runtime());
            player.sendActionBar(Component.text("REVIVE PROTECTION ENDED", NamedTextColor.YELLOW));
        }
    }

    void handleCommand(Player actor, String[] args) {
        if (args.length < 2) {
            usage(actor);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> showStatus(actor);
            case "down" -> forceDown(actor, args, false);
            case "execute" -> forceDown(actor, args, true);
            case "revive" -> beginRevive(actor, args);
            case "interrupt" -> interruptOwnedChannel(actor, "LAB_COMMAND");
            case "hostile" -> observeHostileAction(actor, "LAB_COMMAND");
            default -> usage(actor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            interruptOwnedChannel(player, "DAMAGE");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (combatSessions.isDowned(player) && positionChanged(event.getFrom(), event.getTo())) {
            Location locked = event.getFrom().clone();
            locked.setYaw(event.getTo().getYaw());
            locked.setPitch(event.getTo().getPitch());
            event.setTo(locked);
            return;
        }
        Location origin = reviveOrigins.get(characterId(player));
        if (origin != null && positionChanged(origin, event.getTo())) {
            interruptOwnedChannel(player, "MOVEMENT");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        interruptOwnedChannel(event.getPlayer(), "DISCONNECT");
    }

    private void forceDown(Player actor, String[] args, boolean execute) {
        Player target = target(actor, args);
        if (target == null) {
            return;
        }
        PartyEncounterContext context = bossEncounters.partyEncounter(target).orElse(null);
        if (context == null) {
            actor.sendMessage(
                    Component.text(
                            "Target is not in an active multi-player boss encounter.",
                            NamedTextColor.RED));
            return;
        }
        if (!execute) {
            if (!combatSessions.forceLethalDamage(target)) {
                actor.sendMessage(
                        Component.text("Target has no live combat session.", NamedTextColor.RED));
            }
            return;
        }
        AttemptKey key = AttemptKey.from(context);
        DownedEncounterRuntime runtime = runtime(key, context);
        Result<DownedTransition, DownedErrorCode> result =
                engine.lethalDamage(
                        runtime, characterId(target), true, UUID.randomUUID(), currentTick());
        DownedTransition transition = transition(result, actor);
        if (transition == null) {
            return;
        }
        runtimes.put(key, transition.runtime());
        clearFinishedChannels(runtime, transition.runtime());
        if (transition.newlyDead().contains(characterId(target))) {
            broadcast(
                    context,
                    target.getName() + " was EXECUTED and cannot be revived.",
                    NamedTextColor.RED);
            combatSessions.killPlayer(target);
        }
    }

    private void beginRevive(Player actor, String[] args) {
        if (args.length < 3) {
            usage(actor);
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            actor.sendMessage(Component.text("Revive target is not online.", NamedTextColor.RED));
            return;
        }
        PartyEncounterContext actorContext = bossEncounters.partyEncounter(actor).orElse(null);
        PartyEncounterContext targetContext = bossEncounters.partyEncounter(target).orElse(null);
        if (actorContext == null
                || targetContext == null
                || !AttemptKey.from(actorContext).equals(AttemptKey.from(targetContext))) {
            actor.sendMessage(
                    Component.text(
                            "Reviver and target must share one active party encounter.",
                            NamedTextColor.RED));
            return;
        }
        AttemptKey key = AttemptKey.from(actorContext);
        DownedEncounterRuntime runtime = runtime(key, actorContext);
        Result<DownedTransition, DownedErrorCode> result =
                engine.beginRevive(
                        runtime,
                        characterId(actor),
                        characterId(target),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        currentTick());
        DownedTransition transition = transition(result, actor);
        if (transition == null) {
            return;
        }
        runtimes.put(key, transition.runtime());
        reviveOrigins.put(characterId(actor), actor.getLocation().clone());
        broadcast(
                actorContext,
                actor.getName() + " is reviving " + target.getName() + " (4s).",
                NamedTextColor.AQUA);
    }

    private void interruptOwnedChannel(Player reviver, String reason) {
        CharacterId reviverId = characterId(reviver);
        for (Map.Entry<AttemptKey, DownedEncounterRuntime> entry : runtimes.entrySet()) {
            ReviveChannel channel =
                    entry.getValue().reviveChannelsByTarget().values().stream()
                            .filter(candidate -> candidate.reviverId().equals(reviverId))
                            .findFirst()
                            .orElse(null);
            if (channel == null) {
                continue;
            }
            Result<DownedTransition, DownedErrorCode> result =
                    engine.interruptRevive(entry.getValue(), channel.targetId(), UUID.randomUUID());
            DownedTransition transition = transition(result, reviver);
            if (transition != null) {
                entry.setValue(transition.runtime());
                reviveOrigins.remove(reviverId);
                reviver.sendMessage(
                        Component.text("Revive interrupted: " + reason, NamedTextColor.RED));
            }
            return;
        }
    }

    private void advance() {
        long tick = currentTick();
        for (Map.Entry<AttemptKey, DownedEncounterRuntime> entry : runtimes.entrySet()) {
            DownedEncounterRuntime before = entry.getValue();
            Result<DownedTransition, DownedErrorCode> result =
                    engine.advance(before, UUID.randomUUID(), tick);
            if (!(result instanceof Result.Success<DownedTransition, DownedErrorCode> success)
                    || !success.value().changed()) {
                continue;
            }
            DownedTransition transition = success.value();
            entry.setValue(transition.runtime());
            clearFinishedChannels(before, transition.runtime());
            transition.newlyDead().forEach(this::killOnline);
            transition.revivedHealthRatios().forEach(this::reviveOnline);
        }
        if (tick % 20 == 0) {
            showDownedCountdowns(tick);
        }
    }

    private void killOnline(CharacterId characterId) {
        Player player = plugin.getServer().getPlayer(characterId.value());
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text("Downed timer expired.", NamedTextColor.RED));
            combatSessions.killPlayer(player);
        }
    }

    private void reviveOnline(CharacterId characterId, double healthRatio) {
        Player player = plugin.getServer().getPlayer(characterId.value());
        if (player != null && player.isOnline()) {
            combatSessions.reviveFromDowned(player, healthRatio);
            player.sendMessage(
                    Component.text(
                            "REVIVED at 25% health with 3s protection.", NamedTextColor.GREEN));
        }
    }

    private void showDownedCountdowns(long tick) {
        runtimes.values()
                .forEach(
                        runtime ->
                                runtime.participants().values().stream()
                                        .filter(
                                                participant ->
                                                        participant.lifeState()
                                                                == EncounterLifeState.DOWNED)
                                        .forEach(
                                                participant -> {
                                                    Player player =
                                                            plugin.getServer()
                                                                    .getPlayer(
                                                                            participant
                                                                                    .characterId()
                                                                                    .value());
                                                    if (player != null && player.isOnline()) {
                                                        long seconds =
                                                                Math.max(
                                                                        0,
                                                                        (participant
                                                                                                .downedDeadlineTick()
                                                                                        - tick
                                                                                        + 19)
                                                                                / 20);
                                                        player.sendActionBar(
                                                                Component.text(
                                                                        "DOWNED " + seconds + "s",
                                                                        NamedTextColor.RED));
                                                    }
                                                }));
    }

    private void showStatus(Player actor) {
        RuntimeView view = view(actor).orElse(null);
        if (view == null) {
            actor.sendMessage(
                    Component.text("No active party downed runtime.", NamedTextColor.YELLOW));
            return;
        }
        DownedParticipant participant = view.runtime().participants().get(characterId(actor));
        actor.sendMessage(
                Component.text(
                        "Downed runtime | encounter="
                                + view.key().encounterId().value()
                                + " | attempt="
                                + view.key().attempt()
                                + " | state="
                                + participant.lifeState()
                                + " | reviveConsumed="
                                + participant.reviveConsumed()
                                + " | channels="
                                + view.runtime().reviveChannelsByTarget().size(),
                        NamedTextColor.AQUA));
    }

    private Optional<RuntimeView> view(Player player) {
        PartyEncounterContext context = bossEncounters.partyEncounter(player).orElse(null);
        if (context == null) {
            return Optional.empty();
        }
        AttemptKey key = AttemptKey.from(context);
        DownedEncounterRuntime runtime = runtimes.get(key);
        return runtime == null ? Optional.empty() : Optional.of(new RuntimeView(key, runtime));
    }

    private DownedEncounterRuntime runtime(AttemptKey key, PartyEncounterContext context) {
        runtimes.keySet()
                .removeIf(
                        candidate ->
                                candidate.encounterId().equals(key.encounterId())
                                        && candidate.attempt() != key.attempt());
        return runtimes.computeIfAbsent(
                key,
                ignored -> {
                    Result<DownedEncounterRuntime, DownedErrorCode> started =
                            engine.start(context.encounterId(), context.participants());
                    return ((Result.Success<DownedEncounterRuntime, DownedErrorCode>) started)
                            .value();
                });
    }

    private DownedTransition transition(
            Result<DownedTransition, DownedErrorCode> result, Player feedbackTarget) {
        if (result instanceof Result.Success<DownedTransition, DownedErrorCode> success) {
            return success.value();
        }
        Result.Failure<DownedTransition, DownedErrorCode> failure =
                (Result.Failure<DownedTransition, DownedErrorCode>) result;
        feedbackTarget.sendMessage(
                Component.text(
                        failure.error().code() + ": " + failure.detail(), NamedTextColor.RED));
        return null;
    }

    private void clearFinishedChannels(
            DownedEncounterRuntime before, DownedEncounterRuntime after) {
        before.reviveChannelsByTarget().values().stream()
                .filter(channel -> !after.reviveChannelsByTarget().containsKey(channel.targetId()))
                .forEach(channel -> reviveOrigins.remove(channel.reviverId()));
    }

    private void broadcast(PartyEncounterContext context, String message, NamedTextColor color) {
        context.participants().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .forEach(player -> player.sendMessage(Component.text(message, color)));
    }

    private Player target(Player actor, String[] args) {
        if (args.length < 3) {
            return actor;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            actor.sendMessage(Component.text("Target is not online.", NamedTextColor.RED));
            return null;
        }
        return target;
    }

    private long currentTick() {
        return plugin.getServer().getCurrentTick();
    }

    private static boolean positionChanged(Location from, Location to) {
        return from.getWorld() != to.getWorld()
                || from.toVector().distanceSquared(to.toVector()) > MOVEMENT_EPSILON_SQUARED;
    }

    private static CharacterId characterId(Player player) {
        return new CharacterId(player.getUniqueId());
    }

    private static void usage(Player player) {
        player.sendMessage(
                "Usage: /mmo downed <status|down [player]|execute [player]|revive <player>|interrupt|hostile>");
    }

    private record AttemptKey(EncounterId encounterId, int attempt) {
        private AttemptKey {
            Objects.requireNonNull(encounterId, "encounterId");
            if (attempt < 1) {
                throw new IllegalArgumentException("attempt must be positive");
            }
        }

        private static AttemptKey from(PartyEncounterContext context) {
            return new AttemptKey(context.encounterId(), context.attempt());
        }
    }

    private record RuntimeView(AttemptKey key, DownedEncounterRuntime runtime) {
        private RuntimeView {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(runtime, "runtime");
        }
    }
}
