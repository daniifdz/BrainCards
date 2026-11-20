-- Sync del progreso de estudio (card_scheduling). Espejo en Postgres de la
-- tabla local card_scheduling (SM-2: easeFactor, interval, repetitions, nextReview).
--
-- Una fila por tarjeta REPASADA (se crea en el primer repaso, no al crear la tarjeta).
-- PK = card_id (no hay columna id): es 1:1 con public.flashcards.
--
-- Nombres de columna: snake_case como el resto del esquema. "interval" local pasa a
-- interval_days porque INTERVAL es un tipo/palabra delicada en Postgres. El DTO del
-- cliente lo mapea con @SerialName; el upsert del cliente usa onConflict = "card_id".
--
-- ON DELETE CASCADE en card_id: si se borra de verdad una flashcard (la app solo hace
-- borrado logico, asi que hoy solo ocurre en cascada al borrar la cuenta), su
-- scheduling se va con ella. user_id tambien cascade (borrado de cuenta,
-- sql/delete_own_account.sql).
--
-- RLS: cada usuario solo ve y toca sus filas (auth.uid() = user_id). En insert y update
-- ademas la flashcard referenciada debe existir y ser del mismo usuario: las FK saltan
-- RLS, asi que sin este exists se podria colgar una fila propia de la flashcard de otro
-- (y sondear si un uuid ajeno existe por el error de FK). El exists se evalua con las
-- policies de flashcards del propio usuario (flashcards_select_own).
--
-- No se aplica con migraciones automaticas -- se ejecuta a mano en el SQL Editor de
-- Supabase, igual que sql/supabase_schema.sql y sql/delete_own_account.sql. Es
-- idempotente: se puede volver a ejecutar (create ... if not exists, drop policy if
-- exists). OJO: "create table if not exists" NO modifica una tabla que ya existiera con
-- otra forma; si se cambia el esquema mas adelante, hace falta un alter table aparte.
--
-- Despues de ejecutarlo, comprobar con sql/card_scheduling_verify.sql.

-- ============================================================================
-- 1. TABLA
-- ============================================================================

create table if not exists public.card_scheduling (
  card_id uuid primary key references public.flashcards(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  ease_factor double precision not null,
  interval_days integer not null,
  repetitions integer not null,
  next_review timestamptz not null,
  updated_at timestamptz not null,
  last_synced_at timestamptz
);

-- Cubre el pull (where user_id = ? and updated_at > ? order by updated_at).
create index if not exists card_scheduling_user_id_idx
  on public.card_scheduling (user_id, updated_at);

-- ============================================================================
-- 2. PERMISOS
-- ============================================================================
-- Los default privileges de Supabase conceden TODO (incluidos TRUNCATE, REFERENCES y
-- TRIGGER) a anon y authenticated sobre cualquier tabla nueva de public. Eso es un
-- problema porque RLS NO protege contra TRUNCATE: cualquier usuario con ese privilegio
-- podria vaciar la tabla entera de todos los usuarios, saltandose las policies.
-- Por eso se recortan los permisos al minimo que necesita la app:
--   - authenticated: solo select, insert, update, delete (RLS sigue aplicando encima).
--   - anon: nada. La app siempre llama con sesion.

grant select, insert, update, delete on public.card_scheduling to authenticated;

revoke all on public.card_scheduling from anon;
revoke truncate, references, trigger on public.card_scheduling from authenticated;

-- ============================================================================
-- 3. ROW LEVEL SECURITY
-- ============================================================================

alter table public.card_scheduling enable row level security;

drop policy if exists "card_scheduling_select_own" on public.card_scheduling;
create policy "card_scheduling_select_own"
  on public.card_scheduling for select
  using (auth.uid() = user_id);

drop policy if exists "card_scheduling_insert_own" on public.card_scheduling;
create policy "card_scheduling_insert_own"
  on public.card_scheduling for insert
  with check (
    auth.uid() = user_id
    and exists (
      select 1 from public.flashcards f
      where f.id = card_id and f.user_id = auth.uid()
    )
  );

drop policy if exists "card_scheduling_update_own" on public.card_scheduling;
create policy "card_scheduling_update_own"
  on public.card_scheduling for update
  using (auth.uid() = user_id)
  with check (
    auth.uid() = user_id
    and exists (
      select 1 from public.flashcards f
      where f.id = card_id and f.user_id = auth.uid()
    )
  );

drop policy if exists "card_scheduling_delete_own" on public.card_scheduling;
create policy "card_scheduling_delete_own"
  on public.card_scheduling for delete
  using (auth.uid() = user_id);
