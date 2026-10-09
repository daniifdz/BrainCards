-- Bloque F (borrado de cuenta). Permite que un usuario autenticado borre su propia
-- cuenta de auth.users desde la app, via RPC (Postgrest: client.postgrest.rpc(...)).
-- El ON DELETE CASCADE de sql/supabase_schema.sql se encarga de borrar en cascada
-- sus filas en decks/topics/flashcards/reviewlog -- esta funcion solo borra el
-- usuario, no toca esas tablas directamente.
--
-- security definer + search_path vacio: corre con los privilegios de quien la creo
-- (no del usuario que la invoca), necesario para poder borrar de auth.users; el
-- search_path vacio evita que una funcion/tabla con el mismo nombre en otro schema
-- se cuele en la resolucion de nombres dentro del body.
--
-- auth.uid() en vez de un parametro de id: un usuario solo puede borrarse a si
-- mismo, nunca a otro, sin necesidad de ninguna comprobacion adicional.
--
-- No se aplica con migraciones automaticas -- se ejecuta a mano una vez en el SQL
-- Editor de Supabase, igual que sql/supabase_schema.sql. Ya esta aplicada en el
-- proyecto real; este archivo es solo para que quede versionada en el repo.

create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from auth.users where id = auth.uid();
end;
$$;

revoke execute on function public.delete_own_account() from public, anon;
grant execute on function public.delete_own_account() to authenticated;
