create table public.decks (
  id uuid primary key,
  name text not null,
  description text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  last_synced_at timestamptz,
  user_id uuid not null references auth.users(id) on delete cascade
);

create index decks_user_id_idx on public.decks (user_id);

alter table public.decks enable row level security;

create policy "decks_select_own"
  on public.decks for select
  using (auth.uid() = user_id);

create policy "decks_insert_own"
  on public.decks for insert
  with check (auth.uid() = user_id);

create policy "decks_update_own"
  on public.decks for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);


create table public.topics (
  id uuid primary key,
  deck_id uuid not null references public.decks(id),
  name text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  last_synced_at timestamptz,
  user_id uuid not null references auth.users(id) on delete cascade
);

create index topics_deck_id_idx on public.topics (deck_id);
create index topics_user_id_idx on public.topics (user_id);

alter table public.topics enable row level security;

create policy "topics_select_own"
  on public.topics for select
  using (auth.uid() = user_id);

create policy "topics_insert_own"
  on public.topics for insert
  with check (auth.uid() = user_id);

create policy "topics_update_own"
  on public.topics for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);



create table public.flashcards (
  id uuid primary key,
  deck_id uuid not null references public.decks(id),
  topic_id uuid references public.topics(id),
  question text not null,
  answer text not null,
  card_type text not null check (card_type in ('FLIP', 'MULTIPLE_CHOICE')),
  options jsonb,
  correct_option_index integer,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  last_synced_at timestamptz,
  user_id uuid not null references auth.users(id) on delete cascade
);

create index flashcards_deck_id_idx on public.flashcards (deck_id);
create index flashcards_topic_id_idx on public.flashcards (topic_id);
create index flashcards_user_id_idx on public.flashcards (user_id);

alter table public.flashcards enable row level security;

create policy "flashcards_select_own"
  on public.flashcards for select
  using (auth.uid() = user_id);

create policy "flashcards_insert_own"
  on public.flashcards for insert
  with check (auth.uid() = user_id);

create policy "flashcards_update_own"
  on public.flashcards for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create table public.reviewlog (
  id uuid primary key,
  card_id uuid not null references public.flashcards(id),
  "timestamp" timestamptz not null,
  grade integer not null,
  previous_interval integer not null,
  new_interval integer not null,
  user_id uuid not null references auth.users(id) on delete cascade
);

create index reviewlog_card_id_idx on public.reviewlog (card_id);
create index reviewlog_user_id_idx on public.reviewlog (user_id);

alter table public.reviewlog enable row level security;

create policy "reviewlog_select_own"
  on public.reviewlog for select
  using (auth.uid() = user_id);

create policy "reviewlog_insert_own"
  on public.reviewlog for insert
  with check (auth.uid() = user_id);

