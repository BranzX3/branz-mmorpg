package com.branz.mmorpg.content.diagnostic;

import com.branz.mmorpg.api.result.ErrorCode;

/** Stable validation codes defined by docs/43-content-authoring-tools.md. */
public enum ContentDiagnosticCode implements ErrorCode {
    CONTENT_SCHEMA_REQUIRED_FIELD,
    CONTENT_REFERENCE_NOT_FOUND,
    CONTENT_REFERENCE_WRONG_TYPE,
    CONTENT_ID_DUPLICATE,
    CONTENT_ALIAS_CYCLE,
    CONTENT_BUDGET_EXCEEDED,
    CONTENT_LOCALIZATION_MISSING,
    CONTENT_ASSET_NOT_FOUND,
    CONTENT_RECIPE_CYCLE,
    CONTENT_UNREACHABLE_NODE,
    CONTENT_MIGRATION_REQUIRED;

    @Override
    public String code() {
        return name();
    }
}
