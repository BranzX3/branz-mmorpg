package com.branz.mmorpg.bootstrap;

enum DurableFlaskUsePhase {
    WINDUP,
    COMMITTING,
    RECOVERY,
    COMPLETE,
    CANCELLED_BEFORE_COMMIT,
    INTERRUPTED_AFTER_COMMIT,
    COMMIT_FAILED;

    boolean terminal() {
        return this == COMPLETE
                || this == CANCELLED_BEFORE_COMMIT
                || this == INTERRUPTED_AFTER_COMMIT
                || this == COMMIT_FAILED;
    }
}
