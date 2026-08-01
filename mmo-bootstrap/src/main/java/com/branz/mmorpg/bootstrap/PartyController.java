package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.PartyId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.social.party.PartyEngine;
import com.branz.mmorpg.social.party.PartyErrorCode;
import com.branz.mmorpg.social.party.PartyMember;
import com.branz.mmorpg.social.party.PartyMemberStatus;
import com.branz.mmorpg.social.party.PartyRuntime;
import com.branz.mmorpg.social.party.PartyTransition;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread live party commands, reconnect grace and boss participant bridge. */
final class PartyController implements Listener {
    private final JavaPlugin plugin;
    private final CharacterSessionController characterSessions;
    private final BossEncounterController bossEncounters;
    private final PartyEngine engine = new PartyEngine();
    private final Map<PartyId, PartyRuntime> parties = new HashMap<>();
    private final Map<CharacterId, PartyId> partyByMember = new HashMap<>();
    private int tickTaskId = -1;
    private boolean checkpointRecoveryApplied;

    PartyController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            BossEncounterController bossEncounters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.bossEncounters = Objects.requireNonNull(bossEncounters, "bossEncounters");
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::advance, 20L, 20L);
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        checkpointRecoveryApplied = false;
        partyByMember.clear();
        parties.clear();
    }

    void onCharacterReady(Player player) {
        PartyRuntime runtime = runtimeFor(player);
        if (runtime == null) {
            return;
        }
        PartyMember member = runtime.members().get(characterId(player));
        if (member == null || member.status() == PartyMemberStatus.ONLINE) {
            return;
        }
        apply(
                runtime,
                engine.reconnect(runtime, characterId(player), UUID.randomUUID(), currentTick()),
                player.getName() + " reconnected",
                player);
    }

    void handleCommand(Player actor, String[] args) {
        if (!characterSessions.ready(actor)) {
            actor.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            usage(actor);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> create(actor);
            case "status" -> status(actor);
            case "invite" -> invite(actor, args);
            case "accept" -> accept(actor, args);
            case "decline" -> decline(actor, args);
            case "leader" -> transfer(actor, args);
            case "kick" -> kick(actor, args);
            case "leave" -> leave(actor);
            case "ready" -> ready(actor, args);
            default -> usage(actor);
        }
    }

    List<Player> onlineMembers(Player actor) {
        PartyRuntime runtime = runtimeFor(actor);
        if (runtime == null) {
            return List.of(actor);
        }
        return runtime.members().values().stream()
                .filter(member -> member.status() == PartyMemberStatus.ONLINE)
                .map(member -> plugin.getServer().getPlayer(member.characterId().value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .filter(characterSessions::ready)
                .sorted(Comparator.comparing(Player::getUniqueId))
                .toList();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PartyRuntime runtime = runtimeFor(event.getPlayer());
        if (runtime == null) {
            return;
        }
        apply(
                runtime,
                engine.disconnect(
                        runtime, characterId(event.getPlayer()), UUID.randomUUID(), currentTick()),
                event.getPlayer().getName() + " entered party reconnect grace");
    }

    private void create(Player actor) {
        if (runtimeFor(actor) != null) {
            actor.sendMessage(Component.text("You are already in a party.", NamedTextColor.RED));
            return;
        }
        PartyId partyId = new PartyId(UUID.randomUUID());
        PartyRuntime runtime = engine.start(partyId, characterId(actor));
        install(runtime);
        actor.sendMessage(
                Component.text("Party created: " + partyId.value(), NamedTextColor.GREEN));
    }

    private void invite(Player actor, String[] args) {
        if (args.length < 3) {
            usage(actor);
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline() || !characterSessions.ready(target)) {
            actor.sendMessage(
                    Component.text("Invite target is not ready/online.", NamedTextColor.RED));
            return;
        }
        if (runtimeFor(target) != null) {
            actor.sendMessage(Component.text("Target is already in a party.", NamedTextColor.RED));
            return;
        }
        PartyRuntime runtime = runtimeFor(actor);
        if (runtime == null) {
            create(actor);
            runtime = runtimeFor(actor);
        }
        PartyRuntime source = runtime;
        apply(
                source,
                engine.invite(
                        source,
                        characterId(actor),
                        characterId(target),
                        UUID.randomUUID(),
                        currentTick()),
                actor.getName() + " invited " + target.getName(),
                actor);
        if (parties.get(source.partyId()).invitations().containsKey(characterId(target))) {
            target.sendMessage(
                    Component.text(
                            actor.getName()
                                    + " invited you. Use /mmo party accept "
                                    + source.partyId().value(),
                            NamedTextColor.AQUA));
        }
    }

    private void accept(Player actor, String[] args) {
        if (runtimeFor(actor) != null) {
            actor.sendMessage(
                    Component.text("Leave your current party first.", NamedTextColor.RED));
            return;
        }
        PartyRuntime runtime = invitedParty(actor, args);
        if (runtime == null) {
            return;
        }
        apply(
                runtime,
                engine.accept(runtime, characterId(actor), UUID.randomUUID(), currentTick()),
                actor.getName() + " joined the party",
                actor);
    }

    private void decline(Player actor, String[] args) {
        PartyRuntime runtime = invitedParty(actor, args);
        if (runtime == null) {
            return;
        }
        apply(
                runtime,
                engine.decline(runtime, characterId(actor), UUID.randomUUID(), currentTick()),
                actor.getName() + " declined the party invitation",
                actor);
    }

    private PartyRuntime invitedParty(Player actor, String[] args) {
        if (args.length < 3) {
            actor.sendMessage(Component.text("Party UUID is required.", NamedTextColor.RED));
            return null;
        }
        UUID value;
        try {
            value = UUID.fromString(args[2]);
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text("Party ID must be a UUID.", NamedTextColor.RED));
            return null;
        }
        PartyRuntime runtime = parties.get(new PartyId(value));
        if (runtime == null || !runtime.invitations().containsKey(characterId(actor))) {
            actor.sendMessage(Component.text("No matching party invitation.", NamedTextColor.RED));
            return null;
        }
        return runtime;
    }

    private void transfer(Player actor, String[] args) {
        Player target = requireOnlineTarget(actor, args);
        PartyRuntime runtime = runtimeFor(actor);
        if (target == null || runtime == null) {
            return;
        }
        apply(
                runtime,
                engine.transferLeader(
                        runtime, characterId(actor), characterId(target), UUID.randomUUID()),
                target.getName() + " is now party leader",
                actor);
    }

    private void kick(Player actor, String[] args) {
        Player target = requireOnlineTarget(actor, args);
        PartyRuntime runtime = runtimeFor(actor);
        if (target == null || runtime == null) {
            return;
        }
        apply(
                runtime,
                engine.kick(runtime, characterId(actor), characterId(target), UUID.randomUUID()),
                target.getName() + " was removed from the party",
                actor);
    }

    private void leave(Player actor) {
        PartyRuntime runtime = runtimeFor(actor);
        if (runtime == null) {
            actor.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }
        apply(
                runtime,
                engine.leave(runtime, characterId(actor), UUID.randomUUID()),
                actor.getName() + " left the party",
                actor);
    }

    private void ready(Player actor, String[] args) {
        PartyRuntime runtime = runtimeFor(actor);
        if (runtime == null) {
            actor.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }
        if (args.length == 2 || "start".equalsIgnoreCase(args[2])) {
            apply(
                    runtime,
                    engine.startReadyCheck(
                            runtime,
                            characterId(actor),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            currentTick()),
                    actor.getName() + " started a ready check",
                    actor);
            return;
        }
        boolean response;
        if ("yes".equalsIgnoreCase(args[2])) {
            response = true;
        } else if ("no".equalsIgnoreCase(args[2])) {
            response = false;
        } else {
            usage(actor);
            return;
        }
        apply(
                runtime,
                engine.respondReady(
                        runtime, characterId(actor), response, UUID.randomUUID(), currentTick()),
                actor.getName() + " responded " + (response ? "READY" : "NOT READY"),
                actor);
    }

    private void status(Player actor) {
        PartyRuntime runtime = runtimeFor(actor);
        if (runtime == null) {
            actor.sendMessage(Component.text("You are not in a party.", NamedTextColor.YELLOW));
            return;
        }
        actor.sendMessage(
                Component.text(
                        "Party "
                                + runtime.partyId().value()
                                + " | leader="
                                + name(runtime.leaderId().orElseThrow())
                                + " | members="
                                + runtime.members().size()
                                + "/5",
                        NamedTextColor.AQUA));
        runtime.members().values().stream()
                .sorted(Comparator.comparingLong(PartyMember::joinedOrder))
                .forEach(
                        member ->
                                actor.sendMessage(
                                        "- " + name(member.characterId()) + " " + member.status()));
        runtime.readyCheck()
                .ifPresent(
                        check ->
                                actor.sendMessage(
                                        "Ready check "
                                                + check.checkId()
                                                + " responses="
                                                + check.responses().size()
                                                + "/"
                                                + runtime.members().size()));
    }

    private void advance() {
        recoverCheckpointParties();
        long tick = currentTick();
        for (PartyRuntime runtime : List.copyOf(parties.values())) {
            apply(
                    runtime,
                    engine.advance(runtime, UUID.randomUUID(), tick),
                    "Party timers advanced");
        }
    }

    private void recoverCheckpointParties() {
        if (checkpointRecoveryApplied || !bossEncounters.recoveryReady()) {
            return;
        }
        checkpointRecoveryApplied = true;
        for (PartyEncounterContext context : bossEncounters.activePartyEncounters()) {
            if (context.participants().stream().anyMatch(partyByMember::containsKey)) {
                continue;
            }
            List<CharacterId> participants =
                    context.participants().stream()
                            .sorted(Comparator.comparing(CharacterId::value))
                            .toList();
            PartyId partyId =
                    new PartyId(
                            UUID.nameUUIDFromBytes(
                                    ("checkpoint-party:" + context.encounterId().value())
                                            .getBytes(StandardCharsets.UTF_8)));
            PartyRuntime runtime = engine.start(partyId, participants.getFirst());
            long tick = currentTick();
            for (CharacterId participant : participants.subList(1, participants.size())) {
                runtime =
                        success(
                                        engine.invite(
                                                runtime,
                                                runtime.leaderId().orElseThrow(),
                                                participant,
                                                UUID.randomUUID(),
                                                tick))
                                .runtime();
                runtime =
                        success(engine.accept(runtime, participant, UUID.randomUUID(), tick))
                                .runtime();
            }
            for (CharacterId participant : participants) {
                Player player = plugin.getServer().getPlayer(participant.value());
                if (player == null || !player.isOnline() || !characterSessions.ready(player)) {
                    runtime =
                            success(
                                            engine.disconnect(
                                                    runtime, participant, UUID.randomUUID(), tick))
                                    .runtime();
                }
            }
            install(runtime);
            broadcast(
                    runtime,
                    "Party membership recovered from active boss checkpoint.",
                    NamedTextColor.GREEN);
        }
    }

    private void apply(
            PartyRuntime expected,
            Result<PartyTransition, PartyErrorCode> result,
            String description) {
        apply(expected, result, description, null);
    }

    private void apply(
            PartyRuntime expected,
            Result<PartyTransition, PartyErrorCode> result,
            String description,
            Player feedbackRecipient) {
        if (result instanceof Result.Failure<PartyTransition, PartyErrorCode> failure) {
            Optional.ofNullable(feedbackRecipient)
                    .or(() -> firstOnline(expected))
                    .ifPresent(
                            player ->
                                    player.sendMessage(
                                            Component.text(
                                                    failure.error().code()
                                                            + ": "
                                                            + failure.detail(),
                                                    NamedTextColor.RED)));
            return;
        }
        PartyTransition transition =
                ((Result.Success<PartyTransition, PartyErrorCode>) result).value();
        if (!transition.changed()) {
            return;
        }
        PartyRuntime replacement = transition.runtime();
        expected.members().keySet().forEach(partyByMember::remove);
        if (replacement.disbanded()) {
            parties.remove(expected.partyId());
        } else {
            install(replacement);
            broadcast(replacement, description, NamedTextColor.YELLOW);
        }
        transition.removed().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .forEach(
                        player ->
                                player.sendMessage(
                                        Component.text(description, NamedTextColor.YELLOW)));
        transition
                .readyCheckResult()
                .ifPresent(
                        ready ->
                                broadcast(
                                        replacement,
                                        ready ? "Party is READY." : "Party is NOT READY.",
                                        ready ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void install(PartyRuntime runtime) {
        parties.put(runtime.partyId(), runtime);
        runtime.members().keySet().forEach(member -> partyByMember.put(member, runtime.partyId()));
    }

    private PartyRuntime runtimeFor(Player player) {
        PartyId partyId = partyByMember.get(characterId(player));
        return partyId == null ? null : parties.get(partyId);
    }

    private Player requireOnlineTarget(Player actor, String[] args) {
        if (args.length < 3) {
            usage(actor);
            return null;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            actor.sendMessage(Component.text("Target is not online.", NamedTextColor.RED));
            return null;
        }
        return target;
    }

    private void broadcast(PartyRuntime runtime, String message, NamedTextColor color) {
        runtime.members().keySet().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .forEach(player -> player.sendMessage(Component.text(message, color)));
    }

    private Optional<Player> firstOnline(PartyRuntime runtime) {
        return runtime.members().keySet().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .findFirst();
    }

    private String name(CharacterId characterId) {
        Player player = plugin.getServer().getPlayer(characterId.value());
        return player == null ? characterId.value().toString() : player.getName();
    }

    private long currentTick() {
        return plugin.getServer().getCurrentTick();
    }

    private static CharacterId characterId(Player player) {
        return new CharacterId(player.getUniqueId());
    }

    private static PartyTransition success(Result<PartyTransition, PartyErrorCode> result) {
        return ((Result.Success<PartyTransition, PartyErrorCode>) result).value();
    }

    private static void usage(Player player) {
        player.sendMessage(
                "Usage: /mmo party <create|status|invite <player>|accept <party-uuid>|decline <party-uuid>|leader <player>|kick <player>|leave|ready [start|yes|no]>");
    }
}
