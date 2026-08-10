from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/SceneHubController.java")
text = path.read_text()

import_old = '''import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneMode;
'''
import_new = '''import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneHubNavigationPolicy;
import com.branz.mmorpg.scenes.SceneMode;
'''
if text.count(import_old) != 1:
    raise SystemExit("Expected Scene import insertion point once")
text = text.replace(import_old, import_new, 1)

admission_old = '''        if (!characterSessions.ready(player)) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE, "Character database session is not ready.");
        }
        if (player.isDead()
'''
admission_new = '''        if (!characterSessions.ready(player)) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE, "Character database session is not ready.");
        }
        Optional<String> mutationRejection =
                SceneValueMutationAdmission.rejection(
                        characterSessions.valueMutationInFlight(player));
        if (mutationRejection.isPresent()) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE, mutationRejection.orElseThrow());
        }
        if (player.isDead()
'''
if text.count(admission_old) != 1:
    raise SystemExit("Expected Scene admission insertion point once")
text = text.replace(admission_old, admission_new, 1)

hub_old = '''        if (session.mode() == SceneMode.HUB) {
            HUB_BUTTONS.forEach(
                    (mode, spec) ->
                            inventory.setItem(
                                    spec.slot(),
                                    button(spec.material(), spec.label(), mode.name())));
            inventory.setItem(49, button(Material.BARRIER, "Exit Chronicle", "exit"));
'''
hub_new = '''        if (session.mode() == SceneMode.HUB) {
            SceneHubNavigationPolicy.visibleHubModes(restContext(player))
                    .forEach(
                            mode -> {
                                ButtonSpec spec = HUB_BUTTONS.get(mode);
                                if (spec != null) {
                                    inventory.setItem(
                                            spec.slot(),
                                            button(spec.material(), spec.label(), mode.name()));
                                }
                            });
            inventory.setItem(49, button(Material.BARRIER, "Exit Chronicle", "exit"));
'''
if text.count(hub_old) != 1:
    raise SystemExit("Expected Scene hub rendering insertion point once")
text = text.replace(hub_old, hub_new, 1)

mode_old = '''        try {
            mode = SceneMode.valueOf(action);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Result<SceneSession, SceneErrorCode> result =
                sessions.changeMode(player.getUniqueId(), holder.sessionId(), mode);
'''
mode_new = '''        try {
            mode = SceneMode.valueOf(action);
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (!SceneHubNavigationPolicy.canEnterFromHub(mode, restContext(player))) {
            player.sendActionBar(
                    Component.text(
                            "This Chronicle workflow is only available at Rest Context.",
                            NamedTextColor.RED));
            return;
        }
        Result<SceneSession, SceneErrorCode> result =
                sessions.changeMode(player.getUniqueId(), holder.sessionId(), mode);
'''
if text.count(mode_old) != 1:
    raise SystemExit("Expected Scene mode admission insertion point once")
text = text.replace(mode_old, mode_new, 1)

path.write_text(text)
