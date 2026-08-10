from pathlib import Path

combat_path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
combat = combat_path.read_text()

field_old = '''    private SuccessfulCombatActionObserver successfulActionObserver =
            SuccessfulCombatActionObserver.NONE;
    private BiConsumer<Player, String> consumableInterruptObserver = (player, reason) -> {};
'''
field_new = '''    private SuccessfulCombatActionObserver successfulActionObserver =
            SuccessfulCombatActionObserver.NONE;
    private ShieldBlockedImpactObserver shieldBlockedImpactObserver =
            ShieldBlockedImpactObserver.NONE;
    private BiConsumer<Player, String> consumableInterruptObserver = (player, reason) -> {};
'''
if combat.count(field_old) != 1:
    raise SystemExit("Expected successful observer field insertion point once")
combat = combat.replace(field_old, field_new, 1)

setter_old = '''    void setSuccessfulActionObserver(SuccessfulCombatActionObserver observer) {
        if (successfulActionObserver != SuccessfulCombatActionObserver.NONE) {
            throw new IllegalStateException("successful combat action observer is already set");
        }
        successfulActionObserver = Objects.requireNonNull(observer, "observer");
    }
'''
setter_new = setter_old + '''
    void setShieldBlockedImpactObserver(ShieldBlockedImpactObserver observer) {
        if (shieldBlockedImpactObserver != ShieldBlockedImpactObserver.NONE) {
            throw new IllegalStateException("shield blocked-impact observer is already set");
        }
        shieldBlockedImpactObserver = Objects.requireNonNull(observer, "observer");
    }
'''
if combat.count(setter_old) != 1:
    raise SystemExit("Expected observer setter insertion point once")
combat = combat.replace(setter_old, setter_new, 1)

impact_old = '''        defenderSession.guard = resolved.guardRuntime();
        if (resolved.staminaSpent() > 0) {
'''
impact_new = '''        defenderSession.guard = resolved.guardRuntime();
        if (ShieldImpactWearPolicy.consumesDurability(resolved.outcome())) {
            shieldBlockedImpactObserver.observe(defender, UUID.randomUUID());
        }
        if (resolved.staminaSpent() > 0) {
'''
if combat.count(impact_old) != 2:
    raise SystemExit(f"Expected two guard resolution insertion points, found {combat.count(impact_old)}")
combat = combat.replace(impact_old, impact_new)

message_old = '''                            combatReadinessFailure(player)
                                    .orElse("This weapon has no defensive response."),
'''
message_new = '''                            guardReadinessFailure(player)
                                    .orElse("This weapon has no defensive response."),
'''
if combat.count(message_old) != 1:
    raise SystemExit("Expected guard feedback insertion point once")
combat = combat.replace(message_old, message_new, 1)

guard_old = '''    private Optional<GuardEngine> guardEngineFor(Player player) {
        if (combatReadinessFailure(player).isPresent()) {
            return Optional.empty();
        }
'''
guard_new = '''    private Optional<String> guardReadinessFailure(Player player) {
        Optional<String> combatFailure = combatReadinessFailure(player);
        if (combatFailure.isPresent()) {
            return combatFailure;
        }
        ItemDefinition main = equippedDefinition(player, EquipmentSlot.MAIN_HAND).orElse(null);
        Optional<ItemDefinition> offHand = equippedDefinition(player, EquipmentSlot.OFF_HAND);
        if (main == null) {
            return Optional.empty();
        }
        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> resolved =
                WeaponLoadoutPolicy.resolve(main, offHand);
        if (resolved
                instanceof
                Result.Failure<WeaponLoadoutResolution, WeaponLoadoutErrorCode> failure) {
            return Optional.of("Guard not ready: " + failure.detail());
        }
        ItemDefinition shield =
                offHand.filter(definition -> definition.shieldProfile().isPresent()).orElse(null);
        if (shield == null) {
            return Optional.empty();
        }
        LoadedCharacterSession character = characters.active(player).orElse(null);
        return character == null
                ? Optional.of("Guard not ready: character state is unavailable.")
                : ShieldCombatReadiness.durabilityFailure(character, shield);
    }

    private Optional<GuardEngine> guardEngineFor(Player player) {
        if (guardReadinessFailure(player).isPresent()) {
            return Optional.empty();
        }
'''
if combat.count(guard_old) != 1:
    raise SystemExit("Expected guard readiness insertion point once")
combat = combat.replace(guard_old, guard_new, 1)
combat_path.write_text(combat)

plugin_path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java")
plugin = plugin_path.read_text()

controller_old = '''        WeaponDurabilityController weaponDurabilityController =
                new WeaponDurabilityController(
                        this,
                        characterSessionController,
                        activeItemEngine.get(),
                        activeMoveEngine.get(),
                        new WeaponDurabilityService(databaseRuntime, characterSessionService),
                        pvpController,
                        snapshot.manifest().contentVersion());
        ResourceNodeContentCompiler.compileFirst(snapshot)
'''
controller_new = '''        WeaponDurabilityController weaponDurabilityController =
                new WeaponDurabilityController(
                        this,
                        characterSessionController,
                        activeItemEngine.get(),
                        activeMoveEngine.get(),
                        new WeaponDurabilityService(databaseRuntime, characterSessionService),
                        pvpController,
                        snapshot.manifest().contentVersion());
        ShieldDurabilityController shieldDurabilityController =
                new ShieldDurabilityController(
                        this,
                        characterSessionController,
                        activeItemEngine.get(),
                        new ShieldDurabilityService(databaseRuntime, characterSessionService),
                        pvpController,
                        snapshot.manifest().contentVersion());
        ResourceNodeContentCompiler.compileFirst(snapshot)
'''
if plugin.count(controller_old) != 1:
    raise SystemExit("Expected durability controller composition point once")
plugin = plugin.replace(controller_old, controller_new, 1)

observer_old = '''        combatSessionController.setHostileActionObserver(downedController::observeHostileAction);
        combatSessionController.setSuccessfulActionObserver(
'''
observer_new = '''        combatSessionController.setHostileActionObserver(downedController::observeHostileAction);
        combatSessionController.setShieldBlockedImpactObserver(shieldDurabilityController);
        combatSessionController.setSuccessfulActionObserver(
'''
if plugin.count(observer_old) != 1:
    raise SystemExit("Expected shield observer composition point once")
plugin = plugin.replace(observer_old, observer_new, 1)
plugin_path.write_text(plugin)
