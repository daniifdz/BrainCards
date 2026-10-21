-- Comprobaciones PREVIAS al endurecimiento del servidor (sql/parent_owner_rls.sql). SOLO LECTURA:
-- no crea ni cambia nada.
--
-- COMO USARLO: el SQL Editor de Supabase solo muestra el resultado de la ULTIMA sentencia, asi que
-- pega y ejecuta cada bloque P1..P7 POR SEPARADO. GUARDA la salida de P1 (politicas) y de P2
-- (permisos): es el estado al que vuelve sql/parent_owner_rls_rollback.sql.
--
-- Objetivo del endurecimiento: hoy las politicas de INSERT/UPDATE de topics y flashcards solo
-- comprueban auth.uid() = user_id. La FK comprueba que el padre (deck/topic) EXISTE, pero salta
-- RLS, asi que una fila propia puede colgar del deck o topic de OTRA cuenta. Las politicas nuevas
-- exigen ademas que el padre exista y sea del mismo usuario.


-- ============================================================================
-- P1. Politicas actuales de decks, topics y flashcards.
-- ============================================================================
-- Esperado (segun sql/supabase_schema.sql): <tabla>_select_own, _insert_own y _update_own, todas
-- permissive, sin politicas DELETE. Si hay OTRAS politicas permisivas de INSERT/UPDATE/ALL, se
-- combinan con OR y anularian el endurecimiento: sql/parent_owner_rls.sql aborta si las encuentra.

select tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public' and tablename in ('decks', 'topics', 'flashcards')
order by tablename, cmd, policyname;


-- ============================================================================
-- P2. Permisos actuales de anon y authenticated (incluye reviewlog).
-- ============================================================================
-- Los default privileges de Supabase conceden TODO (incluidos TRUNCATE, REFERENCES y TRIGGER) a
-- anon y authenticated sobre cualquier tabla nueva de public. Si NO sale ninguna fila de
-- reviewlog, esa tabla no existe en el servidor: NO ejecutes el bloque de reviewlog del script de
-- aplicar (un revoke sobre una tabla inexistente abortaria su transaccion).

select table_name, grantee, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('decks', 'topics', 'flashcards', 'reviewlog')
  and grantee in ('anon', 'authenticated')
order by table_name, grantee, privilege_type;


-- ============================================================================
-- P3. RLS activo (incluye reviewlog si existe).
-- ============================================================================
-- Esperado: relrowsecurity = true en todas las filas. to_regclass devuelve NULL (en vez de dar
-- error) si reviewlog no existe.

select relname, relrowsecurity, relforcerowsecurity
from pg_class
where oid in (
  'public.decks'::regclass,
  'public.topics'::regclass,
  'public.flashcards'::regclass,
  to_regclass('public.reviewlog')
);


-- ============================================================================
-- P4. Triggers de usuario en las tres tablas.
-- ============================================================================
-- Esperado: ninguna fila (el cliente no depende de ninguno).

select tgrelid::regclass as tabla, tgname
from pg_trigger
where not tgisinternal
  and tgrelid in ('public.decks'::regclass, 'public.topics'::regclass, 'public.flashcards'::regclass);


-- ============================================================================
-- P5. Filas que HOY violarian las politicas nuevas (de todos los usuarios).
-- ============================================================================
-- Esperado: las tres filas con 0. Si no, mira P6 y limpia o corrige antes de aplicar (de lo
-- contrario esas filas dejarian de poder actualizarse/subirse: el cliente recibiria un 403 por fila).

select 'flashcards con deck inexistente o de otro dueno' as problema, count(*) as filas
from public.flashcards f
left join public.decks d on d.id = f.deck_id
where d.id is null or d.user_id <> f.user_id
union all
select 'flashcards con topic inexistente o de otro dueno', count(*)
from public.flashcards f
left join public.topics t on t.id = f.topic_id
where f.topic_id is not null and (t.id is null or t.user_id <> f.user_id)
union all
select 'topics con deck inexistente o de otro dueno', count(*)
from public.topics t
left join public.decks d on d.id = t.deck_id
where d.id is null or d.user_id <> t.user_id;


-- ============================================================================
-- P6. Detalle de P5 (solo si algun contador de P5 no es 0).
-- ============================================================================

select 'flashcard' as tipo, f.id, f.user_id as dueno, f.deck_id, d.user_id as dueno_deck,
       f.topic_id, t.user_id as dueno_topic, f.deleted_at
from public.flashcards f
left join public.decks d on d.id = f.deck_id
left join public.topics t on t.id = f.topic_id
where d.id is null or d.user_id <> f.user_id
   or (f.topic_id is not null and (t.id is null or t.user_id <> f.user_id))
union all
select 'topic', t.id, t.user_id, t.deck_id, d.user_id, null, null, t.deleted_at
from public.topics t
left join public.decks d on d.id = t.deck_id
where d.id is null or d.user_id <> t.user_id
order by tipo, dueno;


-- ============================================================================
-- P7. card_scheduling que colgaria de esas flashcards.
-- ============================================================================
-- Si limpias las flashcards de P6 con DELETE, estas filas se borran en cascada
-- (card_scheduling.card_id -> flashcards ON DELETE CASCADE).

select cs.card_id, cs.user_id
from public.card_scheduling cs
join public.flashcards f on f.id = cs.card_id
left join public.decks d on d.id = f.deck_id
left join public.topics t on t.id = f.topic_id
where d.id is null or d.user_id <> f.user_id
   or (f.topic_id is not null and (t.id is null or t.user_id <> f.user_id));
