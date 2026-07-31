package com.branz.mmorpg.persistence.lease;

import java.util.Objects;

public sealed interface LeaseAcquireOutcome
        permits LeaseAcquireOutcome.Acquired,
                LeaseAcquireOutcome.AlreadyHeld,
                LeaseAcquireOutcome.Conflict,
                LeaseAcquireOutcome.RecoveryRequired {
    CharacterLease lease();

    record Acquired(CharacterLease lease) implements LeaseAcquireOutcome {
        public Acquired {
            Objects.requireNonNull(lease, "lease");
        }
    }

    record AlreadyHeld(CharacterLease lease) implements LeaseAcquireOutcome {
        public AlreadyHeld {
            Objects.requireNonNull(lease, "lease");
        }
    }

    record Conflict(CharacterLease lease) implements LeaseAcquireOutcome {
        public Conflict {
            Objects.requireNonNull(lease, "lease");
        }
    }

    record RecoveryRequired(CharacterLease lease) implements LeaseAcquireOutcome {
        public RecoveryRequired {
            Objects.requireNonNull(lease, "lease");
        }
    }
}
