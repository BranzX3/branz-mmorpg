package com.branz.mmorpg.scenes.overlay;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneSession;

/** Inventory or dialogue control surface layered over a world-backed Scene. */
public interface MenuOverlay {
    Result<MenuOverlayHandle, SceneErrorCode> open(SceneSession session);

    Result<MenuOverlayHandle, SceneErrorCode> update(
            MenuOverlayHandle handle, SceneSession session);

    void close(MenuOverlayHandle handle);
}
