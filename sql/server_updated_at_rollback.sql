-- ROLLBACK de sql/server_updated_at.sql: quita triggers, funcion, indices y la columna
-- server_updated_at de decks, topics, flashcards y card_scheduling.
--
-- ORDEN: PRIMERO vuelve al cliente anterior (el que no pide por server_updated_at); un cliente nuevo
-- contra un servidor sin la columna recibiria un 400 en cada pull. DESPUES ejecuta este script.
-- El rollback no toca `updated_at` ni ningun dato de usuario.
--
-- COMO USARLO: ejecuta el bloque de una vez (una transaccion) y despues el `notify`. Para comprobarlo,
-- vuelve a ejecutar A1 y A2 de sql/server_updated_at_precheck.sql (deben dar 0 filas).

begin;

drop trigger if exists decks_set_server_updated_at on public.decks;
drop trigger if exists topics_set_server_updated_at on public.topics;
drop trigger if exists flashcards_set_server_updated_at on public.flashcards;
drop trigger if exists card_scheduling_set_server_updated_at on public.card_scheduling;

drop function if exists public.set_server_updated_at();

drop index if exists public.decks_user_server_updated_idx;
drop index if exists public.topics_user_server_updated_idx;
drop index if exists public.flashcards_user_server_updated_idx;
drop index if exists public.card_scheduling_user_server_updated_idx;

alter table public.decks           drop column if exists server_updated_at;
alter table public.topics          drop column if exists server_updated_at;
alter table public.flashcards      drop column if exists server_updated_at;
alter table public.card_scheduling drop column if exists server_updated_at;

commit;

-- Recarga el esquema de PostgREST (ejecutalo despues del commit).
notify pgrst, 'reload schema';
