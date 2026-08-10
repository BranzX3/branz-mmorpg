package com.branz.mmorpg.scenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SceneHubNavigationPolicyTest {
    @Test
    void canonicalV1RootContainsOnlyAuthoredPrimaryCategoriesInOrder() {
        assertEquals(
                List.of(
                        SceneMode.EQUIPMENT,
                        SceneMode.WARDROBE_DYE,
                        SceneMode.COMBAT_ARTS,
                        SceneMode.FORMS,
                        SceneMode.MAGIC_ATTUNEMENT,
                        SceneMode.CHARACTER_INFORMATION),
                SceneHubNavigationPolicy.primaryRootModes());
    }

    @Test
    void flaskIsRestContextualRatherThanPrimaryRoot() {
        assertEquals(
                Set.of(SceneMode.FLASK_PREPARATION),
                SceneHubNavigationPolicy.restContextualModes());
        assertFalse(SceneHubNavigationPolicy.isPrimaryRootMode(SceneMode.FLASK_PREPARATION));
        assertTrue(SceneHubNavigationPolicy.isRestContextualMode(SceneMode.FLASK_PREPARATION));
    }

    @Test
    void journalAndSettingsRemainCompatibilityOverlays() {
        assertEquals(
                Set.of(SceneMode.JOURNAL_PENDING_REWARDS, SceneMode.SETTINGS_HELP),
                SceneHubNavigationPolicy.compatibilityOverlayModes());
        assertFalse(
                SceneHubNavigationPolicy.isPrimaryRootMode(SceneMode.JOURNAL_PENDING_REWARDS));
        assertFalse(SceneHubNavigationPolicy.isPrimaryRootMode(SceneMode.SETTINGS_HELP));
        assertTrue(
                SceneHubNavigationPolicy.isCompatibilityOverlayMode(
                        SceneMode.JOURNAL_PENDING_REWARDS));
        assertTrue(SceneHubNavigationPolicy.isCompatibilityOverlayMode(SceneMode.SETTINGS_HELP));
    }

    @Test
    void localCharacterProfileCarriesPrimaryAndRestModesWithoutPromotingOverlays() {
        SceneProfile profile = SceneProfiles.localCharacterHub();

        for (SceneMode mode : SceneHubNavigationPolicy.primaryRootModes()) {
            assertTrue(profile.mode(mode).isPresent());
        }
        for (SceneMode mode : SceneHubNavigationPolicy.restContextualModes()) {
            assertTrue(profile.mode(mode).isPresent());
        }
        for (SceneMode mode : SceneHubNavigationPolicy.compatibilityOverlayModes()) {
            assertTrue(profile.mode(mode).isPresent());
            assertFalse(SceneHubNavigationPolicy.isPrimaryRootMode(mode));
        }
    }
}
