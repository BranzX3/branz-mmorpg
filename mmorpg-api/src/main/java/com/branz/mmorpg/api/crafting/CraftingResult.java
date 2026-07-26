package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.economy.EconomyPaymentPort;

public record CraftingResult(
        CraftJob job,
        boolean outputDelivered,
        EconomyPaymentPort.Status paymentStatus,
        String detail) {
}
