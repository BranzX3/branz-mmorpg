package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.Set;
import java.util.UUID;

public interface CraftingService {
    ProfessionSnapshot profession(UUID playerId, ContentId professionId);

    java.util.Optional<CraftJob> activeJob(UUID playerId);

    CraftingResult begin(UUID playerId, ContentId recipeId, Set<String> stationTags,
                         Set<ContentId> selectedCatalysts, OperationId operationId);

    CraftingResult begin(UUID playerId, ContentId recipeId, Set<String> stationTags,
                         Set<ContentId> selectedCatalysts);

    CraftingResult resumePayment(OperationId operationId);

    CraftingResult complete(OperationId operationId);
}
