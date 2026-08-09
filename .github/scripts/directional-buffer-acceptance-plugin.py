from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java")
text = path.read_text(encoding="utf-8")
old = '''        SuccessfulCombatActionObserver onboardingAcceptanceObserver =
                OnboardingClientAcceptanceProbe.install(
                        this, startingFoundationController, characterSessionController);
        combatSessionController.setSuccessfulActionObserver(
                (actorId, actionId, moveId, currentTick) -> {
                    liveTeachingSessionController.observeSuccessfulAction(
                            actorId, actionId, moveId, currentTick);
                    onboardingAcceptanceObserver.observe(actorId, actionId, moveId, currentTick);
                });
'''
new = '''        SuccessfulCombatActionObserver onboardingAcceptanceObserver =
                OnboardingClientAcceptanceProbe.install(
                        this, startingFoundationController, characterSessionController);
        SuccessfulCombatActionObserver directionalBufferAcceptanceObserver =
                DirectionalBufferClientAcceptanceProbe.install(
                        this, startingFoundationController, combatSessionController);
        combatSessionController.setSuccessfulActionObserver(
                (actorId, actionId, moveId, currentTick) -> {
                    liveTeachingSessionController.observeSuccessfulAction(
                            actorId, actionId, moveId, currentTick);
                    onboardingAcceptanceObserver.observe(actorId, actionId, moveId, currentTick);
                    directionalBufferAcceptanceObserver.observe(
                            actorId, actionId, moveId, currentTick);
                });
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"acceptance observer composition expected one match, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("directional buffer acceptance plugin patch applied")
