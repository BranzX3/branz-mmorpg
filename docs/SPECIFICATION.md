# Branz MMORPG - Legacy Product Direction
**Status**: Historical gameplay direction; locked engineering decisions live in `PHASE_1_FOUNDATION.md` and override conflicts in this document.  
**Target Platform**: Paper 26.2  
**Target Java Version**: Java 25  

---

## 1. ภาพรวมระบบ (System Overview)
ระบบ Branz MMORPG เป็นระบบ Plugin แบบ Sandbox MMORPG บน Paper Minecraft ที่ใช้การเติบโตแบบ **Mastery / Skill-usage** (แบบ Albion Online / RuneScape) ผู้เล่นจะพัฒนาตัวละครผ่านการใช้งานไอเทมและสกิลจริง ข้อมูลถูกจัดเก็บแบบ Document DB (MongoDB) และแสดงผลมอนสเตอร์/บอสระดับสูงผ่าน ModelEngine & ProtocolLib

---

## 2. สถาปัตยกรรมซอฟต์แวร์ (Software Architecture)

โครงการแบ่งโครงสร้างออกเป็น 3 Modules หลัก (Gradle Multi-Module):

```
branz-mmorpg/
├── mmorpg-api/        # Interfaces, Data Models, Events, Custom API for addon plugins
├── mmorpg-core/       # Core Logic, MongoDB BSON DAO, Stat Calculations, Mastery Engine
└── mmorpg-paper/      # Paper Bootstrap, Event Listeners, Commands, GUI Displays, Packets
```

---

## 3. สเปกระบบหลักจากการสัมภาษณ์สรุปผล (/grill-me Design Tree)

### 3.1 ระบบการเติบโตของตัวละคร (Progression & Mastery System)
- **Model**: **Mastery / Skill-Usage Progression** (ใช้สิ่งไหน เก่งสิ่งนั้น)
- **Structure**: **Direct Item/Skill Leveling**  
  ระบบจะสะสม EXP แยกอิสระในระดับแต่ละไอเทมและแต่ละสกิลโดยตรง (เช่น `broadsword_mastery_xp`, `fireball_skill_xp`)
- **Penalty on Death**: **Safe Respawn with Minor EXP Penalty**  
  เมื่อผู้เล่นเสียชีวิต อุปกรณ์และไอเทมจะไม่ตก และความทนทานจะไม่ลดลง แต่จะถูกปรับหักค่า Mastery EXP เล็กน้อย และส่งกลับไปเกิดยังจุด Safe Zone

### 3.2 ระบบควบคุมและการใช้งานสกิล (Active Skill Execution)
- **Input Mechanism**: **Off-hand & Key Interaction**
  - กดปุ่ม Swap Hand (`Key F`) + Click
  - `Shift + Right Click` ที่ไอเทมประเภทอาวุธ
  - การใช้งานจะหักค่า Mana/Stamina และเข้าสู่ Cooldown โดยประมวลผลระดับ Millisecond

### 3.3 ระบบฐานข้อมูลและการจัดเก็บ (Database Architecture)
- **Database Engine**: **MongoDB (Document DB)**
  - เก็บข้อมูล Player Profile เป็น BSON/JSON Document
  - รองรับการปรับเปลี่ยนฟิลด์และโครงสร้าง Mastery Data ได้ยืดหยุ่นสูงโดยไม่ต้องรัน Schema Migration
  - ประมวลผล บันทึก และโหลดข้อมูลแบบ Asynchronous ทั้งหมดผ่าน Mongo Driver (CompletableFuture)

### 3.4 ระบบมอนสเตอร์และบอส (Mob & Boss Engine)
- **Visual & Entity Engine**: **ModelEngine + ProtocolLib / PacketEvents**
  - รองรับการแสดงผลโมเดล 3D และ Animation ซับซ้อนด้วย ModelEngine
  - ควบคุมและจัดการ Entity Packets ด้วย ProtocolLib เพื่อประสิทธิภาพสูงสุดและไม่ลดทอน Server TPS

### 3.5 ระบบแสดงผลส่วนติดต่อผู้เล่น (Player HUD & Visual UI)
- **Combined HUD**:
  - **Action Bar Display**: แสดงผลหลอด HP / MP / Skill Cooldown แบบ Real-time บน Action Bar
  - **MiniMessage Inventory GUI**: เมนูระบบ, หน้าต่างดูความชำนาญ (Mastery Status), และการจัดสรรสกิลผ่าน Custom Chest GUI

---

## 4. แผนการดำเนินงาน (Implementation Roadmap)

- [x] Phase 0: กำหนด System Specification ผ่าน /grill-me & ออกแบบ Architecture
- [x] Phase 1: สถาปนา Gradle Multi-Module Project Structure (`mmorpg-api`, `mmorpg-core`, `mmorpg-paper`)
- [ ] Phase 2: ติดตั้ง MongoDB Driver & ระบบ Player Profile Document Persistence
- [ ] Phase 3: ระบบ Direct Item/Skill Mastery XP Engine
- [ ] Phase 4: ระบบ Active Skill Execution (Off-hand / Shift-Right Click Listener)
- [ ] Phase 5: Action Bar Combined HUD & MiniMessage GUI Engine
- [ ] Phase 6: ModelEngine + ProtocolLib Packet Entity Integration
- [ ] Phase 7: Testing & Performance Optimization (Paper Profiler / Spark)
