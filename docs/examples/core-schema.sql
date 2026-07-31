-- Illustrative contract only; migration files own production DDL.
create table characters (
  character_id uuid primary key,
  account_uuid uuid not null unique,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table items (
  item_uuid uuid primary key,
  definition_id text not null,
  definition_revision integer not null,
  enhancement_level integer not null default 0,
  current_durability integer,
  max_durability integer,
  version bigint not null default 0,
  state jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table item_locations (
  item_uuid uuid primary key references items(item_uuid),
  owner_character_id uuid references characters(character_id),
  location_type text not null,
  location_key text not null,
  unique(owner_character_id, location_type, location_key)
);

create table transactions (
  transaction_id uuid primary key,
  idempotency_key text not null unique,
  type text not null,
  state text not null,
  actor_character_id uuid,
  metadata jsonb not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table reward_grants (
  reward_grant_id text primary key,
  character_id uuid not null references characters(character_id),
  encounter_id uuid,
  payload jsonb not null,
  delivery_state text not null,
  created_at timestamptz not null
);
