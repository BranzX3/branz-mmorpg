from pathlib import Path

path = Path("docs/39-implementation-roadmap.md")
text = path.read_text(encoding="utf-8")
old = '''1. **Local Character Scene and equipment transaction — `AUTOMATED_VERIFIED`.** The world-backed
   Scene, preview/commit/cancel, input/interruption ownership, authoritative equipment projection,
   stale-session rejection and reconnect/restart persistence have automated coverage. Production
   navigation now consumes the canonical Scene policy, Rest-only workflows are context-gated and a
   new Scene cannot snapshot state while an authoritative value mutation is in flight. Real local
   client acceptance, including interruption plus reconnect/restart, remains required before this
   feature becomes `COMPLETE`.
2. **Training Sword combat loop — `NOT_STARTED` (blocked by feature 1).** A player with an owned and
   equipped sword must draw, attack, hit/miss, defend, take MMO damage, die/respawn and reconnect
   without using a command as the combat runtime. Dev tooling may grant the test-owned sword.
3. **Additional weapon families, magic, progression, consumables, encounters/social and
   lifeskills — `NOT_STARTED` for delivery purposes.** Existing kernels and automated coverage will
   be audited and completed one player-facing feature at a time; historical Milestone 0–8 labels
   do not waive live acceptance.
'''
new = '''1. **Physical gameplay item authority refactor — `IN_PROGRESS`.** Replace the temporary
   `MAIN_HAND`/Scene-deck interaction model with the authored physical model from ADR 0025: weapons
   and consumables live in hotbar 1–8, selected hotbar weapon drives combat authority, shield/armor
   use native physical slots, Chronicle owns build/virtual/cosmetic state, and production world mobs
   never use training-only hidden health. Migrate legacy main-hand locations idempotently while
   preserving item UUID/payload/durability and reconnect/restart truth.
2. **Local Character Scene/build configuration — `NOT_STARTED` for renewed acceptance (blocked by
   feature 1).** Preserve the already-tested world-backed Scene lifecycle/recovery, but remove
   ordinary weapon/shield/native-armor/consumable equip from the Scene acceptance target. Re-verify
   Chronicle after its Character & Equipment page becomes inspection plus virtual/build management.
3. **Training Sword physical combat loop — `NOT_STARTED` (blocked by features 1–2).** A player with
   a dev-granted MMO-owned sword must move it to any hotbar slot 1–8, select/draw it, hit/miss, kill
   an ordinary world cow through one authoritative health path, defend with a physical off-hand
   shield, take MMO damage, die/respawn and reconnect without a Chronicle weapon-equip transaction.
4. **Additional weapon families, magic, progression, consumables, encounters/social and
   lifeskills — `NOT_STARTED` for delivery purposes.** Existing kernels and automated coverage will
   be audited and completed one player-facing feature at a time; historical Milestone 0–8 labels
   do not waive live acceptance.
'''
if old not in text:
    raise SystemExit("roadmap queue block changed; refusing broad rewrite")
text = text.replace(old, new, 1)
old_rest = "Atomic Rest allocation/refill is now available through Chronicle with exact Infusion Stock CAS,"
new_rest = "Atomic Rest allocation/refill persistence is implemented with exact Infusion Stock CAS; its player-facing preparation entry is being moved from Chronicle to the Rest interaction under ADR 0025,"
if old_rest not in text:
    raise SystemExit("Milestone 6 Rest sentence changed; refusing broad rewrite")
text = text.replace(old_rest, new_rest, 1)
path.write_text(text, encoding="utf-8")
