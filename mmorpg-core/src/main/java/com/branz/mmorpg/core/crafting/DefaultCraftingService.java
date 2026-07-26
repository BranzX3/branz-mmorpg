package com.branz.mmorpg.core.crafting;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.CraftingRepository;
import com.branz.mmorpg.api.crafting.CraftingResult;
import com.branz.mmorpg.api.crafting.CraftingService;
import com.branz.mmorpg.api.crafting.ProfessionSnapshot;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.economy.EconomyPaymentPort;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultCraftingService implements CraftingService {
    private final CraftingRepository repository;
    private final EconomyPaymentPort economy;
    private final PlayerSessionService sessions;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private final CraftingEngine engine = new CraftingEngine();

    public DefaultCraftingService(CraftingRepository repository, EconomyPaymentPort economy,
                                  PlayerSessionService sessions,
                                  Supplier<ContentSnapshot> content, GameClock clock) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.economy = java.util.Objects.requireNonNull(economy, "economy");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ProfessionSnapshot profession(UUID playerId, ContentId professionId) {
        sessions.requirePlayable(playerId);
        return repository.profession(playerId, professionId);
    }

    @Override
    public java.util.Optional<CraftJob> activeJob(UUID playerId) {
        sessions.requirePlayable(playerId);
        return repository.activeJob(playerId);
    }

    @Override
    public CraftingResult begin(
            UUID playerId, ContentId recipeId, Set<String> stationTags,
            Set<ContentId> selectedCatalysts, OperationId operationId) {
        sessions.requirePlayable(playerId);
        ContentSnapshot snapshot = content.get();
        RecipeDefinition recipe = snapshot.recipes().get(recipeId);
        if (recipe == null) throw new IllegalArgumentException("unknown recipe " + recipeId);
        if (!stationTags.contains(recipe.stationTag())) {
            throw new IllegalStateException("WRONG_STATION");
        }
        if (!recipe.optionalCatalysts().keySet().containsAll(selectedCatalysts)) {
            throw new IllegalArgumentException("unknown catalyst selection");
        }
        recipe.professionId().ifPresent(id -> {
            ProfessionSnapshot profession = repository.profession(playerId, id);
            if (profession.level() < recipe.requiredProfessionLevel()) {
                throw new IllegalStateException("PROFESSION_LEVEL_TOO_LOW");
            }
        });
        Map<ContentId, Long> escrow = new HashMap<>(recipe.inputs());
        selectedCatalysts.forEach(id ->
                escrow.merge(id, recipe.optionalCatalysts().get(id), Math::addExact));
        Instant now = clock.now();
        var prepared = repository.prepare(
                playerId, recipe, snapshot.revision(), Map.copyOf(escrow),
                operationId, now, before -> engine.consume(before, escrow, now));
        if (prepared.job().status() != CraftJob.Status.PENDING_PAYMENT) {
            return existing(prepared.job());
        }
        return settle(prepared.job());
    }

    @Override
    public CraftingResult begin(
            UUID playerId, ContentId recipeId, Set<String> stationTags,
            Set<ContentId> selectedCatalysts) {
        long sequence = repository.allocateSequence(playerId, recipeId);
        OperationId operation = OperationId.of(
                "craft", recipeId.toString(), playerId, "sequence-" + sequence);
        return begin(playerId, recipeId, stationTags, selectedCatalysts, operation);
    }

    @Override
    public CraftingResult resumePayment(OperationId operationId) {
        CraftJob job = repository.job(operationId)
                .orElseThrow(() -> new IllegalArgumentException("unknown craft operation"));
        if (job.status() != CraftJob.Status.PENDING_PAYMENT) return existing(job);
        return settle(job);
    }

    @Override
    public CraftingResult complete(OperationId operationId) {
        CraftJob job = repository.job(operationId)
                .orElseThrow(() -> new IllegalArgumentException("unknown craft operation"));
        if (job.status() == CraftJob.Status.PENDING_PAYMENT) {
            CraftingResult resumed = settle(job);
            job = resumed.job();
            if (job.status() != CraftJob.Status.IN_PROGRESS) return resumed;
        }
        if (job.status() == CraftJob.Status.COMPLETE
                || job.status() == CraftJob.Status.CANCELLED) return existing(job);
        Instant now = clock.now();
        if (now.isBefore(job.readyAt().orElseThrow())) {
            return new CraftingResult(job, false, EconomyPaymentPort.Status.PAID,
                    "Craft is still in progress.");
        }
        ContentSnapshot snapshot = content.get();
        CraftJob finalJob = job;
        var committed = repository.finalizeCraft(
                operationId, job.professionId(), now,
                before -> engine.deliver(before, finalJob, snapshot, now),
                before -> awardProfession(before, finalJob, snapshot, now));
        return new CraftingResult(committed.job(), committed.applied(),
                EconomyPaymentPort.Status.PAID,
                committed.applied() ? "Craft completed." : "Craft already completed.");
    }

    private CraftingResult settle(CraftJob job) {
        EconomyPaymentPort.PaymentResult payment;
        if (job.coinFee() == 0) {
            payment = new EconomyPaymentPort.PaymentResult(
                    EconomyPaymentPort.Status.PAID, "No fee.", 0);
        } else {
            payment = economy.chargeCoins(job.playerId(), job.coinFee(),
                    job.recipeId().toString(), job.operationId());
        }
        if (payment.status() == EconomyPaymentPort.Status.INSUFFICIENT) {
            ContentSnapshot snapshot = content.get();
            Instant now = clock.now();
            CraftJob cancelled = repository.cancel(
                    job.operationId(), payment.detail(), now,
                    before -> engine.refund(
                            before, job.escrowedMaterials(), snapshot, now));
            return new CraftingResult(cancelled, false, payment.status(), payment.detail());
        }
        if (!payment.settled()) {
            return new CraftingResult(job, false, payment.status(), payment.detail());
        }
        Instant now = clock.now();
        CraftJob started = repository.markPaymentSettled(
                job.operationId(), now.plusMillis(job.durationMillis()), now);
        return new CraftingResult(started, false, payment.status(), payment.detail());
    }

    private static ProfessionSnapshot awardProfession(
            ProfessionSnapshot before, CraftJob job,
            ContentSnapshot snapshot, Instant now) {
        var definition = snapshot.professions().get(before.professionId());
        if (definition == null) throw new IllegalStateException(
                "missing profession definition " + before.professionId());
        ProfessionEngine engine = new ProfessionEngine(definition);
        long xp = engine.diminishedAward(
                job.professionXp(), before.level(), job.trivialAfterLevel());
        return engine.award(before, xp, now);
    }

    private static CraftingResult existing(CraftJob job) {
        return new CraftingResult(job, false,
                job.status() == CraftJob.Status.PENDING_PAYMENT
                        ? EconomyPaymentPort.Status.UNAVAILABLE
                        : EconomyPaymentPort.Status.ALREADY_PAID,
                "Existing craft state: " + job.status());
    }
}
