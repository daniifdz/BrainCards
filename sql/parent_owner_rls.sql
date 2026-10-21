-- Endurecimiento del servidor de decks, topics y flashcards (invariante de dueno de los padres),
-- igual que se hizo con card_scheduling (sql/card_scheduling.sql).
--
-- PROBLEMA: las politicas de INSERT/UPDATE de topics y flashcards solo comprobaban
-- auth.uid() = user_id. La FK de Postgres comprueba que el padre existe pero NO de quien es (salta
-- RLS), asi que una fila propia podia colgar del deck o topic de OTRA cuenta (asi llegaron al
-- servidor flashcards de una cuenta colgando de un mazo de otra, que el pull luego no podia
-- guardar en local).
--
-- AHORA:
--   - topics:     INSERT/UPDATE exigen auth.uid() = user_id Y el deck existe y es del mismo usuario.
--   - flashcards: INSERT/UPDATE exigen auth.uid() = user_id Y el deck existe y es del mismo usuario
--                 Y (topic_id es null O el topic existe y es del mismo usuario).
--   - Los padres borrados logicamente (deleted_at) SIGUEN existiendo: no se mira deleted_at.
--   - Permisos: anon sin nada; authenticated solo select, insert y update (sin delete, truncate,
--     references ni trigger).
--
-- ORDEN DE PUSH DEL CLIENTE: decks -> topics -> flashcards, una peticion por fila. El hijo se sube
-- cuando su padre ya esta confirmado. Si el padre aun no esta en el servidor (su push fallo), el
-- WITH CHECK falla (42501, HTTP 403 en PostgREST): el cliente lo trata como rechazo por fila (no
-- como fallo de red), la fila queda pendiente y se reintenta en el siguiente sync. Antes la FK daba
-- un 409; para el cliente es lo mismo.
--
-- DELETE: la app solo hace borrado logico (deleted_at) y no hay politicas DELETE. Se retira el
-- permiso DELETE a authenticated. El borrado de cuenta (sql/delete_own_account.sql, security
-- definer) y las FK en cascada corren con privilegios de propietario y no dependen de estos
-- permisos. TRAS APLICAR, PRUEBA "Eliminar cuenta" CON UNA CUENTA DE PRUEBA.
--
-- COMO USARLO (no se aplica con migraciones automaticas; se ejecuta A MANO en el SQL Editor):
--   1. Ejecuta sql/parent_owner_precheck.sql: P5 debe dar 0 en las tres filas. Guarda P1 y P2.
--   2. Ejecuta la PARTE 1 (todo hasta el primer COMMIT) de una vez: es una sola transaccion; si algo
--      falla, o aparece una politica permisiva inesperada, no se guarda nada.
--   3. Solo si P2 mostro permisos de reviewlog: ejecuta la PARTE 2.
--   4. Ejecuta sql/parent_owner_rls_verify.sql (V0..V4) y prueba el sync en tus dispositivos.
--   Rollback: sql/parent_owner_rls_rollback.sql.
--
-- Es idempotente: se puede volver a ejecutar (drop policy if exists + create policy).
-- DROP/CREATE POLICY y REVOKE toman un bloqueo breve sobre las tablas: mejor sin un sync en curso.


-- ============================================================================
-- PARTE 1. decks, topics, flashcards (una transaccion)
-- ============================================================================

begin;

-- ----- topics: INSERT y UPDATE exigen deck propio ----------------------------
-- (deck_id sin cualificar dentro del exists es la columna de topics: decks no tiene deck_id.)

drop policy if exists "topics_insert_own" on public.topics;
create policy "topics_insert_own"
  on public.topics for insert
  with check (
    auth.uid() = user_id
    and exists (select 1 from public.decks d where d.id = deck_id and d.user_id = auth.uid())
  );

drop policy if exists "topics_update_own" on public.topics;
create policy "topics_update_own"
  on public.topics for update
  using (auth.uid() = user_id)
  with check (
    auth.uid() = user_id
    and exists (select 1 from public.decks d where d.id = deck_id and d.user_id = auth.uid())
  );

-- ----- flashcards: INSERT y UPDATE exigen deck propio y topic propio (si lo hay) ----------
-- (deck_id / topic_id sin cualificar son las columnas de flashcards; decks y topics no las tienen.)

drop policy if exists "flashcards_insert_own" on public.flashcards;
create policy "flashcards_insert_own"
  on public.flashcards for insert
  with check (
    auth.uid() = user_id
    and exists (select 1 from public.decks d where d.id = deck_id and d.user_id = auth.uid())
    and (topic_id is null
         or exists (select 1 from public.topics t where t.id = topic_id and t.user_id = auth.uid()))
  );

drop policy if exists "flashcards_update_own" on public.flashcards;
create policy "flashcards_update_own"
  on public.flashcards for update
  using (auth.uid() = user_id)
  with check (
    auth.uid() = user_id
    and exists (select 1 from public.decks d where d.id = deck_id and d.user_id = auth.uid())
    and (topic_id is null
         or exists (select 1 from public.topics t where t.id = topic_id and t.user_id = auth.uid()))
  );

-- ----- Permisos --------------------------------------------------------------
-- anon: nada. authenticated: solo select, insert y update (RLS sigue aplicando encima).

revoke all on public.decks, public.topics, public.flashcards from anon;
revoke delete, truncate, references, trigger
  on public.decks, public.topics, public.flashcards from authenticated;

-- ----- Seguro ----------------------------------------------------------------
-- Si existieran OTRAS politicas PERMISIVAS de INSERT/UPDATE/ALL en topics/flashcards (con otro
-- nombre), se combinarian con OR y anularian el endurecimiento. En ese caso aborta TODA la
-- transaccion (nada queda aplicado): revisa P1 y borra esas politicas antes de volver a ejecutar.

do $$
declare
  extra int;
begin
  select count(*) into extra
  from pg_policies
  where schemaname = 'public'
    and tablename in ('topics', 'flashcards')
    and permissive = 'PERMISSIVE'
    and cmd in ('INSERT', 'UPDATE', 'ALL')
    and policyname not in ('topics_insert_own', 'topics_update_own',
                           'flashcards_insert_own', 'flashcards_update_own');
  if extra > 0 then
    raise exception 'Hay % politica(s) permisiva(s) de INSERT/UPDATE/ALL no esperadas en topics/flashcards: revisa P1 y borralas antes de aplicar', extra;
  end if;
end;
$$;

commit;


-- ============================================================================
-- PARTE 2. reviewlog -- BLOQUE APARTE
-- ============================================================================
-- EJECUTALO SOLO SI P2 (sql/parent_owner_precheck.sql) MOSTRO PERMISOS DE reviewlog. Si la tabla no
-- existe en el servidor, un revoke fallaria; por eso NO forma parte de la transaccion de la PARTE 1
-- (abortaria el endurecimiento principal). reviewlog no se sincroniza todavia; se le quitan los
-- mismos permisos peligrosos que a las demas (TRUNCATE se salta RLS).

begin;

revoke all on public.reviewlog from anon;
revoke delete, truncate, references, trigger on public.reviewlog from authenticated;

commit;
