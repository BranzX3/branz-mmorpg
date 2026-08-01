package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LfgListingId;
import com.branz.mmorpg.api.identity.PartyId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.social.lfg.LfgApplicantProfile;
import com.branz.mmorpg.social.lfg.LfgEngine;
import com.branz.mmorpg.social.lfg.LfgEntryRequirements;
import com.branz.mmorpg.social.lfg.LfgErrorCode;
import com.branz.mmorpg.social.lfg.LfgJoinPolicy;
import com.branz.mmorpg.social.lfg.LfgListingRuntime;
import com.branz.mmorpg.social.lfg.LfgRolePreference;
import com.branz.mmorpg.social.lfg.LfgSearchQuery;
import com.branz.mmorpg.social.lfg.LfgTransition;
import com.branz.mmorpg.social.party.PartyEngine;
import com.branz.mmorpg.social.party.PartyRuntime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread process-local LFG directory composed with live party authority. */
final class LfgController {
    private final JavaPlugin plugin;
    private final CharacterSessionController characterSessions;
    private final PartyController parties;
    private final LfgEngine engine = new LfgEngine();
    private final Map<LfgListingId, LfgListingRuntime> listings = new HashMap<>();
    private final Map<PartyId, LfgListingId> listingByParty = new HashMap<>();

    LfgController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            PartyController parties) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    void shutdown() {
        listingByParty.clear();
        listings.clear();
    }

    void handleCommand(Player actor, String[] args) {
        if (!characterSessions.ready(actor)) {
            actor.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        reconcileListings();
        if (args.length < 2) {
            usage(actor);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> publish(actor, args);
            case "browse" -> browse(actor, args);
            case "request" -> request(actor, args);
            case "requests" -> requests(actor);
            case "accept" -> decide(actor, args, true);
            case "decline" -> decide(actor, args, false);
            case "cancel" -> cancel(actor, args);
            case "status" -> status(actor);
            case "close" -> close(actor);
            default -> usage(actor);
        }
    }

    private void publish(Player actor, String[] args) {
        if (args.length < 7) {
            usage(actor);
            return;
        }
        DefinitionId activity = definition(actor, args[2], "Activity");
        DefinitionId region = definition(actor, args[3], "Region");
        LfgRolePreference role = role(actor, args[5]);
        if (activity == null || region == null || role == null) {
            return;
        }
        Set<String> requirements;
        LfgApplicantProfile profile;
        try {
            String requirementToken =
                    args.length >= 8 && !"auto".equalsIgnoreCase(args[7]) ? args[7] : "-";
            requirements = tags(requirementToken);
            profile = new LfgApplicantProfile(role, note(args[6]), Set.of());
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return;
        }
        PartyRuntime party = parties.ensureLeaderParty(actor);
        if (party == null) {
            return;
        }
        if (listingByParty.containsKey(party.partyId())) {
            actor.sendMessage(
                    Component.text("Your party already has an LFG listing.", NamedTextColor.RED));
            return;
        }
        int availableSlots = PartyEngine.MAX_MEMBERS - party.members().size();
        if (availableSlots == 0) {
            actor.sendMessage(Component.text("Your party is already full.", NamedTextColor.RED));
            return;
        }
        LfgJoinPolicy policy =
                (args.length > 7 && "auto".equalsIgnoreCase(args[7]))
                                || (args.length > 8 && "auto".equalsIgnoreCase(args[8]))
                        ? LfgJoinPolicy.AUTOMATIC
                        : LfgJoinPolicy.LEADER_APPROVAL;
        LfgListingRuntime listing;
        try {
            listing =
                    engine.start(
                            new LfgListingId(UUID.randomUUID()),
                            party.partyId(),
                            characterId(actor),
                            activity,
                            region,
                            args[4],
                            profile,
                            new LfgEntryRequirements(requirements),
                            policy,
                            availableSlots);
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return;
        }
        install(listing);
        actor.sendMessage(
                Component.text(
                        "LFG listed: " + listing.listingId().value() + " | " + policy,
                        NamedTextColor.GREEN));
    }

    private void browse(Player actor, String[] args) {
        DefinitionId activity = optionalDefinition(actor, args, 2, "Activity");
        DefinitionId region = optionalDefinition(actor, args, 3, "Region");
        if (invalidOptional(args, 2, activity) || invalidOptional(args, 3, region)) {
            return;
        }
        String language = args.length > 4 && !"*".equals(args[4]) ? args[4] : null;
        LfgRolePreference role = null;
        if (args.length > 5 && !"*".equals(args[5])) {
            role = role(actor, args[5]);
            if (role == null) {
                return;
            }
        }
        Set<String> eligibility;
        try {
            eligibility = args.length > 6 ? tags(args[6]) : Set.of();
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return;
        }
        LfgSearchQuery query =
                new LfgSearchQuery(
                        Optional.ofNullable(activity),
                        Optional.ofNullable(region),
                        language == null ? Set.of() : Set.of(language),
                        role == null ? Set.of() : Set.of(role),
                        new LfgApplicantProfile(LfgRolePreference.FLEXIBLE, "", eligibility));
        List<LfgListingRuntime> matches =
                listings.values().stream()
                        .filter(listing -> leaderOnline(listing).isPresent())
                        .filter(listing -> engine.matches(listing, query))
                        .sorted(Comparator.comparing(listing -> listing.listingId().value()))
                        .toList();
        actor.sendMessage(Component.text("LFG matches: " + matches.size(), NamedTextColor.AQUA));
        matches.forEach(
                listing ->
                        actor.sendMessage(
                                listing.listingId().value()
                                        + " | "
                                        + listing.activityId()
                                        + " | "
                                        + listing.regionId()
                                        + " | "
                                        + listing.language()
                                        + " | leader="
                                        + name(listing.leaderId())
                                        + " | slots="
                                        + listing.remainingSlots()
                                        + " | role="
                                        + listing.leaderProfile().rolePreference()
                                        + " | note="
                                        + listing.leaderProfile().experienceNote()));
    }

    private void request(Player actor, String[] args) {
        if (args.length < 6) {
            usage(actor);
            return;
        }
        if (parties.partyFor(actor) != null) {
            actor.sendMessage(
                    Component.text(
                            "Leave your current party before applying.", NamedTextColor.RED));
            return;
        }
        LfgListingRuntime listing = listing(actor, args[2]);
        LfgRolePreference role = role(actor, args[3]);
        if (listing == null || role == null) {
            return;
        }
        Player leader = leaderOnline(listing).orElse(null);
        if (leader == null) {
            actor.sendMessage(Component.text("Listing leader is not ready.", NamedTextColor.RED));
            return;
        }
        LfgApplicantProfile profile;
        try {
            profile = new LfgApplicantProfile(role, note(args[4]), tags(args[5]));
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return;
        }
        Result<LfgTransition, LfgErrorCode> result =
                engine.requestJoin(
                        listing, characterId(actor), profile, UUID.randomUUID(), currentTick());
        LfgTransition transition = success(actor, result);
        if (transition == null) {
            return;
        }
        if (transition.acceptedApplicant().isPresent() && !parties.admitFromLfg(leader, actor)) {
            return;
        }
        install(transition.runtime());
        if (transition.acceptedApplicant().isPresent()) {
            actor.sendMessage(
                    Component.text("Joined the party through LFG.", NamedTextColor.GREEN));
        } else {
            actor.sendMessage(Component.text("LFG request sent.", NamedTextColor.GREEN));
            leader.sendMessage(
                    Component.text(
                            actor.getName() + " requested to join your LFG listing.",
                            NamedTextColor.AQUA));
        }
    }

    private void requests(Player actor) {
        LfgListingRuntime listing = leaderListing(actor);
        if (listing == null) {
            return;
        }
        actor.sendMessage(
                Component.text(
                        "Pending LFG requests: " + listing.pendingRequests().size(),
                        NamedTextColor.AQUA));
        listing.pendingRequests().values().stream()
                .sorted(Comparator.comparing(request -> request.applicantId().value()))
                .forEach(
                        request ->
                                actor.sendMessage(
                                        name(request.applicantId())
                                                + " | role="
                                                + request.profile().rolePreference()
                                                + " | note="
                                                + request.profile().experienceNote()));
    }

    private void decide(Player actor, String[] args, boolean accept) {
        if (args.length < 3) {
            usage(actor);
            return;
        }
        LfgListingRuntime listing = leaderListing(actor);
        if (listing == null) {
            return;
        }
        CharacterId applicantId = applicantId(listing, args[2]);
        if (applicantId == null) {
            actor.sendMessage(Component.text("LFG request was not found.", NamedTextColor.RED));
            return;
        }
        Player applicant = plugin.getServer().getPlayer(applicantId.value());
        if (accept && (applicant == null || !characterSessions.ready(applicant))) {
            actor.sendMessage(Component.text("Applicant is not ready/online.", NamedTextColor.RED));
            return;
        }
        Result<LfgTransition, LfgErrorCode> result =
                engine.decideRequest(
                        listing, characterId(actor), applicantId, accept, UUID.randomUUID());
        LfgTransition transition = success(actor, result);
        if (transition == null) {
            return;
        }
        if (accept && !parties.admitFromLfg(actor, Objects.requireNonNull(applicant))) {
            return;
        }
        install(transition.runtime());
        if (applicant != null) {
            applicant.sendMessage(
                    Component.text(
                            accept
                                    ? "Your LFG request was accepted."
                                    : "Your LFG request was declined.",
                            accept ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        }
    }

    private void cancel(Player actor, String[] args) {
        if (args.length < 3) {
            usage(actor);
            return;
        }
        LfgListingRuntime listing = listing(actor, args[2]);
        if (listing == null) {
            return;
        }
        LfgTransition transition =
                success(
                        actor,
                        engine.cancelRequest(listing, characterId(actor), UUID.randomUUID()));
        if (transition != null) {
            install(transition.runtime());
            actor.sendMessage(Component.text("LFG request cancelled.", NamedTextColor.YELLOW));
        }
    }

    private void status(Player actor) {
        PartyRuntime party = parties.partyFor(actor);
        LfgListingRuntime own =
                party == null ? null : listings.get(listingByParty.get(party.partyId()));
        if (own != null && own.leaderId().equals(characterId(actor))) {
            actor.sendMessage(
                    Component.text(
                            "LFG "
                                    + own.listingId().value()
                                    + " | "
                                    + own.joinPolicy()
                                    + " | slots="
                                    + own.remainingSlots()
                                    + " | pending="
                                    + own.pendingRequests().size(),
                            NamedTextColor.AQUA));
            return;
        }
        List<LfgListingRuntime> pending =
                listings.values().stream()
                        .filter(
                                listing ->
                                        listing.pendingRequests().containsKey(characterId(actor)))
                        .sorted(Comparator.comparing(listing -> listing.listingId().value()))
                        .toList();
        actor.sendMessage(
                Component.text("Pending LFG applications: " + pending.size(), NamedTextColor.AQUA));
        pending.forEach(listing -> actor.sendMessage(listing.listingId().value().toString()));
    }

    private void close(Player actor) {
        LfgListingRuntime listing = leaderListing(actor);
        if (listing == null) {
            return;
        }
        LfgTransition transition =
                success(actor, engine.close(listing, characterId(actor), UUID.randomUUID()));
        if (transition != null) {
            install(transition.runtime());
            actor.sendMessage(Component.text("LFG listing closed.", NamedTextColor.YELLOW));
        }
    }

    private void reconcileListings() {
        for (LfgListingRuntime listing : List.copyOf(listings.values())) {
            if (listing.closed()) {
                continue;
            }
            PartyRuntime party = parties.party(listing.partyId());
            int expectedMembers =
                    PartyEngine.MAX_MEMBERS
                            - listing.availableSlots()
                            + listing.acceptedApplicants().size();
            boolean invalid =
                    party == null
                            || party.leaderId().isEmpty()
                            || !party.leaderId().orElseThrow().equals(listing.leaderId())
                            || party.members().size() != expectedMembers
                            || !party.members()
                                    .keySet()
                                    .containsAll(listing.acceptedApplicants().keySet());
            if (invalid) {
                LfgTransition closed =
                        ((Result.Success<LfgTransition, LfgErrorCode>)
                                        engine.close(
                                                listing, listing.leaderId(), UUID.randomUUID()))
                                .value();
                install(closed.runtime());
            }
        }
    }

    private LfgListingRuntime leaderListing(Player actor) {
        PartyRuntime party = parties.partyFor(actor);
        LfgListingRuntime listing =
                party == null ? null : listings.get(listingByParty.get(party.partyId()));
        if (listing == null || !listing.leaderId().equals(characterId(actor))) {
            actor.sendMessage(
                    Component.text("You do not lead an LFG listing.", NamedTextColor.RED));
            return null;
        }
        return listing;
    }

    private LfgListingRuntime listing(Player actor, String rawId) {
        UUID value;
        try {
            value = UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text("Listing ID must be a UUID.", NamedTextColor.RED));
            return null;
        }
        LfgListingRuntime listing = listings.get(new LfgListingId(value));
        if (listing == null || listing.closed()) {
            actor.sendMessage(Component.text("LFG listing is not active.", NamedTextColor.RED));
            return null;
        }
        return listing;
    }

    private void install(LfgListingRuntime listing) {
        if (listing.closed()) {
            listings.remove(listing.listingId());
            listingByParty.remove(listing.partyId());
        } else {
            listings.put(listing.listingId(), listing);
            listingByParty.put(listing.partyId(), listing.listingId());
        }
    }

    private CharacterId applicantId(LfgListingRuntime listing, String token) {
        Player online = plugin.getServer().getPlayerExact(token);
        if (online != null && listing.pendingRequests().containsKey(characterId(online))) {
            return characterId(online);
        }
        try {
            CharacterId parsed = new CharacterId(UUID.fromString(token));
            return listing.pendingRequests().containsKey(parsed) ? parsed : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Optional<Player> leaderOnline(LfgListingRuntime listing) {
        Player player = plugin.getServer().getPlayer(listing.leaderId().value());
        return player != null && player.isOnline() && characterSessions.ready(player)
                ? Optional.of(player)
                : Optional.empty();
    }

    private DefinitionId optionalDefinition(Player actor, String[] args, int index, String label) {
        return args.length <= index || "*".equals(args[index])
                ? null
                : definition(actor, args[index], label);
    }

    private static boolean invalidOptional(String[] args, int index, DefinitionId parsed) {
        return args.length > index && !"*".equals(args[index]) && parsed == null;
    }

    private static DefinitionId definition(Player actor, String value, String label) {
        try {
            return DefinitionId.of(value);
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text(label + " ID is invalid.", NamedTextColor.RED));
            return null;
        }
    }

    private static LfgRolePreference role(Player actor, String value) {
        try {
            return LfgRolePreference.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(
                    Component.text(
                            "Role must be FRONTLINE, GUARD_CONTROL, DAMAGE, SUPPORT or FLEXIBLE.",
                            NamedTextColor.RED));
            return null;
        }
    }

    private static Set<String> tags(String value) {
        if ("-".equals(value) || value.isBlank()) {
            return Set.of();
        }
        return List.of(value.split(",")).stream()
                .filter(candidate -> !candidate.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String note(String value) {
        return "-".equals(value) ? "" : value.replace('_', ' ');
    }

    private static LfgTransition success(Player actor, Result<LfgTransition, LfgErrorCode> result) {
        if (result instanceof Result.Failure<LfgTransition, LfgErrorCode> failure) {
            actor.sendMessage(
                    Component.text(
                            failure.error().code() + ": " + failure.detail(), NamedTextColor.RED));
            return null;
        }
        return ((Result.Success<LfgTransition, LfgErrorCode>) result).value();
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

    private static void usage(Player player) {
        player.sendMessage(
                "Usage: /mmo lfg <list <activity> <region> <language> <role> <note|-> [requirements-csv|-] [auto]|browse [activity|*] [region|*] [language|*] [role|*] [eligibility-csv|-]|request <listing-uuid> <role> <note|-> <eligibility-csv|->|requests|accept <player>|decline <player>|cancel <listing-uuid>|status|close>");
    }
}
