package com.branz.mmorpg.progression.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.progression.evidence.BodyConditioningAxis;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeLearningEngineTest {
    private final KnowledgeLearningEngine engine = new KnowledgeLearningEngine();
    private final KnowledgeKey foundation = key(KnowledgeType.FOUNDATION, "foundation.greatsword");
    private final KnowledgeKey technique =
            key(KnowledgeType.TECHNIQUE, "technique.greatsword.cleaving_arc");

    @Test
    void acceptsOnlyWhenPermanentKnowledgeReadinessAndWorldFlagsArePresent() {
        ProgressionTrack mastery = ProgressionTrack.mastery("greatsword");
        ProgressionTrack conditioning = ProgressionTrack.conditioning(BodyConditioningAxis.MIGHT);
        LearningRequirements requirements =
                new LearningRequirements(
                        Set.of(foundation),
                        Map.of(
                                mastery,
                                ReadinessBand.RELIABLE,
                                conditioning,
                                ReadinessBand.DEVELOPING),
                        Set.of("trial.cleaving_arc.complete"));
        KnowledgeProfile eligible =
                new KnowledgeProfile(
                        Set.of(foundation),
                        Map.of(
                                mastery,
                                ReadinessBand.REFINED,
                                conditioning,
                                ReadinessBand.RELIABLE),
                        Set.of("trial.cleaving_arc.complete"));

        assertTrue(engine.evaluate(technique, requirements, eligible).accepted());
    }

    @Test
    void returnsStableFirstMissingRequirementWithoutLeakingNumericEvidence() {
        ProgressionTrack mastery = ProgressionTrack.mastery("greatsword");
        LearningRequirements requirements =
                new LearningRequirements(
                        Set.of(foundation),
                        Map.of(mastery, ReadinessBand.RELIABLE),
                        Set.of("trial.cleaving_arc.complete"));

        LearningDecision missingKnowledge =
                engine.evaluate(
                        technique,
                        requirements,
                        new KnowledgeProfile(
                                Set.of(), Map.of(mastery, ReadinessBand.EXCEPTIONAL), Set.of()));
        LearningDecision missingMastery =
                engine.evaluate(
                        technique,
                        requirements,
                        new KnowledgeProfile(
                                Set.of(foundation),
                                Map.of(),
                                Set.of("trial.cleaving_arc.complete")));
        LearningDecision missingFlag =
                engine.evaluate(
                        technique,
                        requirements,
                        new KnowledgeProfile(
                                Set.of(foundation),
                                Map.of(mastery, ReadinessBand.RELIABLE),
                                Set.of()));

        assertEquals(LearningRejectionReason.MISSING_KNOWLEDGE, missingKnowledge.reason());
        assertEquals(LearningRejectionReason.MASTERY_NOT_READY, missingMastery.reason());
        assertEquals(
                "mastery.greatsword>=RELIABLE", missingMastery.missingRequirement().orElseThrow());
        assertEquals(LearningRejectionReason.MISSING_WORLD_FLAG, missingFlag.reason());
    }

    @Test
    void learnedKnowledgeIsPermanentAndCannotBeGrantedTwice() {
        LearningDecision decision =
                engine.evaluate(
                        technique,
                        LearningRequirements.none(),
                        new KnowledgeProfile(Set.of(technique), Map.of(), Set.of()));

        assertFalse(decision.accepted());
        assertEquals(LearningRejectionReason.ALREADY_KNOWN, decision.reason());
    }

    private static KnowledgeKey key(KnowledgeType type, String id) {
        return new KnowledgeKey(type, DefinitionId.of(id));
    }
}
