from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
source = path.read_text(encoding="utf-8")

import_needle = "import com.branz.mmorpg.combat.input.PrimaryAttackIngressPolicy;\n"
if source.count(import_needle) != 1:
    raise SystemExit(f"import guard failed: {source.count(import_needle)}")
source = source.replace(
    import_needle,
    import_needle + "import com.branz.mmorpg.combat.input.PrimaryAttackInputCoordinator;\n",
    1,
)

old = """        Result<CombatInputRequest, InputRejectionCode> observed =
                session.input.observe(
                        new InputObservation(
                                plugin.getServer().getCurrentTick(),
                                intent,
                                DirectionSnapshot.NEUTRAL,
                                primary.input().branch(),
                                new InputDeduplicationKey(\"MAIN_HAND\", \"ATTACK\")));
        if (!(observed instanceof Result.Success<CombatInputRequest, InputRejectionCode> input)) {
            return true;
        }
        Result<InputRouteOutcome, InputRejectionCode> routed =
                session.input.routeFrame(List.of(input.value()), routingContext(player, session));
        if (routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode> success) {
            handleRoute(player, session, success.value());
        }
"""
new = """        Optional<InputRouteOutcome> routed =
                PrimaryAttackInputCoordinator.route(
                        session.input,
                        plugin.getServer().getCurrentTick(),
                        primary.input().branch(),
                        routingContext(player, session));
        routed.ifPresent(outcome -> handleRoute(player, session, outcome));
"""
if source.count(old) != 1:
    raise SystemExit(f"primary routing block guard failed: {source.count(old)}")
source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
