# Reference Quest QA Path — `branz:the_old_seal`

## Content checks

- Stand at the desired local test origin and run `/branz quest bootstrap`.
- `/branz quest validate` succeeds with no diagnostics.
- `/branz quest graph branz:the_old_seal` shows:
  `meet_keeper -> enter_ruins -> gather_key -> break_seal`.
- `en_us.yml` contains every title, description, speaker, line, choice, and
  cutscene text key.
- NPC `branz:seal_keeper`, region `branz:old_ruins_gate`, and world object
  `branz:seal_pedestal` are captured/bound and survive restart.

## Play path

1. Talk to the Seal Keeper. The quest starts automatically and the same event
   completes `speak_keeper`; one dialogue session appears.
2. Select both dialogue branches in separate test runs. Double-click one choice
   and confirm only the first expected sequence advances.
3. Enter the old ruins region. Confirm credit occurs once on entry.
4. Interact with the seal pedestal. Confirm the cutscene freezes input, applies
   invulnerability, displays text/sound alternatives, and restores camera,
   movement, actors, and invulnerability after normal completion.
5. Repeat and skip at each timeline boundary. Every skip reaches the same
   canonical signal and cleanup.
6. Defeat the guardian with a nearby party member. Only the frozen in-range
   party snapshot gets kill credit. A late joiner gets none.
7. Possess at least three `branz:aether_ore`. Canonical possession is recognized
   immediately when the stage activates. Completion consumes exactly three via
   one durable operation.
8. Complete `branz:seal_guardian_encounter`. Only encounter-eligible
   contributors receive boss objective credit.
9. Turn in. Five ore are delivered once; overflow enters mailbox. Retry/rejoin
   never duplicates the reward.

## Recovery matrix

- Disconnect during dialogue: the session pauses and resumes with a new
  sequence.
- Disconnect/restart during cutscene: skip/final recovery and cleanup run
  idempotently.
- Restart after progress commit but before reward: pending action resumes.
- Reload a safe text-only version: active progress migrates without loss.
- Reload an incompatible mapped version: progress requires audited migration.
- Database unavailable: no objective, reward, item, or currency mutation is
  invented.

## Accessibility

Test MANUAL, AUTO, and FAST dialogue modes, text speed bounds, sound text
alternatives, cutscene skip, and readable state without relying on color.
