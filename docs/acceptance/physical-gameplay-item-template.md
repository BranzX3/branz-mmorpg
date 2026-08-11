# Physical Gameplay Item Acceptance Evidence

Accepted runtime revision: `0851f599caf8565d78338a53c9917f9c982d6f4a`  
Content version: `<version>`  
Acceptance date/time: `<local timestamp>`  
Player UUID: `<uuid>`  
Character ID: `<uuid>`

Do not replace `Accepted runtime revision` with the later documentation/evidence commit SHA. If
runtime source, migrations, configuration defaults or active content change, create a new evidence
record for the new runtime revision and rerun A-F.

## Environment fingerprint

Follow `physical-gameplay-item-environment.md` before A0.

- Paper startup identity: `<Minecraft version + Paper build + Paper commit>`
- Java runtime identity: `<java -version vendor/version/build>`
- effective `JAVA_HOME`: `<path or stable machine-local identifier>`
- Minecraft client version: `<version>`
- acceptance machine/OS: `<stable identity>`
- same fingerprint verified at A1 boot: `<PASS|FAIL>`
- same fingerprint verified at every required restart: `<PASS|FAIL + notes>`

If this fingerprint changes anywhere in the pass, this evidence record is invalid. Do not combine
results from different Paper builds, Java runtimes, client versions or acceptance machines; restart
A-F from A0.

## A. Legacy MAIN_HAND migration

- legacy seed revision: `8c5a04271f9385730aff0b3332608812a216dc95`
- value definition: `weapon.training_sword`
- legacy `/paper dumpitem` full projection value UUID: `<uuid>`
- legacy projection authority version: `<version>`
- legacy projection content version: `<version>`
- pre authoritative state: `NATIVE_EQUIPPED/MAIN_HAND`, durability baseline `120/120`
- target `/mmo physical status` line: `<full stable line>`
- post authoritative location: `<CHARACTER_INVENTORY/slot:n>`
- post version: `<must equal legacy authority version + 1>`
- post durability: `<must be 120/120>`
- reconnect inspector result: `<PASS|FAIL + evidence>`
- restart inspector result: `<PASS|FAIL + evidence>`
- full-inventory negative case / feedback: `<...>`
- verdict: `<PASS|FAIL>`

## B. Physical Training Sword hotbar

- item UUID: `<uuid>`
- initial inspector line: `<...>`
- first slot / post-commit inspector / reconnect: `<...>`
- second slot / post-commit inspector / reconnect: `<...>`
- MISS action UUID and pre/post inspector durability/version: `<...>`
- HIT action UUID and pre/post inspector durability/version: `<...>`
- broken-state rejection and final inspector line: `<...>`
- Chronicle-slot rejection and recovery: `<...>`
- restart inspector result: `<...>`
- verdict: `<PASS|FAIL>`

## C. Whole physical consumable lot

- value definition: `consumable.training_body_tonic`
- lot UUID: `<uuid>`
- initial location/version/quantity inspector line: `<...>`
- hotbar move / reconnect inspector result: `<...>`
- committed use operation and post quantity/version inspector line: `<...>`
- split/merge/swap rejection and recovery inspector result: `<...>`
- final slot / restart inspector result: `<...>`
- verdict: `<PASS|FAIL>`

## D. Physical shield OFF_HAND

- value definition: `equipment.training_shield`
- shield A UUID: `<uuid>`
- shield B UUID: `<uuid>`
- initial inspector lines: `<...>`
- inventory -> OFF_HAND inspector version/location transition: `<...>`
- unequip/swap inspector transition: `<...>`
- blocked impact UUID and pre/post wear inspector lines: `<...>`
- broken guard rejection: `<...>`
- Staff F-key negative case and unchanged Staff inspector line: `<...>`
- restart OFF_HAND reconstruction inspector result: `<...>`
- verdict: `<PASS|FAIL>`

## E. Ordinary world-mob health

- entity type/UUID: `<...>`
- training-dummy tag absent: `<yes/no>`
- pre/post Bukkit current/max health: `<...>`
- combat action/projectile/spell operation ID: `<...>`
- durable item inspector before/after, when applicable: `<...>`
- lethal lifecycle: `<...>`
- projectile/Staff repeat: `<...>`
- tagged training-dummy control: `<...>`
- verdict: `<PASS|FAIL>`

## F. Chronicle/native-slot boundary

- `/mmo physical status` baseline: `<...>`
- native slot mutation attempts and feedback: `<...>`
- supported virtual/build/cosmetic transaction: `<...>`
- close/reopen inspector comparison: `<...>`
- reconnect inspector comparison: `<...>`
- verdict: `<PASS|FAIL>`

## Final gate

All A-F verdicts: `<PASS|FAIL>`  
Environment fingerprint stayed consistent: `<PASS|FAIL>`  
Known blocker: `<none|description>`  
Feature status decision: `<remain AUTOMATED_VERIFIED | LIVE_ACCEPTED>`

Do not attach credentials, raw item payload JSON or large raw logs to this record. Screenshots and
Bukkit inventory appearance are supporting evidence only; use the stable inspector/projection fields
above for authority decisions.

Do not write `COMPLETE` here unless the global completion gate in `42-ai-coding-handoff.md` is also
satisfied on this exact accepted runtime revision.
