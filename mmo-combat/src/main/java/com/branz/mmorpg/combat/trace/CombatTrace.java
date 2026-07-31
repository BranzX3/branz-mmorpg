package com.branz.mmorpg.combat.trace;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTraceEvent;
import com.branz.mmorpg.combat.action.CombatResources;
import java.util.List;
import java.util.Objects;

public record CombatTrace(
        String contentVersion,
        DefinitionId moveId,
        CombatResources initialResources,
        List<ActionSimulationCommand> commands,
        List<ActionTraceEvent> events,
        CombatResources finalResources,
        ActionPhase finalPhase) {
    public CombatTrace {
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(moveId, "moveId");
        Objects.requireNonNull(initialResources, "initialResources");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(finalResources, "finalResources");
        Objects.requireNonNull(finalPhase, "finalPhase");
    }

    public String canonicalExport() {
        StringBuilder output = new StringBuilder();
        output.append("content=").append(contentVersion).append('\n');
        output.append("move=").append(moveId.value()).append('\n');
        output.append("initial=").append(resources(initialResources)).append('\n');
        for (ActionSimulationCommand command : commands) {
            output.append("command=")
                    .append(command.tick())
                    .append('|')
                    .append(command.type())
                    .append('|')
                    .append(command.detail())
                    .append('\n');
        }
        for (ActionTraceEvent event : events) {
            output.append("event=")
                    .append(event.tick())
                    .append('|')
                    .append(event.type())
                    .append('|')
                    .append(event.detail())
                    .append('\n');
        }
        output.append("final=").append(resources(finalResources)).append('\n');
        output.append("phase=").append(finalPhase).append('\n');
        return output.toString();
    }

    private static String resources(CombatResources resources) {
        return resources.health()
                + "/"
                + resources.maximumHealth()
                + ","
                + resources.stamina()
                + "/"
                + resources.maximumStamina()
                + ","
                + resources.mana()
                + "/"
                + resources.maximumMana()
                + ",reserved="
                + resources.reservedHealth()
                + "/"
                + resources.reservedStamina()
                + "/"
                + resources.reservedMana();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
