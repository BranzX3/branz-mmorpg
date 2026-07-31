package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;

public interface AuditLogRepository {
    Result<List<AuditLogEntry>, TransactionErrorCode> findByTransaction(
            TransactionId transactionId);
}
