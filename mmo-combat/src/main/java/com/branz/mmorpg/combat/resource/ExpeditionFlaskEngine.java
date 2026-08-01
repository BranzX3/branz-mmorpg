package com.branz.mmorpg.combat.resource;

import com.branz.mmorpg.api.result.Result;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Pure reusable-Flask consumption and Rest preparation authority. */
public final class ExpeditionFlaskEngine {
    public static final int MERCY_MINIMUM_CHARGES = 2;

    public Result<FlaskConsumption, FlaskErrorCode> consume(FlaskState current, FlaskDose dose) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(dose, "dose");
        if (current.charge(dose) == 0) {
            return Result.failure(
                    FlaskErrorCode.FLASK_CHARGE_UNAVAILABLE,
                    dose + " Flask charge is unavailable.");
        }
        EnumMap<FlaskDose, Integer> next = new EnumMap<>(FlaskDose.class);
        next.putAll(current.charges());
        next.put(dose, next.get(dose) - 1);
        return Result.success(
                new FlaskConsumption(
                        new FlaskState(current.allocation(), next),
                        FlaskRestoration.forDose(dose)));
    }

    public Result<FlaskPreparation, FlaskErrorCode> prepare(
            FlaskState current,
            FlaskAllocation desired,
            int availableInfusionStock,
            boolean mercyEligible) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(desired, "desired");
        if (availableInfusionStock < 0) {
            return Result.failure(
                    FlaskErrorCode.FLASK_STOCK_INVALID,
                    "Available Infusion Stock must not be negative.");
        }
        EnumMap<FlaskDose, Integer> charges = new EnumMap<>(FlaskDose.class);
        for (FlaskDose dose : FlaskDose.values()) {
            charges.put(dose, Math.min(current.charge(dose), desired.maximum(dose)));
        }
        int stock = availableInfusionStock;
        int consumed = 0;
        for (FlaskDose dose : FlaskDose.values()) {
            int missing = desired.maximum(dose) - charges.get(dose);
            int refill = Math.min(missing, stock);
            charges.put(dose, charges.get(dose) + refill);
            stock -= refill;
            consumed += refill;
        }
        int mercy = 0;
        if (mercyEligible) {
            int needed = Math.max(0, MERCY_MINIMUM_CHARGES - total(charges));
            for (FlaskDose dose : FlaskDose.values()) {
                int grant = Math.min(needed, desired.maximum(dose) - charges.get(dose));
                charges.put(dose, charges.get(dose) + grant);
                needed -= grant;
                mercy += grant;
            }
        }
        return Result.success(
                new FlaskPreparation(new FlaskState(desired, charges), consumed, mercy));
    }

    private static int total(Map<FlaskDose, Integer> charges) {
        return charges.values().stream().mapToInt(Integer::intValue).sum();
    }
}
