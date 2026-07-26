package com.branz.mmorpg.core.character;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassSelected;
import com.branz.mmorpg.api.character.CharacterClassSelectionRepository;
import com.branz.mmorpg.api.character.CharacterClassSelectionRequest;
import com.branz.mmorpg.api.character.CharacterClassSelectionResult;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.event.EventBus;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.player.RuntimePlayerSession;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** K0/K1 boundary for the one-time permanent class choice. */
public final class PermanentCharacterClassService {
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final CharacterClassSelectionRepository repository;
    private final EventBus events;
    private final GameClock clock;

    public PermanentCharacterClassService(
            PlayerSessionService sessions,
            ContentService content,
            CharacterClassSelectionRepository repository,
            EventBus events,
            GameClock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Blocking storage mutation; platform adapters must invoke it off the tick thread. */
    public CharacterClassSelectionResult select(CharacterClassSelectionRequest request) {
        Objects.requireNonNull(request, "request");
        RuntimePlayerSession session = sessions.requirePlayable(request.playerId());
        if (!session.token().equals(request.sessionToken())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "stale player session token");
        }
        if (!request.permanentChoiceConfirmed()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "permanent class choice requires explicit confirmation");
        }

        var replay = repository.find(request.playerId(), request.operationId());
        if (replay.isPresent()) {
            reconcile(session, replay.get());
            return replay.get();
        }

        ContentSnapshot snapshot = content.snapshot();
        if (snapshot.revision() != request.expectedContentRevision()) {
            throw new MMOException(ErrorCode.CONTENT_INVALID,
                    "stale class content revision: expected " + request.expectedContentRevision()
                            + " but active is " + snapshot.revision());
        }
        if (session.profile().revision() != request.expectedProfileRevision()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "stale profile revision: expected " + request.expectedProfileRevision()
                            + " but session is " + session.profile().revision());
        }
        if (session.profile().classId().isPresent()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "character class is permanent and already selected: "
                            + session.profile().classId().get());
        }
        CharacterClassDefinition definition = snapshot.find(
                        request.selectedClassId().value(), CharacterClassDefinition.class)
                .orElseThrow(() -> new MMOException(ErrorCode.CONTENT_INVALID,
                        "unknown character class " + request.selectedClassId()));
        Instant selectedAt = clock.now();
        CharacterClassSelectionResult result = repository.select(
                request.playerId(), request.expectedProfileRevision(), request.operationId(),
                definition, snapshot.revision(), selectedAt);
        reconcile(session, result);
        if (result.applied()) {
            events.publish(new CharacterClassSelected(
                    UUID.randomUUID(), selectedAt, request.playerId(), definition.classId(),
                    request.operationId(), snapshot.revision(),
                    definition.starterGrantPlan().revision()));
        }
        return result;
    }

    private static void reconcile(
            RuntimePlayerSession session, CharacterClassSelectionResult result) {
        session.acceptPersistedClass(result.snapshot().classId().orElseThrow().value(),
                result.snapshot().profileRevision());
    }
}
