package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.economy.EconomyPaymentPort;
import com.branz.mmorpg.api.operation.OperationId;
import dev.branzx.wallet.api.Checkout;
import dev.branzx.wallet.api.WalletApi;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

/** The only module boundary that imports BranzWallet. All calls are blocking. */
public final class PaperWalletEconomyAdapter implements EconomyPaymentPort,
        com.branz.mmorpg.api.economy.AdminCurrencyPort {
    private final JavaPlugin plugin;

    public PaperWalletEconomyAdapter(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override public boolean adjustCredits(
            UUID playerId, long amount, String operationId, String reason) {
        WalletApi wallet = plugin.getServer().getServicesManager().load(WalletApi.class);
        if (wallet == null) {
            throw new IllegalStateException("BranzWallet service is unavailable");
        }
        if (amount == 0 || operationId == null || operationId.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "amount, operation ID, and reason are required");
        }
        return wallet.adjustCredit(playerId, amount, "MMORPG_ADMIN",
                operationId, reason);
    }

    @Override
    public long coins(UUID playerId) {
        WalletApi wallet = wallet();
        if (wallet == null) throw new IllegalStateException("BranzWallet is unavailable");
        return wallet.coins(playerId);
    }

    @Override
    public PaymentResult chargeCoins(
            UUID playerId, long amount, String purchaseId, OperationId operationId) {
        if (amount < 0) throw new IllegalArgumentException("negative Coin charge");
        if (amount == 0) return new PaymentResult(Status.PAID, "No fee.", 0);
        WalletApi wallet = wallet();
        if (wallet == null) {
            return new PaymentResult(Status.UNAVAILABLE,
                    "BranzWallet is unavailable; craft remains pending.", 0);
        }
        try {
            Checkout checkout = wallet.hybridPay(
                    playerId, amount, 0, purchaseId, operationId.value());
            if (checkout.success()) {
                return new PaymentResult(
                        Status.PAID, checkout.message(), checkout.coinsCharged());
            }
            String detail = checkout.message() == null ? "" : checkout.message();
            String normalized = detail.toLowerCase(Locale.ROOT);
            if (normalized.contains("already processed")) {
                return new PaymentResult(Status.ALREADY_PAID, detail, amount);
            }
            if (normalized.contains("not enough")) {
                return new PaymentResult(Status.INSUFFICIENT, detail, 0);
            }
            return new PaymentResult(Status.FAILED, detail, 0);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("BranzWallet checkout failed: " + failure.getMessage());
            return new PaymentResult(Status.UNAVAILABLE,
                    "Wallet checkout failed; craft remains pending.", 0);
        }
    }

    private WalletApi wallet() {
        return plugin.getServer().getServicesManager().load(WalletApi.class);
    }
}
