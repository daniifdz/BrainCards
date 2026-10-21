-- Marca de agua de servidor para el pull incremental: server_updated_at en decks, topics, flashcards
-- y card_scheduling.
--
-- POR QUE: hoy cada sync baja las cuatro tablas enteras. Un incremental por `updated_at` NO es
-- seguro porque ese campo lo escribe el cliente (sql/supabase_schema.sql y sql/card_scheduling.sql
-- lo declaran `not null` sin default ni trigger): un dispositivo que estuvo sin red y sube el
-- viernes una edicion del martes llega con updated_at < la marca que otro dispositivo ya avanzo el
-- jueves, y ese otro dispositivo no la volveria a bajar nunca (ni hace falta un reloj desfasado).
--
-- QUE HACE: anade `server_updated_at timestamptz` NOT NULL a las cuatro tablas, rellenada por un
-- trigger BEFORE INSERT OR UPDATE FOR EACH ROW con clock_timestamp(). El valor lo pone SIEMPRE el
-- servidor: pisa cualquier valor que mande el cliente (tambien en un UPDATE). Los borrados logicos
-- (deleted_at) son UPDATE, asi que tambien la mueven y se bajan como cualquier edicion.
--   - clock_timestamp() y no now(): now() es la hora de INICIO de la transaccion; una transaccion
--     que empieza antes pero confirma despues podria quedar con un valor menor que la marca ya
--     avanzada y perderse. Con clock_timestamp() y sentencias de una fila el desfase es de
--     milisegundos, y ademas el cliente pide desde marca - 2 minutos (solape).
--   - `updated_at` NO se toca: sigue siendo el reloj del cliente y sigue mandando en el LWW.
--
-- CLIENTES VIEJOS: siguen funcionando. Su DTO ignora claves desconocidas (supabase-kt
-- SupabaseClientBuilder.defaultSerializer = Json { ignoreUnknownKeys = true }) y sus upserts no
-- mandan la columna: la rellena el trigger. RLS y permisos no cambian (las politicas SELECT, INSERT
-- y UPDATE de sql/parent_owner_rls.sql siguen igual; la columna nueva entra en los grants de tabla).
--
-- ORDEN DE DESPLIEGUE (obligatorio): PRIMERO este SQL, DESPUES el cliente que pide por
-- server_updated_at. Un cliente nuevo contra un servidor SIN la columna recibiria un 400 en cada
-- pull. Para volver atras: primero el cliente anterior, despues sql/server_updated_at_rollback.sql.
--
-- COMO USARLO (no se aplica con migraciones automaticas; se ejecuta A MANO en el SQL Editor):
--   1. Ejecuta sql/server_updated_at_precheck.sql (A1..A4).
--   2. Ejecuta ESTE script de una vez (una transaccion), SIN un sync en curso: ALTER TABLE y
--      CREATE TRIGGER toman un bloqueo breve sobre las tablas.
--   3. Ejecuta el `notify` del final (recarga el esquema de PostgREST).
--   4. Ejecuta sql/server_updated_at_verify.sql: V0, V1a, V1b, V1c y V2. V0 (columnas obligatorias)
--      se puede ejecutar ANTES de aplicar este script.
--
-- Es idempotente: se puede volver a ejecutar (if not exists / drop trigger if exists).

begin;

-- ----- Funcion del trigger ---------------------------------------------------
-- search_path vacio, como delete_own_account: sin resolucion de nombres ambigua. No es security
-- definer: no lee ni escribe otras tablas, solo asigna un campo de la fila nueva.

create or replace function public.set_server_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.server_updated_at := clock_timestamp();
  return new;
end;
$$;

-- Los triggers no necesitan que el rol tenga EXECUTE sobre la funcion; se retira para que no quede
-- como funcion expuesta.
revoke execute on function public.set_server_updated_at() from public, anon, authenticated;

-- ----- Columna: nullable -> backfill -> NOT NULL + default ----------------------

alter table public.decks           add column if not exists server_updated_at timestamptz;
alter table public.topics          add column if not exists server_updated_at timestamptz;
alter table public.flashcards      add column if not exists server_updated_at timestamptz;
alter table public.card_scheduling add column if not exists server_updated_at timestamptz;

-- Backfill: las filas existentes quedan con la hora de esta migracion. Ningun cliente tiene todavia
-- marca de agua (el primer sync de cada uno sera completo), asi que no se pierde ni se repite nada.
update public.decks           set server_updated_at = clock_timestamp() where server_updated_at is null;
update public.topics          set server_updated_at = clock_timestamp() where server_updated_at is null;
update public.flashcards      set server_updated_at = clock_timestamp() where server_updated_at is null;
update public.card_scheduling set server_updated_at = clock_timestamp() where server_updated_at is null;

alter table public.decks
  alter column server_updated_at set not null,
  alter column server_updated_at set default clock_timestamp();
alter table public.topics
  alter column server_updated_at set not null,
  alter column server_updated_at set default clock_timestamp();
alter table public.flashcards
  alter column server_updated_at set not null,
  alter column server_updated_at set default clock_timestamp();
alter table public.card_scheduling
  alter column server_updated_at set not null,
  alter column server_updated_at set default clock_timestamp();

-- ----- Triggers --------------------------------------------------------------

drop trigger if exists decks_set_server_updated_at on public.decks;
create trigger decks_set_server_updated_at
  before insert or update on public.decks
  for each row execute function public.set_server_updated_at();

drop trigger if exists topics_set_server_updated_at on public.topics;
create trigger topics_set_server_updated_at
  before insert or update on public.topics
  for each row execute function public.set_server_updated_at();

drop trigger if exists flashcards_set_server_updated_at on public.flashcards;
create trigger flashcards_set_server_updated_at
  before insert or update on public.flashcards
  for each row execute function public.set_server_updated_at();

drop trigger if exists card_scheduling_set_server_updated_at on public.card_scheduling;
create trigger card_scheduling_set_server_updated_at
  before insert or update on public.card_scheduling
  for each row execute function public.set_server_updated_at();

-- ----- Indices del pull por cursor -------------------------------------------
-- El pull pide `user_id = ? and (server_updated_at, clave) > cursor order by server_updated_at, clave`.
-- La clave de desempate es `id` (decks, topics, flashcards) y `card_id` (card_scheduling).

create index if not exists decks_user_server_updated_idx
  on public.decks (user_id, server_updated_at, id);
create index if not exists topics_user_server_updated_idx
  on public.topics (user_id, server_updated_at, id);
create index if not exists flashcards_user_server_updated_idx
  on public.flashcards (user_id, server_updated_at, id);
create index if not exists card_scheduling_user_server_updated_idx
  on public.card_scheduling (user_id, server_updated_at, card_id);

commit;

-- Recarga el esquema de PostgREST para que la API vea la columna nueva sin esperar. Ejecutalo DESPUES
-- del commit (si la transaccion de arriba fallara, no hay nada que recargar).
notify pgrst, 'reload schema';
