from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
'''        MoveDefinition primary = primaryMove(player).orElse(null);
        if (primary == null) {
            return true;
        }
        SemanticInput intent = resolvedIntent(player, session, ClientAction.ATTACK).orElse(null);
        if (intent != SemanticInput.PRIMARY) {
            return true;
        }
        Optional<InputRouteOutcome> routed =
                PrimaryAttackInputCoordinator.route(
                        session.input,
                        plugin.getServer().getCurrentTick(),
                        primary.input().branch(),
                        routingContext(player, session));
''',
'''        DirectionSnapshot attackDirection = direction(player.getCurrentInput());
        MovesetBranch attackBranch = primaryBranch(session, attackDirection);
        MoveDefinition primary = primaryMove(player, attackBranch, attackDirection).orElse(null);
        if (primary == null) {
            return true;
        }
        SemanticInput intent = resolvedIntent(player, session, ClientAction.ATTACK).orElse(null);
        if (intent != SemanticInput.PRIMARY) {
            return true;
        }
        Optional<InputRouteOutcome> routed =
                PrimaryAttackInputCoordinator.route(
                        session.input,
                        plugin.getServer().getCurrentTick(),
                        attackDirection,
                        attackBranch.name(),
                        routingContext(player, session, attackBranch));
''',
"route primary attack",
)

replace_once(
'''        if (session.timeline.phase().terminal()) {
            session.resources = session.timeline.resources();
            finishTrace(session, session.timeline);
            session.timeline = null;
            session.previousActionTransform = null;
            session.activeMoveEvidenceActionId = null;
            session.action = ActionState.IDLE;
        } else {
''',
'''        if (session.timeline.phase().terminal()) {
            session.resources = session.timeline.resources();
            finishTrace(session, session.timeline);
            session.timeline = null;
            session.previousActionTransform = null;
            session.activeMoveEvidenceActionId = null;
            session.action = ActionState.IDLE;
            pollBuffered(player, session);
        } else {
''',
"poll buffer after action completion",
)

replace_once(
'''    private void handleRoute(Player player, LiveSession session, InputRouteOutcome outcome) {
        if (outcome.decision() == com.branz.mmorpg.combat.input.InputRouteDecision.EXECUTED) {
            startMove(player, session);
        } else {
            player.sendActionBar(Component.text("Primary opener buffered.", NamedTextColor.YELLOW));
        }
    }

    private void startMove(Player player, LiveSession session) {
        MoveDefinition activeMove = primaryMove(player).orElse(null);
''',
'''    private void handleRoute(Player player, LiveSession session, InputRouteOutcome outcome) {
        if (outcome.decision() == com.branz.mmorpg.combat.input.InputRouteDecision.EXECUTED) {
            startMove(player, session, outcome.request());
        } else {
            player.sendActionBar(Component.text("Primary follow-up buffered.", NamedTextColor.YELLOW));
        }
    }

    private void startMove(Player player, LiveSession session) {
        startMove(
                player,
                session,
                new CombatInputRequest(
                        0,
                        plugin.getServer().getCurrentTick(),
                        SemanticInput.PRIMARY,
                        DirectionSnapshot.NEUTRAL,
                        MovesetBranch.PRIMARY_1.name()));
    }

    private void startMove(
            Player player, LiveSession session, CombatInputRequest request) {
        MovesetBranch branch;
        try {
            branch = MovesetBranch.valueOf(request.branchFamily());
        } catch (IllegalArgumentException exception) {
            player.sendActionBar(Component.text("Primary branch is unavailable.", NamedTextColor.RED));
            return;
        }
        MoveDefinition activeMove = primaryMove(player, branch, request.direction()).orElse(null);
''',
"execute routed request",
)

replace_once(
'''    private InputRoutingContext routingContext(Player player, LiveSession session) {
        if (session.dodge != null) {
            return InputRoutingContext.legal(
                    java.util.EnumSet.of(
                            SemanticInput.FORCED_INTERRUPT, SemanticInput.UI_DANGER_CLOSE));
        }
        return inputPolicy.routingContext(policyContext(player, session), false);
    }
''',
'''    private InputRoutingContext routingContext(Player player, LiveSession session) {
        return routingContext(player, session, null);
    }

    private InputRoutingContext routingContext(
            Player player, LiveSession session, MovesetBranch requestedBranch) {
        if (session.dodge != null) {
            return InputRoutingContext.legal(
                    java.util.EnumSet.of(
                            SemanticInput.FORCED_INTERRUPT, SemanticInput.UI_DANGER_CLOSE));
        }
        boolean authoredQueueWindowOpen =
                session.timeline != null
                        && requestedBranch != null
                        && session.timeline.chainWindowOpen(requestedBranch.name());
        return inputPolicy.routingContext(policyContext(player, session), authoredQueueWindowOpen);
    }

    private static MovesetBranch primaryBranch(
            LiveSession session, DirectionSnapshot direction) {
        if (session.timeline != null) {
            for (MoveDefinition.ChainWindow window : session.timeline.move().cancels().chainWindows()) {
                if (!session.timeline.chainWindowOpen(window.branch())) {
                    continue;
                }
                try {
                    return MovesetBranch.valueOf(window.branch());
                } catch (IllegalArgumentException ignored) {
                    // Invalid authored branch cannot become a server-owned combat request.
                }
            }
        }
        return switch (direction) {
            case FORWARD -> MovesetBranch.PRIMARY_DIRECTIONAL_FORWARD;
            case BACK -> MovesetBranch.PRIMARY_DIRECTIONAL_BACK;
            case LEFT, RIGHT, NEUTRAL -> MovesetBranch.PRIMARY_1;
        };
    }
''',
"authored routing context",
)

replace_once(
'''    private Optional<MoveDefinition> primaryMove(Player player) {
        if (combatReadinessFailure(player).isPresent()) {
            return Optional.empty();
        }
        return equippedWeaponFamily(player)
                .flatMap(
                        family -> {
                            MoveDefinition fallback =
                                    switch (family) {
                                        case "SWORD" -> trainingMove;
                                        case "GREATSWORD" -> trainingGreatswordMove;
                                        case "SWORD_SHIELD" -> trainingSwordShieldMove;
                                        case "STAFF" -> trainingStaffMove;
                                        default -> null;
                                    };
                            if (fallback == null) {
                                return Optional.empty();
                            }
                            return buildResolution(player, family)
                                    .flatMap(
                                            resolution ->
                                                    Optional.ofNullable(
                                                                    resolution
                                                                            .resolvedMoves()
                                                                            .get(
                                                                                    MovesetBranch
                                                                                            .PRIMARY_1))
                                                            .flatMap(moves::find)
                                                            .or(() -> Optional.of(fallback)))
                                    .map(move -> moveWithBuildCosts(player, family, move));
                        });
    }
''',
'''    private Optional<MoveDefinition> primaryMove(Player player) {
        return primaryMove(player, MovesetBranch.PRIMARY_1, DirectionSnapshot.NEUTRAL);
    }

    private Optional<MoveDefinition> primaryMove(
            Player player, MovesetBranch branch, DirectionSnapshot direction) {
        if (combatReadinessFailure(player).isPresent()) {
            return Optional.empty();
        }
        return equippedWeaponFamily(player)
                .flatMap(
                        family -> {
                            Optional<MoveDefinition> authored =
                                    buildResolution(player, family)
                                            .flatMap(
                                                    resolution ->
                                                            Optional.ofNullable(
                                                                            resolution
                                                                                    .resolvedMoves()
                                                                                    .get(branch))
                                                                    .flatMap(moves::find));
                            Optional<MoveDefinition> fallback = starterPrimaryMove(family, branch);
                            if (authored.isEmpty()
                                    && fallback.isEmpty()
                                    && (branch == MovesetBranch.PRIMARY_DIRECTIONAL_FORWARD
                                            || branch == MovesetBranch.PRIMARY_DIRECTIONAL_BACK)) {
                                fallback = starterPrimaryMove(family, MovesetBranch.PRIMARY_1);
                            }
                            return authored.or(() -> fallback)
                                    .filter(
                                            move ->
                                                    move.input().direction()
                                                                    == DirectionSnapshot.NEUTRAL
                                                            || move.input().direction() == direction)
                                    .map(move -> moveWithBuildCosts(player, family, move));
                        });
    }

    private Optional<MoveDefinition> starterPrimaryMove(String family, MovesetBranch branch) {
        MoveDefinition move =
                switch (family) {
                    case "SWORD" -> branch == MovesetBranch.PRIMARY_1 ? trainingMove : null;
                    case "GREATSWORD" ->
                            switch (branch) {
                                case PRIMARY_1 -> trainingGreatswordMove;
                                case PRIMARY_DIRECTIONAL_FORWARD ->
                                        moves.find(
                                                        DefinitionId.of(
                                                                "move.training_greatsword.forward_drive"))
                                                .orElse(null);
                                case PRIMARY_2 ->
                                        moves.find(
                                                        DefinitionId.of(
                                                                "move.training_greatsword.followup_crosscut"))
                                                .orElse(null);
                                default -> null;
                            };
                    case "SWORD_SHIELD" ->
                            branch == MovesetBranch.PRIMARY_1 ? trainingSwordShieldMove : null;
                    case "STAFF" -> branch == MovesetBranch.PRIMARY_1 ? trainingStaffMove : null;
                    default -> null;
                };
        return Optional.ofNullable(move);
    }
''',
"branch-aware move resolution",
)

path.write_text(text, encoding="utf-8")
print("directional buffer controller patch applied")
