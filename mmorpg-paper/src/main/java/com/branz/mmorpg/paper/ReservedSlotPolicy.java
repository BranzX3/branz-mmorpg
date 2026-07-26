package com.branz.mmorpg.paper;

/** Pure decision table for lossless slot-9 reconciliation. */
final class ReservedSlotPolicy {
    enum Action {
        REFRESH_VALID_TOKEN,
        REPLACE_INVALID_TOKEN,
        PLACE_TOKEN,
        RELOCATE_NORMAL_ITEM,
        PERSIST_NORMAL_ITEM
    }

    record Decision(Action action, int destinationSlot) {
        Decision {
            if (action == Action.RELOCATE_NORMAL_ITEM && destinationSlot < 0) {
                throw new IllegalArgumentException("relocation requires a destination slot");
            }
            if (action != Action.RELOCATE_NORMAL_ITEM && destinationSlot != -1) {
                throw new IllegalArgumentException("only relocation has a destination slot");
            }
        }
    }

    Decision decide(boolean empty, boolean uiToken, boolean validToken, int freeSlot) {
        if (uiToken) {
            return new Decision(validToken ? Action.REFRESH_VALID_TOKEN
                    : Action.REPLACE_INVALID_TOKEN, -1);
        }
        if (empty) return new Decision(Action.PLACE_TOKEN, -1);
        if (freeSlot >= 0) {
            return new Decision(Action.RELOCATE_NORMAL_ITEM, freeSlot);
        }
        return new Decision(Action.PERSIST_NORMAL_ITEM, -1);
    }
}
