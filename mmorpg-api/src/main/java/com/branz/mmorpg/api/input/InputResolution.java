package com.branz.mmorpg.api.input;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Optional;

public record InputResolution(Outcome outcome, Optional<SkillSlot> slot,
                              Optional<ContentId> comboId,
                              Optional<ContentId> skillId, String rejection) {
    public enum Outcome { SLOT, COMBO_ADVANCED, COMBO_RESOLVED, REJECTED }

    public InputResolution {
        slot = slot == null ? Optional.empty() : slot;
        comboId = comboId == null ? Optional.empty() : comboId;
        skillId = skillId == null ? Optional.empty() : skillId;
    }
}
