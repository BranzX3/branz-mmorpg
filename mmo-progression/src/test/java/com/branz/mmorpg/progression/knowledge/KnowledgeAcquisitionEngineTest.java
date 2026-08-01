package com.branz.mmorpg.progression.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeAcquisitionEngineTest {
    private static final KnowledgeKey CINDER =
            new KnowledgeKey(KnowledgeType.SPELL, DefinitionId.of("spell.ember.cinder_snap"));
    private static final KnowledgeKey FIRE_LANCE =
            new KnowledgeKey(KnowledgeType.SPELL, DefinitionId.of("spell.ember.fire_lance"));
    private static final DefinitionId SOURCE = DefinitionId.of("discovery.ember.fire_lance_rune");
    private final KnowledgeAcquisitionEngine engine = new KnowledgeAcquisitionEngine();
    private final KnowledgeAcquisitionPolicy policy =
            new KnowledgeAcquisitionPolicy(
                    FIRE_LANCE,
                    KnowledgeAcquisitionSourceType.WORLD_DISCOVERY,
                    SOURCE,
                    new LearningRequirements(
                            Set.of(CINDER),
                            Map.of(ProgressionTrack.mastery("staff"), ReadinessBand.DEVELOPING),
                            Set.of()));

    @Test
    void sourceIdentityMustMatchBeforePrerequisitesAreEvaluated() {
        KnowledgeProfile ready =
                new KnowledgeProfile(
                        Set.of(CINDER),
                        Map.of(ProgressionTrack.mastery("staff"), ReadinessBand.RELIABLE),
                        Set.of());

        LearningDecision wrongSource =
                engine.evaluate(
                        policy,
                        KnowledgeAcquisitionSourceType.MENTOR,
                        DefinitionId.of("mentor.ember.fire_lance"),
                        ready);
        LearningDecision accepted =
                engine.evaluate(
                        policy, KnowledgeAcquisitionSourceType.WORLD_DISCOVERY, SOURCE, ready);

        assertEquals(LearningRejectionReason.ACQUISITION_SOURCE_MISMATCH, wrongSource.reason());
        assertTrue(accepted.accepted());
    }

    @Test
    void authoredKnowledgeAndQualitativeReadinessStillGateTheGrant() {
        LearningDecision missingKnowledge =
                engine.evaluate(
                        policy,
                        KnowledgeAcquisitionSourceType.WORLD_DISCOVERY,
                        SOURCE,
                        new KnowledgeProfile(
                                Set.of(),
                                Map.of(
                                        ProgressionTrack.mastery("staff"),
                                        ReadinessBand.EXCEPTIONAL),
                                Set.of()));
        LearningDecision missingReadiness =
                engine.evaluate(
                        policy,
                        KnowledgeAcquisitionSourceType.WORLD_DISCOVERY,
                        SOURCE,
                        new KnowledgeProfile(Set.of(CINDER), Map.of(), Set.of()));

        assertEquals(LearningRejectionReason.MISSING_KNOWLEDGE, missingKnowledge.reason());
        assertEquals(LearningRejectionReason.MASTERY_NOT_READY, missingReadiness.reason());
    }
}
