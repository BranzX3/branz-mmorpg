package com.branz.mmorpg.content.manifest;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ContentManifestErrorCode implements ErrorCode {
    CONTENT_MANIFEST_NOT_FOUND,
    CONTENT_MANIFEST_INVALID_JSON,
    CONTENT_MANIFEST_INVALID_FIELD,
    CONTENT_MANIFEST_IO;

    @Override
    public String code() {
        return name();
    }
}
