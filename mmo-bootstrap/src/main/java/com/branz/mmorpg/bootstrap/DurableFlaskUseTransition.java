package com.branz.mmorpg.bootstrap;

record DurableFlaskUseTransition(DurableFlaskUseState state, boolean commitNow) {}
