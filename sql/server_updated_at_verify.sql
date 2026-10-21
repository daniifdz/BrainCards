-- Verificacion de sql/server_updated_at.sql. NO es una migracion: no crea ni cambia nada
-- permanente (V2 hace rollback forzado).
--
-- COMO USARLO: el SQL Editor de Supabase solo muestra el resultado de la ULTIMA sentencia, asi que
-- pega y ejecuta cada bloque V0, V1a, V1b, V1c y V2 POR SEPARADO, en orden, DESPUES de ejecutar
-- sql/server_updated_at.sql y su `notify`. El resultado esperado va en el comentario previo.


-- ============================================================================
-- V0. Prerrequisito de V2: columnas obligatorias de las tablas que V2 rellena.
-- ============================================================================
-- V2 inserta filas minimas en auth.users, decks, topics, flashcards y card_scheduling. Esta consulta
-- lista las columnas NOT NULL sin valor por defecto en el Supabase REAL (el esquema de
-- sql/supabase_schema.sql y sql/card_scheduling.sql puede haber derivado). Esperado (contrastado
-- con sql/card_scheduling.sql, sql/supabase_schema.sql y con sql/parent_owner_rls_verify.sql, que ya
-- paso):
--   auth.users        -> (ninguna, o solo columnas que V2 ya informa: id, aud, role, email)
--   public.decks      -> id, name, created_at, updated_at, user_id
--   public.topics     -> id, deck_id, name, created_at, updated_at, user_id
--   public.flashcards -> id, deck_id, question, answer, card_type, created_at, updated_at, user_id
--   public.card_scheduling -> card_id, user_id, ease_factor, interval_days, repetitions,
--                             next_review, updated_at
-- `server_updated_at` NO debe aparecer (tiene default). Si aparece alguna columna obligatoria
-- distinta de las que V2 informa, hay que anadirla al insert correspondiente de V2 antes de
-- ejecutarlo.



-- ============================================================================
-- V1a. Columna. Esperado: 4 filas, timestamp with time zone, is_nullable = NO y un default con
--      clock_timestamp().
-- ============================================================================

select table_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and column_name = 'server_updated_at'
order by table_name;


-- ============================================================================
-- V1b. Triggers e indices.
-- ============================================================================
-- Esperado: 4 triggers <tabla>_set_server_updated_at (tgenabled = 'O', habilitado) y 4 indices
-- <tabla>_user_server_updated_idx con (user_id, server_updated_at, id) -- card_id en card_scheduling.

select tgrelid::regclass as tabla, tgname, tgenabled
from pg_trigger
where not tgisinternal and tgname like '%set_server_updated_at'
order by tabla;

select tablename, indexname, indexdef
from pg_indexes
where schemaname = 'public' and indexname like '%user_server_updated_idx'
order by tablename;


-- ============================================================================
-- V1c. Funcion del trigger.
-- ============================================================================
-- Esperado: security_definer = false; proconfig con search_path="" ; y los tres EXECUTE en false
-- (public/anon/authenticated no la pueden invocar; los triggers no lo necesitan).

select p.proname,
       p.prosecdef as security_definer,
       p.proconfig,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'set_server_updated_at';


-- ============================================================================
-- V2. Test del trigger con un usuario simulado (rollback forzado).
-- ============================================================================
-- UN solo bloque DO = una unica transaccion: crea un usuario y filas de prueba, se identifica como
-- ese usuario ("set local role authenticated" + request.jwt.claims, igual que
-- sql/parent_owner_rls_verify.sql), ejecuta cada caso y, al final, lanza una excepcion A PROPOSITO con
-- el informe. Esa excepcion hace ROLLBACK de todo: NO queda ningun dato de prueba. Es normal que el
-- SQL Editor lo muestre como ERROR: el informe esta en el mensaje ("RESULTADO DEL TEST"). Esperado:
-- "fallos = 0" y todas las lineas [OK].
--
-- Cada caso es una sentencia que devuelve un timestamp; `kind` dice que se espera de el:
--   gt_old    -> mayor que un valor antiguo (2000-01-01) que el CLIENTE intento fijar: el servidor
--                lo pisa (INSERT, y tambien UPDATE).
--   gt_prev   -> mayor que el valor del caso anterior de la misma tabla: toda escritura avanza la
--                marca, incluido el borrado logico (tombstone).
--   eq_client -> igual al updated_at del cliente: `updated_at` NO se toca.
-- Las filas pasan las politicas de sql/parent_owner_rls.sql (deck, topic y flashcard propios).

do $$
declare
  a uuid := gen_random_uuid();
  d uuid := gen_random_uuid();
  tp uuid := gen_random_uuid();
  c uuid := gen_random_uuid();
  ts_old timestamptz := '2000-01-01 00:00:00+00';
  client_updated timestamptz := '2020-01-01 00:00:00+00';
  t record;
  v timestamptz;
  prev timestamptz;
  ok boolean;
  report text := '';
  fails int := 0;
begin
  -- Preparacion como propietario (postgres, se salta RLS).
  insert into auth.users (id, aud, role, email)
  values (a, 'authenticated', 'authenticated', 'sv-' || a || '@test.invalid');

  perform set_config('request.jwt.claims',
    json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;

  for t in
    select * from (values
      -- decks
      (1, 'decks: INSERT pisa el valor que manda el cliente', 'gt_old',
        format($q$insert into public.decks (id, name, created_at, updated_at, user_id, server_updated_at)
                  values (%L, 't', now(), %L, %L, %L) returning server_updated_at$q$, d, client_updated, a, ts_old)),
      (2, 'decks: UPDATE normal avanza la marca', 'gt_prev',
        format($q$update public.decks set name = 'x' where id = %L returning server_updated_at$q$, d)),
      (3, 'decks: el borrado logico (tombstone) avanza la marca', 'gt_prev',
        format($q$update public.decks set deleted_at = now() where id = %L returning server_updated_at$q$, d)),
      (4, 'decks: el cliente no puede retrasar la marca con un UPDATE', 'gt_prev',
        format($q$update public.decks set server_updated_at = %L where id = %L returning server_updated_at$q$, ts_old, d)),
      (5, 'decks: updated_at (reloj del cliente) no se toca', 'eq_client',
        format('select updated_at from public.decks where id = %L', d)),
      -- topics
      (6, 'topics: INSERT pisa el valor que manda el cliente', 'gt_old',
        format($q$insert into public.topics (id, deck_id, name, created_at, updated_at, user_id, server_updated_at)
                  values (%L, %L, 't', now(), %L, %L, %L) returning server_updated_at$q$, tp, d, client_updated, a, ts_old)),
      (7, 'topics: UPDATE normal avanza la marca', 'gt_prev',
        format($q$update public.topics set name = 'x' where id = %L returning server_updated_at$q$, tp)),
      (8, 'topics: el borrado logico avanza la marca', 'gt_prev',
        format($q$update public.topics set deleted_at = now() where id = %L returning server_updated_at$q$, tp)),
      (9, 'topics: updated_at no se toca', 'eq_client',
        format('select updated_at from public.topics where id = %L', tp)),
      -- flashcards
      (10, 'flashcards: INSERT pisa el valor que manda el cliente', 'gt_old',
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id, server_updated_at)
                  values (%L, %L, 'q', 'a', 'FLIP', now(), %L, %L, %L) returning server_updated_at$q$, c, d, client_updated, a, ts_old)),
      (11, 'flashcards: UPDATE normal avanza la marca', 'gt_prev',
        format($q$update public.flashcards set question = 'x' where id = %L returning server_updated_at$q$, c)),
      (12, 'flashcards: el borrado logico avanza la marca', 'gt_prev',
        format($q$update public.flashcards set deleted_at = now() where id = %L returning server_updated_at$q$, c)),
      (13, 'flashcards: updated_at no se toca', 'eq_client',
        format('select updated_at from public.flashcards where id = %L', c)),
      -- card_scheduling
      (14, 'card_scheduling: INSERT pisa el valor que manda el cliente', 'gt_old',
        format($q$insert into public.card_scheduling (card_id, user_id, ease_factor, interval_days, repetitions, next_review, updated_at, server_updated_at)
                  values (%L, %L, 2.5, 1, 1, now(), %L, %L) returning server_updated_at$q$, c, a, client_updated, ts_old)),
      (15, 'card_scheduling: UPDATE normal avanza la marca', 'gt_prev',
        format($q$update public.card_scheduling set ease_factor = 2.0 where card_id = %L returning server_updated_at$q$, c)),
      (16, 'card_scheduling: updated_at no se toca', 'eq_client',
        format('select updated_at from public.card_scheduling where card_id = %L', c))
    ) as x(n, label, kind, stmt)
    order by n
  loop
    begin
      execute t.stmt into v;
      if t.kind = 'gt_old' then
        ok := v > ts_old;
      elsif t.kind = 'gt_prev' then
        ok := v > prev;
      else
        ok := v = client_updated;
      end if;
      if t.kind in ('gt_old', 'gt_prev') then
        prev := v;
      end if;
      if ok then
        report := report || E'\n[OK]   ' || t.label;
      else
        fails := fails + 1;
        report := report || E'\n[FAIL] ' || t.label || ' (valor = ' || coalesce(v::text, 'NULL') || ')';
      end if;
    exception when others then
      fails := fails + 1;
      report := report || E'\n[FAIL] error inesperado en "' || t.label || '": ' || sqlerrm;
    end;
  end loop;

  reset role;

  -- Excepcion final a proposito: fuerza el ROLLBACK de todo lo creado arriba.
  raise exception E'RESULTADO DEL TEST (rollback forzado, no se ha guardado nada) -- fallos = %\n%', fails, report;
end;
$$;
