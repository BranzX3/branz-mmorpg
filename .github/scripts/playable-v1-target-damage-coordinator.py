from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
source = path.read_text(encoding="utf-8")

old = '''            CombatHealthResolution healthResolution =
                    enemyHealth.damage(targetHealth, currentTick, resolvedDamage);
            trainingTargetHealth.put(target.entityId(), healthResolution.runtime());
            renderMeleeHitFeedback(entity, healthResolution.appliedAmount());
'''
new = '''            MeleeTargetDamageCoordinator.MeleeTargetDamageResult targetDamage =
                    MeleeTargetDamageCoordinator.apply(
                            enemyHealth, targetHealth, currentTick, resolvedDamage);
            trainingTargetHealth.put(target.entityId(), targetDamage.runtime());
            targetDamage.feedback().ifPresent(feedback -> renderMeleeHitFeedback(entity, feedback));
'''
if source.count(old) != 1:
    raise SystemExit(f"target damage application guard failed: {source.count(old)}")
source = source.replace(old, new, 1)

replacements = {
    "            totalDamage += healthResolution.appliedAmount();\n":
        "            totalDamage += targetDamage.appliedDamage();\n",
    "                        roundOne(healthResolution.runtime().current())\n":
        "                        roundOne(targetDamage.runtime().current())\n",
    "            if (healthResolution.lethalNow()) {\n":
        "            if (targetDamage.lethalNow()) {\n",
}
for needle, replacement in replacements.items():
    if source.count(needle) != 1:
        raise SystemExit(f"downstream target damage guard failed for {needle!r}: {source.count(needle)}")
    source = source.replace(needle, replacement, 1)

old_render = '''    private void renderMeleeHitFeedback(LivingEntity entity, double appliedDamage) {
        MeleeHitFeedbackPolicy.forAppliedDamage(appliedDamage)
                .ifPresent(
                        feedback -> {
                            entity.playHurtAnimation(0.0f);
                            Location location =
                                    entity.getLocation().add(0.0, entity.getHeight() * 0.6, 0.0);
                            entity.getWorld()
                                    .spawnParticle(
                                            Particle.DAMAGE_INDICATOR,
                                            location,
                                            feedback.particleCount(),
                                            0.18,
                                            0.18,
                                            0.18,
                                            0.02);
                            entity.getWorld()
                                    .playSound(
                                            location,
                                            Sound.ENTITY_PLAYER_ATTACK_STRONG,
                                            feedback.volume(),
                                            feedback.pitch());
                        });
    }
'''
new_render = '''    private void renderMeleeHitFeedback(
            LivingEntity entity, MeleeHitFeedbackPolicy.MeleeHitFeedbackSpec feedback) {
        entity.playHurtAnimation(0.0f);
        Location location = entity.getLocation().add(0.0, entity.getHeight() * 0.6, 0.0);
        entity.getWorld()
                .spawnParticle(
                        Particle.DAMAGE_INDICATOR,
                        location,
                        feedback.particleCount(),
                        0.18,
                        0.18,
                        0.18,
                        0.02);
        entity.getWorld()
                .playSound(
                        location,
                        Sound.ENTITY_PLAYER_ATTACK_STRONG,
                        feedback.volume(),
                        feedback.pitch());
    }
'''
if source.count(old_render) != 1:
    raise SystemExit(f"feedback renderer guard failed: {source.count(old_render)}")
source = source.replace(old_render, new_render, 1)
path.write_text(source, encoding="utf-8")
