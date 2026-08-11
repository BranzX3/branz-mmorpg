# Physical Gameplay Item Acceptance Evidence

Target server commit: `<sha>`  
Content version: `<version>`  
Acceptance date/time: `<local timestamp>`  
Player UUID: `<uuid>`  
Character ID: `<uuid>`

## A. Legacy MAIN_HAND migration

- value definition: `weapon.training_sword`
- item UUID: `<uuid>`
- pre location/version/durability: `<...>`
- post location/version/durability: `<...>`
- reconnect: `<PASS|FAIL + evidence>`
- restart: `<PASS|FAIL + evidence>`
- negative case / feedback: `<...>`
- verdict: `<PASS|FAIL>`

## B. Physical Training Sword hotbar

- item UUID: `<uuid>`
- first slot/reconnect: `<...>`
- second slot/reconnect: `<...>`
- MISS action UUID and pre/post durability/version: `<...>`
- HIT action UUID and pre/post durability/version: `<...>`
- broken-state rejection: `<...>`
- Chronicle-slot rejection: `<...>`
- restart: `<...>`
- verdict: `<PASS|FAIL>`

## C. Whole physical consumable lot

- value definition: `consumable.training_body_tonic`
- lot UUID: `<uuid>`
- pre slot/version/quantity: `<...>`
- committed use operation and post quantity/version: `<...>`
- split/merge/swap rejection and recovery: `<...>`
- final slot/restart: `<...>`
- verdict: `<PASS|FAIL>`

## D. Physical shield OFF_HAND

- value definition: `equipment.training_shield`
- shield A UUID: `<uuid>`
- shield B UUID: `<uuid>`
- inventory -> OFF_HAND version transition: `<...>`
- unequip/swap transition: `<...>`
- blocked impact UUID and wear: `<...>`
- broken guard rejection: `<...>`
- Staff F-key negative case: `<...>`
- restart reconstruction: `<...>`
- verdict: `<PASS|FAIL>`

## E. Ordinary world-mob health

- entity type/UUID: `<...>`
- training-dummy tag absent: `<yes/no>`
- pre/post Bukkit current/max health: `<...>`
- lethal lifecycle: `<...>`
- projectile/Staff repeat: `<...>`
- tagged training-dummy control: `<...>`
- verdict: `<PASS|FAIL>`

## F. Chronicle/native-slot boundary

- physical item/offhand state before Chronicle: `<...>`
- native slot mutation attempts and feedback: `<...>`
- supported virtual/build/cosmetic transaction: `<...>`
- close/reopen comparison: `<...>`
- reconnect comparison: `<...>`
- verdict: `<PASS|FAIL>`

## Final gate

All A-F verdicts: `<PASS|FAIL>`  
Known blocker: `<none|description>`  
Feature status decision: `<remain AUTOMATED_VERIFIED | LIVE_ACCEPTED>`

Do not write `COMPLETE` here unless the global completion gate in `42-ai-coding-handoff.md` is also
satisfied on this exact accepted revision.
