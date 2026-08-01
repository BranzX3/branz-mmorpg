package com.branz.mmorpg.bootstrap;

record DurableConsumableUseTransition(DurableConsumableUseState state, boolean commitNow) {}
