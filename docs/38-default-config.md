# Default Configuration and Balance Targets

These are starting values, not invariants. Telemetry/playtests may tune them without architecture changes.

## Combat

```yaml
engagement_exit_seconds: 8
input_buffer_ticks: 8
tap_hold_threshold_ticks: 8
perfect_guard_window_ticks: 4
soft_facing_normal_degrees: 15
soft_facing_heavy_degrees: 8
armor_mitigation_cap: 0.70
penetration_percent_cap: 0.60
pvp_damage_multiplier: 0.65
pvp_cc_duration_multiplier: 0.60
```

## Dodge

```yaml
light: { stamina: 25, iframes_ticks: 6 }
medium: { stamina: 30, iframes_ticks: 4 }
heavy: { stamina: 35, iframes_ticks: 2 }
overloaded: { stamina: 40, iframes_ticks: 0 }
```

## Resources

```yaml
base_stamina: 100
stamina_regen_engaged_per_second: 8
stamina_regen_exploration_per_second: 12
base_mana: 100
mana_regen_engaged_per_second: 2
mana_regen_exploration_per_second: 8
mana_focus_channel_per_second: 6
critical_health_ratio: 0.10
```

## Flask

```yaml
base_charges: 5
healing_ratio: 0.35
mana_ratio: 0.40
stamina_amount: 60
windup_ticks: 28
commit_tick: 18
recovery_ticks: 20
mercy_minimum_charges: 2
```

## Combat progression evidence

```yaml
mastery_evidence_max_v1: 1000
mastery_evidence_per_candidate_cap: 100
mastery_dummy_familiarity_limit: 25
mastery_repetition_window_minutes: 30
mastery_daily_curve: { full_until: 100, half_until: 250, late_factor: 0.25 }
mastery_readiness_thresholds: [0, 100, 300, 600, 850]
```

## Lifeskill

```yaml
life_focus_max: 100
life_focus_regen_minutes: 10
mastery_max_v1: 1000
work_speed_bonus_cap: 0.35
basic_yield_bonus_cap: 0.60
rare_yield_relative_bonus_cap: 0.30
worker_offline_queue_hours: 24
base_worker_slots: 2
max_worker_slots_v1: 6
```

## Market

```yaml
listing_deposit_rate: 0.01
sale_tax_rate: 0.075
commodity_price_band: 0.25
processed_price_band: 0.35
reference_daily_change_cap: 0.05
commodity_order_days: 7
unique_listing_days: 14
cosmetic_listing_days: 30
```

## Party and Scene

```yaml
party_size: 5
downed_seconds: 15
revive_channel_seconds: 4
revives_per_encounter: 1
scene_preview_distance_blocks: 2.75
rest_context_spawn_radius_blocks: 16.0
mount_whistle_range_blocks: 96
caravan_pack_mounts_max: 4
```

## Economy targets

- Basic recovery and starter tools remain affordable through NPC price ceilings.
- Market tax, repair, enhancement, station fees, freight, packaging and worker wages collectively remove meaningful currency.
- No repeatable activity may create uncapped currency without time, risk or demand saturation.
- Veteran lifeskill basic output should stay below roughly 1.6× novice output before rare/byproduct specialization.

## Content budgets

- Normal move total timeline: usually 12–40 ticks.
- No standard player move has more than 8 targets.
- No normal persistent zone lasts more than 20 seconds without sustain cost.
- V1 boss heavy telegraph is avoidable and may deal 50–80% of expected HP, not unavoidable one-shot damage.
- Status and resistance caps follow subsystem documents.
