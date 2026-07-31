package com.branz.mmorpg.combat.action;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ActionTimelineErrorCode implements ErrorCode {
    NO_STAMINA,
    NO_MANA,
    HEALTH_COST_LETHAL,
    ACTION_ALREADY_FINISHED;

    @Override
    public String code() {
        return name();
    }
}
