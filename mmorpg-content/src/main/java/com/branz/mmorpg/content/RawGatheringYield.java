package com.branz.mmorpg.content;

import java.util.List;

record RawGatheringYield(
        String item,
        List<Long> amount,
        Double chance) {
}
