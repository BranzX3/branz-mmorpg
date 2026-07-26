package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.economy.EconomyPaymentPort;
import com.branz.mmorpg.api.operation.OperationId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        Object wallet = wallet();
        if (wallet == null) {
            throw new IllegalStateException("BranzWallet service is unavailable");
        }
        if (amount == 0 || operationId == null || operationId.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "amount, operation ID, and reason are required");
        }
        return (boolean) invoke(wallet, "adjustCredit",
                new Class<?>[]{UUID.class, long.class, String.class, String.class, String.class},
                playerId, amount, "MMORPG_ADMIN", operationId, reason);
    }

    @Override
    public long coins(UUID playerId) {
        Object wallet = wallet();
        if (wallet == null) throw new IllegalStateException("BranzWallet is unavailable");
        return ((Number) invoke(wallet, "coins", new Class<?>[]{UUID.class}, playerId)).longValue();
    }

    @Override
    public PaymentResult chargeCoins(
            UUID playerId, long amount, String purchaseId, OperationId operationId) {
        if (amount < 0) throw new IllegalArgumentException("negative Coin charge");
        if (amount == 0) return new PaymentResult(Status.PAID, "No fee.", 0);
        Object wallet = wallet();
        if (wallet == null) {
            return new PaymentResult(Status.UNAVAILABLE,
                    "BranzWallet is unavailable; craft remains pending.", 0);
        }
        try {
            Object checkout = invoke(wallet, "hybridPay",
                    new Class<?>[]{UUID.class, long.class, long.class, String.class, String.class},
                    playerId, amount, 0L, purchaseId, operationId.value());
            boolean successful = (boolean) invoke(checkout, "success", new Class<?>[0]);
            String message = (String) invoke(checkout, "message", new Class<?>[0]);
            if (successful) {
                return new PaymentResult(
                        Status.PAID, message,
                        ((Number) invoke(checkout, "coinsCharged", new Class<?>[0])).longValue());
            }
            String detail = message == null ? "" : message;
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

    private Object wallet() {
        try {
            Class<?> walletApi = Class.forName(
                    "dev.branzx.wallet.api.WalletApi", false, plugin.getClass().getClassLoader());
            return plugin.getServer().getServicesManager().load(walletApi);
        } catch (ClassNotFoundException unavailable) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("BranzWallet call failed: " + methodName, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Incompatible BranzWallet API: " + methodName, exception);
        }
    }
}
