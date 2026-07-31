package com.branz.mmorpg.api.provider;

import java.time.Instant;

public interface ClockProvider extends Provider {
    Instant now();
}
