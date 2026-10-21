-- ROLLBACK de sql/parent_owner_rls.sql: devuelve decks, topics y flashcards al estado de
-- sql/supabase_schema.sql (politicas de INSERT/UPDATE solo con auth.uid() = user_id) y los
-- permisos por defecto de Supabase.
--
-- ANTES DE USARLO, compara con la salida que guardaste de P1 (politicas) y P2 (permisos) en
-- sql/parent_owner_precheck.sql: si tus politicas o permisos reales eran distintos de los de
-- supabase_schema.sql / los por defecto, adapta este script a lo que guardaste.
--
-- COMO USARLO: ejecuta la PARTE 1 de una vez (una transaccion). La PARTE 2 solo si aplicaste la
-- PARTE 2 de parent_owner_rls.sql (reviewlog). Despues, vuelve a ejecutar V1 y V2 de
-- sql/parent_owner_rls_verify.sql para comprobar el estado.


-- ============================================================================
-- PARTE 1. decks, topics, flashcards
-- ============================================================================

begin;

drop policy if exists "topics_insert_own" on public.topics;
create policy "topics_insert_own"
  on public.topics for insert
  with check (auth.uid() = user_id);

drop policy if exists "topics_update_own" on public.topics;
create policy "topics_update_own"
  on public.topics for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "flashcards_insert_own" on public.flashcards;
create policy "flashcards_insert_own"
  on public.flashcards for insert
  with check (auth.uid() = user_id);

drop policy if exists "flashcards_update_own" on public.flashcards;
create policy "flashcards_update_own"
  on public.flashcards for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- Permisos: vuelta a los por defecto de Supabase (todo para anon y authenticated).
grant all on public.decks, public.topics, public.flashcards to anon, authenticated;

commit;


-- ============================================================================
-- PARTE 2. reviewlog -- SOLO si aplicaste la PARTE 2 de parent_owner_rls.sql
-- ============================================================================

begin;

grant all on public.reviewlog to anon, authenticated;

commit;
