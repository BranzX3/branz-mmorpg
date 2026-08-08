from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java")
source = path.read_text(encoding="utf-8")

import_needle = "import org.bukkit.Particle;\n"
if source.count(import_needle) != 1:
    raise SystemExit(f"Particle import guard failed: {source.count(import_needle)}")
source = source.replace(import_needle, import_needle + "import org.bukkit.Sound;\n", 1)

hit_needle = """            trainingTargetHealth.put(target.entityId(), healthResolution.runtime());
            observeSuccessfulCombatAction(
"""
hit_replacement = """            trainingTargetHealth.put(target.entityId(), healthResolution.runtime());
            renderMeleeHitFeedback(entity, healthResolution.appliedAmount());
            observeSuccessfulCombatAction(
"""
if source.count(hit_needle) != 1:
    raise SystemExit(f"melee hit application guard failed: {source.count(hit_needle)}")
source = source.replace(hit_needle, hit_replacement, 1)

round_marker = "    private static double roundOne(double value) {"
if source.count(round_marker) != 1:
    raise SystemExit(f"roundOne marker guard failed: {source.count(round_marker)}")
helper = """    private void renderMeleeHitFeedback(LivingEntity entity, double appliedDamage) {
        MeleeHitFeedbackPolicy.forAppliedDamage(appliedDamage)
                .ifPresent(
                        feedback -> {
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

"""
source = source.replace(round_marker, helper + round_marker, 1)
path.write_text(source, encoding="utf-8")
