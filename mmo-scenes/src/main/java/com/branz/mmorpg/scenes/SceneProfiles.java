package com.branz.mmorpg.scenes;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.EnumMap;

/** Canonical V1 profiles. Optional compatibility overlays remain explicit, never automatic. */
public final class SceneProfiles {
    private SceneProfiles() {}

    public static SceneProfile localCharacterHub() {
        EnumMap<SceneMode, SceneModeProfile> modes = new EnumMap<>(SceneMode.class);
        modes.put(SceneMode.HUB, SceneModeProfile.readOnly(SceneMode.HUB));
        modes.put(SceneMode.EQUIPMENT, SceneModeProfile.previewCommit(SceneMode.EQUIPMENT, false));
        modes.put(
                SceneMode.WARDROBE_DYE,
                SceneModeProfile.previewCommit(SceneMode.WARDROBE_DYE, false));
        modes.put(
                SceneMode.COMBAT_ARTS,
                SceneModeProfile.previewCommit(SceneMode.COMBAT_ARTS, false));
        modes.put(SceneMode.FORMS, SceneModeProfile.previewCommit(SceneMode.FORMS, true));
        modes.put(
                SceneMode.MAGIC_ATTUNEMENT,
                SceneModeProfile.previewCommit(SceneMode.MAGIC_ATTUNEMENT, true));
        modes.put(
                SceneMode.CHARACTER_INFORMATION,
                SceneModeProfile.readOnly(SceneMode.CHARACTER_INFORMATION));

        // Rest workflows are contextual Chronicle entries, not primary root categories.
        modes.put(
                SceneMode.FLASK_PREPARATION,
                SceneModeProfile.immediate(SceneMode.FLASK_PREPARATION));

        // Existing local features remain compatibility overlays, not mandatory V1 root entries.
        modes.put(
                SceneMode.JOURNAL_PENDING_REWARDS,
                SceneModeProfile.readOnly(SceneMode.JOURNAL_PENDING_REWARDS));
        modes.put(SceneMode.SETTINGS_HELP, SceneModeProfile.readOnly(SceneMode.SETTINGS_HELP));

        SceneHubNavigationPolicy.requireLocalCharacterHubModes(modes.keySet());
        return new SceneProfile(
                DefinitionId.of("scene.character.local_hub"),
                SceneTopology.LOCAL_CHARACTER,
                SceneMode.HUB,
                modes,
                true);
    }

    public static SceneProfile fixedPrivateCharacterCreation() {
        EnumMap<SceneMode, SceneModeProfile> modes = new EnumMap<>(SceneMode.class);
        modes.put(
                SceneMode.CHARACTER_CREATION,
                SceneModeProfile.previewCommit(SceneMode.CHARACTER_CREATION, false));
        modes.put(
                SceneMode.APPEARANCE_PREVIEW,
                SceneModeProfile.previewCommit(SceneMode.APPEARANCE_PREVIEW, false));
        return new SceneProfile(
                DefinitionId.of("scene.character.private_creation"),
                SceneTopology.FIXED_PRIVATE,
                SceneMode.CHARACTER_CREATION,
                modes,
                true);
    }

    public static SceneProfile narrativeDialogue() {
        EnumMap<SceneMode, SceneModeProfile> modes = new EnumMap<>(SceneMode.class);
        modes.put(
                SceneMode.IMPORTANT_DIALOGUE,
                new SceneModeProfile(
                        SceneMode.IMPORTANT_DIALOGUE, SceneInteractionModel.DIALOGUE, false));
        modes.put(
                SceneMode.CUTSCENE,
                new SceneModeProfile(SceneMode.CUTSCENE, SceneInteractionModel.CINEMATIC, false));
        return new SceneProfile(
                DefinitionId.of("scene.narrative.dialogue"),
                SceneTopology.NARRATIVE,
                SceneMode.IMPORTANT_DIALOGUE,
                modes,
                true);
    }
}
