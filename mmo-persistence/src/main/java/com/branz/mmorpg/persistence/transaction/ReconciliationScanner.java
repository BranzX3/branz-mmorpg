package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.result.Result;
import java.time.Duration;

public interface ReconciliationScanner {
    Result<ReconciliationReport, ReconciliationErrorCode> scan(
            Duration stalePreparedAge, int limit);
}
