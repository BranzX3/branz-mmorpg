# Default Configuration and Balance Targets

These are implementation defaults, not permanent live-balance promises. Runtime config may tune values within safe bounds without changing invariants.

## Combat timing

```yaml
engagement:
  alert_timeout_ticks: 80
  disengage_quiet_ticks: 160
  disengage_confirm_ticks: 40
  aggro_check_radius: 24.0
input:
  buffer_capacity: 1
  default_queue_window_ticks: 8
  buffer_expiry_ticks: 12
  duplicate_window_ticks: 2
  shift_dodge_grace_ticks: 3
  shift_crouch_hold_ticks: 5
```

## Resources

```yaml
resources:
  base_hp: 1000
  base_stamina: 100
  base_mana: 100
  stamina_regen_per_second: 18
  stamina_regen_delay_ticks: 20
  mana_combat_regen_per_second: 2
  mana_combat_regen_delay_ticks: 40
  mana_out_of_combat_regen_per_second: 8
  mana_out_of_combat_delay_ticks: 20
```

## Defense

```yaml
defense:
  armor_constant: 200
  pve_armor_cap: 0.65
  pvp_armor_cap: 0.50
  max_penetration_percent: 0.40
  weapon_guard_physical_block: 0.80
  shield_guard_physical_block: 0.95
  guard_stability_base: 100
  guard_break_ticks: 24
  weapon_perfect_guard_ticks: 3
  shield_perfect_guard_ticks: 5
```

## Dodge

```yaml
dodge:
  light: {cost: 24, total_ticks: 14, iframe_from: 4, iframe_to: 9, distance: 4.2}
  medium: {cost: 28, total_ticks: 16, iframe_from: 5, iframe_to: 10, distance: 3.5}
  heavy: {cost: 32, total_ticks: 18, iframe_from: 6, iframe_to: 9, distance: 2.6}
  overloaded: {cost: 36, total_ticks: 20, iframe_from: -1, iframe_to: -1, distance: 1.4}
```

## Load tiers

```yaml
load:
  light_max_ratio: 0.40
  medium_max_ratio: 0.70
  heavy_max_ratio: 1.00
```

## Conditional advantage

```yaml
advantage:
  counter: 0.20
  back_attack: 0.15
  weak_point: 0.30
  posture_break: 0.25
  combined_cap: 0.60
```

## Flask

```yaml
flask:
  base_charges: 6
  v1_max_charges: 9
  windup_ticks: 12
  recovery_ticks: 10
  healing_fraction: 0.35
  mana_fraction: 0.45
  stamina_amount: 70
  mercy_healing_charges: 1
```

## Items and enhancement

```yaml
durability:
  field_repair_cap_fraction: 0.60
  minimum_max_durability_fraction: 0.50
enhancement:
  max_level: 10
  total_raw_power_target: 0.18
  total_raw_power_hard_cap: 0.20
  base_success_chance:
    1: 1.00
    2: 0.95
    3: 0.90
    4: 0.80
    5: 0.70
    6: 0.60
    7: 0.48
    8: 0.36
    9: 0.25
    10: 0.15
  momentum_gain_on_failure: 0.08
  momentum_cap: 0.40
  max_durability_loss_fraction_on_failure:
    low: 0.02
    mid: 0.04
    high: 0.06
```

## Progression caps

```yaml
progression:
  mastery_cost_reduction_cap: 0.10
  mastery_recovery_reduction_cap: 0.05
  mastery_posture_efficiency_cap: 0.08
  mastery_raw_damage_cap: 0.05
  conditioning_handling_cap: 0.20
  conditioning_stamina_efficiency_cap: 0.08
  conditioning_stability_cap: 0.12
```

## Party/encounter

```yaml
party:
  max_size: 5
  reconnect_grace_seconds: 300
encounter:
  boss_reentry_grace_seconds: 90
  late_join_hp_threshold: 0.20
rewards:
  owner_drop_seconds: 120
  pending_retention_days: 30
```

## PvP

```yaml
pvp:
  damage_multiplier: 0.70
  healing_multiplier: 0.60
  guard_pressure_multiplier: 0.85
  cc_duration_multiplier: 0.65
  hard_cc_immunity_ticks: 30
```

## Scene

```yaml
scene:
  full_preview_distance: 2.75
  compact_preview_distance: 1.60
  hostile_aggro_block_radius: 16.0
  candidate_yaw_offsets: [0, 35, -35, 70, -70, 180]
  session_timeout_seconds: 300
```

## Performance

```yaml
performance:
  target_online_players: 100
  target_active_combatants: 40
  max_active_projectiles_per_caster: 32
  max_active_zones_per_caster: 4
  max_temp_summons_per_caster: 2
  p95_mspt: 35
  p99_mspt: 45
```

## Safe tuning ranges

Live configuration may tune ordinary numeric values by approximately ±25% during V1 balance work. The following require spec/ADR review rather than tuning:

- hotbar slot ownership,
- input semantic ownership,
- item identity/transaction rules,
- state graph structure,
- random critical/accuracy policy,
- durability destruction/downgrade policy,
- Scene commit/cancel behavior,
- personal reward idempotency,
- PvP scope.
