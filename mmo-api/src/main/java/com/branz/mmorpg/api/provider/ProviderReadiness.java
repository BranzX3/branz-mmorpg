package com.branz.mmorpg.api.provider;

/** Aggregate server readiness derived from provider requirements and health. */
public enum ProviderReadiness {
    READY,
    DEGRADED,
    MAINTENANCE
}
