-- Comprobaciones PREVIAS a sql/server_updated_at.sql. SOLO LECTURA: no crea ni cambia nada.
--
-- Contexto: el pull incremental necesita una marca de agua escrita por el SERVIDOR. `updated_at` la
-- escribe el reloj del cliente (un dispositivo que sube tarde con un updated_at antiguo no se
-- volveria a bajar nunca desde otro dispositivo), asi que se anade `server_updated_at` con un
-- trigger BEFORE INSERT OR UPDATE y se usa SOLO como marca de agua. `updated_at` sigue siendo el del
-- cliente y sigue mandando en el LWW.
--
-- COMO USARLO: el SQL Editor de Supabase solo muestra el resultado de la ULTIMA sentencia, asi que
-- pega y ejecuta cada bloque A1..A4 POR SEPARADO.


-- ============================================================================
-- A1. La columna server_updated_at NO debe existir todavia. Esperado: 0 filas.
-- ============================================================================

select table_name, column_name
from information_schema.columns
where table_schema = 'public'
  and table_name in ('decks', 'topics', 'flashcards', 'card_scheduling')
  and column_name = 'server_updated_at';


-- ============================================================================
-- A2. Triggers de usuario en las cuatro tablas. Esperado: 0 filas (P4 ya salio vacio en decks,
--     topics y flashcards; esta consulta cubre tambien card_scheduling).
-- ============================================================================

select tgrelid::regclass as tabla, tgname
from pg_trigger
where not tgisinternal
  and tgrelid in (
    'public.decks'::regclass,
    'public.topics'::regclass,
    'public.flashcards'::regclass,
    'public.card_scheduling'::regclass
  );


-- ============================================================================
-- A3. Sin colisiones de nombres. Esperado: la funcion NULL y 0 indices.
-- ============================================================================

select to_regproc('public.set_server_updated_at') as funcion;   -- esperado: NULL

select indexname
from pg_indexes
where schemaname = 'public' and indexname like '%server_updated%';


-- ============================================================================
-- A4. Tamano de cada tabla (el backfill reescribe las filas una vez; con pocas filas es instantaneo).
-- ============================================================================

select 'decks' as tabla, count(*) as filas from public.decks
union all select 'topics', count(*) from public.topics
union all select 'flashcards', count(*) from public.flashcards
union all select 'card_scheduling', count(*) from public.card_scheduling;
