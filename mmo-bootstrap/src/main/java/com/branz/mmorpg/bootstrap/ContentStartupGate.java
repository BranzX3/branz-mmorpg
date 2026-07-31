package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.provider.ProviderHealthEntry;
import com.branz.mmorpg.api.provider.ProviderHealthReport;
import com.branz.mmorpg.api.provider.ProviderReadiness;
import com.branz.mmorpg.api.provider.ProviderRegistry;
import com.branz.mmorpg.api.provider.ProviderStatus;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Evaluates immutable content and provider capabilities before character sessions may activate. */
final class ContentStartupGate {
    private final ContentSnapshotSource contentSource;

    ContentStartupGate() {
        this(new ContentSnapshotLoader()::load);
    }

    ContentStartupGate(ContentSnapshotSource contentSource) {
        this.contentSource = Objects.requireNonNull(contentSource, "contentSource");
    }

    StartupDecision evaluate(
            Path contentRoot, ProviderRegistryFactory providerFactory, Instant checkedAt) {
        Objects.requireNonNull(contentRoot, "contentRoot");
        Objects.requireNonNull(providerFactory, "providerFactory");
        Objects.requireNonNull(checkedAt, "checkedAt");
        Result<ContentSnapshot, ContentLoadFailure> loaded;
        try {
            loaded = Objects.requireNonNull(contentSource.load(contentRoot), "content result");
        } catch (RuntimeException exception) {
            return new StartupDecision(
                    StartupStatus.MAINTENANCE,
                    Optional.empty(),
                    ProviderRegistry.empty().healthReport(checkedAt),
                    List.of("Content loading failed: " + exception.getClass().getSimpleName()));
        }
        if (!(loaded instanceof Result.Success<ContentSnapshot, ContentLoadFailure> success)) {
            ContentLoadFailure failure =
                    ((Result.Failure<ContentSnapshot, ContentLoadFailure>) loaded).error();
            List<String> reasons =
                    failure.diagnostics().stream()
                            .map(ContentStartupGate::diagnosticReason)
                            .toList();
            return new StartupDecision(
                    StartupStatus.MAINTENANCE,
                    Optional.empty(),
                    ProviderRegistry.empty().healthReport(checkedAt),
                    reasons);
        }

        ContentSnapshot snapshot = success.value();
        ProviderRegistry providers;
        try {
            providers = Objects.requireNonNull(providerFactory.create(snapshot), "providers");
        } catch (RuntimeException exception) {
            return new StartupDecision(
                    StartupStatus.MAINTENANCE,
                    Optional.of(snapshot),
                    ProviderRegistry.empty().healthReport(checkedAt),
                    List.of(
                            "Provider configuration failed: "
                                    + exception.getClass().getSimpleName()));
        }

        ProviderHealthReport report = providers.healthReport(checkedAt);
        StartupStatus status =
                switch (report.readiness()) {
                    case READY -> StartupStatus.READY;
                    case DEGRADED -> StartupStatus.DEGRADED;
                    case MAINTENANCE -> StartupStatus.MAINTENANCE;
                };
        List<String> reasons = unhealthyReasons(report);
        return new StartupDecision(status, Optional.of(snapshot), report, reasons);
    }

    private static List<String> unhealthyReasons(ProviderHealthReport report) {
        if (report.readiness() == ProviderReadiness.READY) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (ProviderHealthEntry entry : report.providers()) {
            if (entry.status() != ProviderStatus.HEALTHY) {
                reasons.add(
                        entry.providerId() + " [" + entry.requirement() + "]: " + entry.message());
            }
        }
        return List.copyOf(reasons);
    }

    private static String diagnosticReason(ContentDiagnostic diagnostic) {
        return diagnostic.code().code()
                + " at "
                + diagnostic.source()
                + ":"
                + diagnostic.line()
                + ":"
                + diagnostic.column()
                + " - "
                + diagnostic.explanation();
    }
}
