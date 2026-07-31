# สรุป Core MMO V1 Full

เอกสารชุดนี้ล็อก baseline สำหรับเริ่มพัฒนา V1 แบบวงจรเต็ม โดยมีระบบสำคัญดังนี้

- ตัวละครเดียวต่อบัญชี ไม่มีคลาส เรียนทุกสายได้
- Combat แบบ action server-authoritative พร้อม Dodge, Guard, Perfect Guard, Parry, Posture และ CC
- อาวุธ V1: Greatsword, Sword & Shield, Bow, Crossbow และ Staff
- Magic runtime, Techniques, Forms, Attunement, Mastery และ Body Conditioning
- Hotbar ช่อง 1–8 ใช้ gameplay และช่อง 9 เป็น Adventurer's Chronicle
- Local Scene Hub เห็นตัวละคร Preview ตรงสถานที่เดิม ไม่วาร์ป และปิดทันทีเมื่อเกิดอันตราย
- Item UUID/Lot UUID, Durability, Enhancement, Repair, Personal Loot และ Death Pouch
- Flask, Potion, Status, Rest, Camp และ Boss Checkpoint
- Lifeskill: Gathering, Fishing, Hunting, Processing, Cooking, Alchemy, Smithing, Trading, Training และ Farming
- Worker แบบ background job จำกัด 24 ชั่วโมงและ reserve ต้นทุนก่อนเริ่ม
- Central Exchange, Unique Market, Crafting Commission และ Regional Trade Cargo
- เมืองเศรษฐกิจหลัก 5 เมือง พร้อม demand, saturation และ event
- Horse, Camel, Mule และ Llama Caravan พร้อม Stable/Cargo/Recovery
- City Storage, Bank, Market Warehouse, Freight และ Overflow Claim
- Map discovery, Coach, Ferry, Encounter ecology และ Activity Board
- Party, LFG, Downed/Revive, Duel และ Arena โดยไม่มี Open-world PvP
- PostgreSQL, Transaction Journal, Reconciliation, Oraxen/Git/CI pipeline และแผน implementation
- Content Authoring Tools: CLI, schema autocomplete, In-game Dev Console, Combat/Scene/Recipe Lab, simulator, PR preview และ validation report

## ขอบเขตที่ตั้งใจไม่ทำใน V1

- Open-world PvP และ Territory War
- Raid ขนาดใหญ่
- Housing และการถือครองที่ดินอิสระ
- Sailing/Bartering เต็มระบบ
- Mounted Combat
- Mount ตายถาวรและระบบ breeding เชิง gacha
- Player loan/interest และระบบการเงินซับซ้อน
- Multi-shard network

## ลำดับเริ่ม Coding

เริ่มจาก Milestone 0–2 ใน `39-implementation-roadmap.md` เท่านั้น:

1. Repository และ quality gate
2. Content schema/snapshot/provider interfaces
3. PostgreSQL, character lease, transaction journal และ item location

ห้ามเริ่ม Combat, Market หรือ Lifeskill ก่อน transaction/idempotency tests ผ่าน เพราะระบบทั้งหมดพึ่ง ownership model เดียวกัน

## เครื่องมือสร้าง Content

`43-content-authoring-tools.md` กำหนดเครื่องมือ V1 สำหรับ Designer/Artist/QA โดย Git ยังเป็น source of truth และทุกเครื่องมือทำงานเฉพาะ local, Content Dev, Integration หรือ Staging ไม่มีการแก้หรือ deploy Production โดยตรง
