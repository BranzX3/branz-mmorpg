package com.branz.mmorpg.lifeskills.progression;

import com.branz.mmorpg.api.result.Result;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/** Applies already-approved committed-work evidence against an authored rank table. */
public final class LifeskillRankEngine {
    private final LifeskillRankTable table;

    public LifeskillRankEngine(LifeskillRankTable table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public Result<LifeskillRankDecision, LifeskillProgressionErrorCode> applyCommittedEvidence(
            LifeskillRankRuntime runtime, double evidence, UUID operationId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(operationId, "operationId");
        if (!runtime.rank().equals(table.rankAt(runtime.evidence()))) {
            return Result.failure(
                    LifeskillProgressionErrorCode.RUNTIME_INVALID,
                    "Stored lifeskill rank does not match its authored evidence table.");
        }
        if (!Double.isFinite(evidence) || evidence <= 0) {
            return Result.failure(
                    LifeskillProgressionErrorCode.EVIDENCE_INVALID,
                    "Committed lifeskill evidence must be finite and positive.");
        }
        Double previousAmount = runtime.processedOperations().get(operationId);
        if (previousAmount != null) {
            if (Double.compare(previousAmount, evidence) != 0) {
                return Result.failure(
                        LifeskillProgressionErrorCode.OPERATION_ID_REUSED,
                        "Lifeskill evidence operation was reused with different input.");
            }
            return Result.success(new LifeskillRankDecision(runtime, runtime.rank(), 0, true));
        }
        double resultingEvidence =
                Math.min(table.maximumEffectiveEvidence(), runtime.evidence() + evidence);
        double awardedEvidence = resultingEvidence - runtime.evidence();
        if (awardedEvidence <= 0) {
            return Result.failure(
                    LifeskillProgressionErrorCode.EVIDENCE_INVALID,
                    "Grandmaster V cannot receive additional effective rank evidence.");
        }
        HashMap<UUID, Double> operations = new HashMap<>(runtime.processedOperations());
        operations.put(operationId, evidence);
        LifeskillRankRuntime next =
                new LifeskillRankRuntime(
                        runtime.discipline(),
                        resultingEvidence,
                        table.rankAt(resultingEvidence),
                        operations);
        return Result.success(
                new LifeskillRankDecision(next, runtime.rank(), awardedEvidence, false));
    }
}
