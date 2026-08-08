from pathlib import Path

controller_path = Path('mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/CombatSessionController.java')
controller = controller_path.read_text(encoding='utf-8')
old_renderer = '''    private void renderMeleeHitFeedback(
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
new_renderer = '''    private void renderMeleeHitFeedback(
            LivingEntity entity, MeleeHitFeedbackPolicy.MeleeHitFeedbackSpec feedback) {
        MeleeHitFeedbackRenderer.render(entity, feedback);
    }
'''
if controller.count(old_renderer) != 1:
    raise SystemExit(f'feedback renderer guard failed: {controller.count(old_renderer)}')
controller_path.write_text(controller.replace(old_renderer, new_renderer, 1), encoding='utf-8')

plugin_path = Path('mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/BranzMmoPlugin.java')
plugin = plugin_path.read_text(encoding='utf-8')
old_startup = '''        } else {
            databaseRuntime.close();
            databaseRuntime = null;
            getLogger()
                    .severe(
                            "Branz MMO entered safe maintenance mode; player sessions are blocked.");
        }
        scheduleSmokeShutdown();
    }
'''
new_startup = '''        } else {
            databaseRuntime.close();
            databaseRuntime = null;
            getLogger()
                    .severe(
                            "Branz MMO entered safe maintenance mode; player sessions are blocked.");
        }
        if (decision.acceptsSessions()) {
            CombatRuntimeAcceptanceProbe.schedule(this);
        }
        scheduleSmokeShutdown();
    }
'''
if plugin.count(old_startup) != 1:
    raise SystemExit(f'plugin startup guard failed: {plugin.count(old_startup)}')
plugin_path.write_text(plugin.replace(old_startup, new_startup, 1), encoding='utf-8')

build_path = Path('mmo-bootstrap/build.gradle.kts')
build = build_path.read_text(encoding='utf-8')
old_smoke = '''    if (providers.gradleProperty("smokeTest").orNull == "true") {
        jvmArgs(
            "-Dmmo.bootstrap.smoke-test=true",
        )
    }
}
'''
new_smoke = '''    val combatAcceptance = providers.gradleProperty("combatAcceptance").orNull == "true"
    if (combatAcceptance) {
        val acceptanceMarker =
            project.layout.buildDirectory.file("combat-runtime-acceptance.pass").get().asFile
        jvmArgs(
            "-Dmmo.bootstrap.smoke-test=true",
            "-Dmmo.bootstrap.combat-acceptance-test=true",
            "-Dmmo.bootstrap.combat-acceptance-marker=${acceptanceMarker.absolutePath}",
        )
        doFirst {
            acceptanceMarker.delete()
        }
        doLast {
            check(
                acceptanceMarker.isFile &&
                    acceptanceMarker.readText().trim() == "COMBAT_RUNTIME_ACCEPTANCE_PASS",
            ) {
                "Paper combat runtime acceptance marker was not produced."
            }
        }
    } else if (providers.gradleProperty("smokeTest").orNull == "true") {
        jvmArgs(
            "-Dmmo.bootstrap.smoke-test=true",
        )
    }
}
'''
if build.count(old_smoke) != 1:
    raise SystemExit(f'runServer smoke guard failed: {build.count(old_smoke)}')
build_path.write_text(build.replace(old_smoke, new_smoke, 1), encoding='utf-8')
