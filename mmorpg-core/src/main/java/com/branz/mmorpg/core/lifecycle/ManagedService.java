package com.branz.mmorpg.core.lifecycle;

public interface ManagedService {
    String name();

    boolean required();

    void start() throws Exception;

    void stop() throws Exception;

    default String detail() {
        return "";
    }
}
