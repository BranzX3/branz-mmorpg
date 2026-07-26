package com.branz.mmorpg.api.character;

/** Idempotent class-progression transaction result. */
public record ClassProgressionMutationCommit(
        boolean applied,
        CharacterClassProgress before,
        CharacterClassProgress after) {
}
