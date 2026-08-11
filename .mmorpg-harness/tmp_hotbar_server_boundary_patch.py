from pathlib import Path

path = Path("mmo-bootstrap/src/main/java/com/branz/mmorpg/bootstrap/PhysicalInventoryInteractionController.java")
text = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 anchor, found {count}")
    text = text.replace(old, new, 1)

replace_once(
'''        UUID playerId = player.getUniqueId();
        PendingInteraction interaction = pending.get(playerId);
''',
'''        UUID playerId = player.getUniqueId();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_CLICK_HANDLER_SERVER player="
                                    + player.getName()
                                    + " action="
                                    + event.getAction()
                                    + " click="
                                    + event.getClick()
                                    + " rawSlot="
                                    + event.getRawSlot()
                                    + " slot="
                                    + event.getSlot()
                                    + " cancelled="
                                    + event.isCancelled()
                                    + " currentProjection="
                                    + hasProjection(event.getCurrentItem())
                                    + " cursorProjection="
                                    + hasProjection(player.getItemOnCursor()));
        }
        PendingInteraction interaction = pending.get(playerId);
''',
"handler-entry",
)

replace_once(
'''        scheduleObservation(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
''',
'''        scheduleObservation(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeHotbarAcceptanceClick(InventoryClickEvent event) {
        if (!Boolean.getBoolean("mmo.physical-hotbar-acceptance")
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.getLogger()
                .info(
                        "PHYSICAL_AUTHORITY_HOTBAR_CLICK_MONITOR_SERVER player="
                                + player.getName()
                                + " action="
                                + event.getAction()
                                + " click="
                                + event.getClick()
                                + " rawSlot="
                                + event.getRawSlot()
                                + " slot="
                                + event.getSlot()
                                + " cancelled="
                                + event.isCancelled()
                                + " sameInventory="
                                + (event.getClickedInventory() == player.getInventory())
                                + " currentProjection="
                                + hasProjection(event.getCurrentItem())
                                + " eventCursorProjection="
                                + hasProjection(event.getCursor())
                                + " playerCursorProjection="
                                + hasProjection(player.getItemOnCursor()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
''',
"monitor-handler",
)

replace_once(
'''        PhysicalInventoryObservation observed =
                ((Result.Success<PhysicalInventoryObservation, ProjectionMoveErrorCode>)
                                observation)
                        .value();
        Result<ProjectionMovePlan, ProjectionMoveErrorCode> planned =
''',
'''        PhysicalInventoryObservation observed =
                ((Result.Success<PhysicalInventoryObservation, ProjectionMoveErrorCode>)
                                observation)
                        .value();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_OBSERVATION_SERVER player="
                                    + player.getName()
                                    + " storageCount="
                                    + observed.storage().size()
                                    + " cursorPresent="
                                    + observed.cursor().isPresent());
        }
        Result<ProjectionMovePlan, ProjectionMoveErrorCode> planned =
''',
"observation",
)

replace_once(
'''        if (planned
                instanceof Result.Failure<ProjectionMovePlan, ProjectionMoveErrorCode> failure) {
            abortInteraction(playerId, failure.error().code() + ": " + failure.detail());
            return;
        }
        ProjectionMovePlan plan =
                ((Result.Success<ProjectionMovePlan, ProjectionMoveErrorCode>) planned).value();
''',
'''        if (planned
                instanceof Result.Failure<ProjectionMovePlan, ProjectionMoveErrorCode> failure) {
            if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
                plugin.getLogger()
                        .severe(
                                "PHYSICAL_AUTHORITY_HOTBAR_PLAN_FAILED_SERVER player="
                                        + player.getName()
                                        + " code="
                                        + failure.error().code()
                                        + " detail="
                                        + failure.detail());
            }
            abortInteraction(playerId, failure.error().code() + ": " + failure.detail());
            return;
        }
        ProjectionMovePlan plan =
                ((Result.Success<ProjectionMovePlan, ProjectionMoveErrorCode>) planned).value();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_PLAN_SERVER player="
                                    + player.getName()
                                    + " disposition="
                                    + plan.disposition()
                                    + " intent="
                                    + plan.intent()
                                            .map(
                                                    intent ->
                                                            intent.sourceSlot()
                                                                    + "->"
                                                                    + intent.destinationSlot()
                                                                    + ":"
                                                                    + intent.valueId())
                                            .orElse("none"));
        }
''',
"planner",
)

replace_once(
'''    private void commit(
            Player player, PendingInteraction interaction, ProjectionMoveIntent intent) {
        UUID playerId = player.getUniqueId();
''',
'''    private void commit(
            Player player, PendingInteraction interaction, ProjectionMoveIntent intent) {
        UUID playerId = player.getUniqueId();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_COMMIT_BEGIN_SERVER player="
                                    + player.getName()
                                    + " source="
                                    + intent.sourceSlot()
                                    + " destination="
                                    + intent.destinationSlot()
                                    + " value="
                                    + intent.valueId());
        }
''',
"commit-begin",
)

path.write_text(text, encoding="utf-8")
