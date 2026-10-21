-- Verificacion de sql/parent_owner_rls.sql. NO es una migracion: no crea ni cambia nada
-- permanente (V4 hace rollback forzado).
--
-- COMO USARLO: el SQL Editor de Supabase solo muestra el resultado de la ULTIMA sentencia, asi que
-- pega y ejecuta cada bloque V0..V4 POR SEPARADO, en orden, DESPUES de ejecutar
-- sql/parent_owner_rls.sql. El resultado esperado va en el comentario previo.


-- ============================================================================
-- V0. Prerrequisito de V4: columnas obligatorias de las tablas que V4 rellena.
-- ============================================================================
-- V4 inserta filas minimas en auth.users, public.decks, public.topics y public.flashcards. Esta
-- consulta lista las columnas NOT NULL sin valor por defecto en el Supabase REAL (el esquema de
-- sql/supabase_schema.sql puede haber derivado). Esperado:
--   auth.users        -> (ninguna, o solo columnas que V4 ya informa: id)
--   public.decks      -> id, name, created_at, updated_at, user_id
--   public.topics     -> id, deck_id, name, created_at, updated_at, user_id
--   public.flashcards -> id, deck_id, question, answer, card_type, created_at, updated_at, user_id
-- Si aparece alguna columna obligatoria distinta (auth.users: V4 informa id, aud, role, email),
-- hay que anadirla al insert correspondiente de V4 antes de ejecutarlo.

select table_schema, table_name, column_name, data_type
from information_schema.columns
where ((table_schema = 'auth' and table_name = 'users')
    or (table_schema = 'public' and table_name in ('decks', 'topics', 'flashcards')))
  and is_nullable = 'NO'
  and column_default is null
  and is_generated = 'NEVER'
  and is_identity = 'NO'
order by table_schema, table_name, ordinal_position;


-- ============================================================================
-- V1. Politicas.
-- ============================================================================
-- Esperado: en topics y flashcards, <tabla>_insert_own y <tabla>_update_own con el exists del deck
-- en with_check (y del topic, solo en flashcards). Las select y las de decks, sin cambios. Ninguna
-- politica DELETE. Ninguna otra politica permisiva de INSERT/UPDATE.

select tablename, policyname, permissive, cmd, qual, with_check
from pg_policies
where schemaname = 'public' and tablename in ('decks', 'topics', 'flashcards')
order by tablename, cmd, policyname;


-- ============================================================================
-- V2. Permisos de tabla.
-- ============================================================================
-- Esperado: SOLO authenticated, con INSERT, SELECT y UPDATE en decks, topics y flashcards (sin
-- DELETE, TRUNCATE, REFERENCES ni TRIGGER), y ninguna fila para anon. Si aplicaste la PARTE 2 del
-- script (reviewlog), reviewlog sigue el mismo criterio; si no, saldran sus permisos antiguos.

select table_name, grantee, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('decks', 'topics', 'flashcards', 'reviewlog')
  and grantee in ('anon', 'authenticated')
order by table_name, grantee, privilege_type;


-- ============================================================================
-- V3. RLS activo (incluye reviewlog si existe).
-- ============================================================================
-- Esperado: relrowsecurity = true en todas las filas. to_regclass da NULL (sin error) si
-- reviewlog no existe.

select relname, relrowsecurity, relforcerowsecurity
from pg_class
where oid in (
  'public.decks'::regclass,
  'public.topics'::regclass,
  'public.flashcards'::regclass,
  to_regclass('public.reviewlog')
);


-- ============================================================================
-- V4. Test de RLS con dos usuarios simulados (rollback forzado).
-- ============================================================================
-- UN solo bloque DO = una unica transaccion: crea dos usuarios, decks, topics y flashcards de
-- prueba, cambia de rol con "set local role" y de identidad con request.jwt.claims, ejecuta cada
-- caso y, al final, lanza una excepcion A PROPOSITO con el informe. Esa excepcion hace ROLLBACK de
-- todo: NO queda ningun dato de prueba (incluso un TRUNCATE que se permitiera por error se
-- revertiria). Es normal que el SQL Editor lo muestre como ERROR: el informe esta en el mensaje
-- ("RESULTADO DEL TEST RLS"). Esperado: "fallos = 0" y todas las lineas [OK].
--
-- Cada caso es una sentencia que debe tener exito (should_succeed = true) o ser denegada con
-- 42501 insufficient_privilege (RLS o falta de permiso). Cualquier otro error (p. ej. una
-- violacion de FK) cuenta como FALLO: la RLS debe saltar ANTES que la FK.
-- Los casos 12-14 van en orden: mover una flashcard a otro deck propio, borrar ese deck
-- logicamente y subir una flashcard a ese deck tombstone (el padre sigue existiendo).

do $$
declare
  a uuid := gen_random_uuid();
  b uuid := gen_random_uuid();
  deck_a uuid := gen_random_uuid();
  deck_a2 uuid := gen_random_uuid();
  deck_b uuid := gen_random_uuid();
  topic_a uuid := gen_random_uuid();
  topic_b uuid := gen_random_uuid();
  card_a uuid := gen_random_uuid();
  card_b uuid := gen_random_uuid();
  ghost uuid := gen_random_uuid();   -- id que no existe en ninguna tabla
  t record;
  report text := '';
  fails int := 0;
  n int;
  tbl text;
begin
  -- Preparacion como propietario (postgres, se salta RLS).
  insert into auth.users (id, aud, role, email) values
    (a, 'authenticated', 'authenticated', 'rls-a-' || a || '@test.invalid'),
    (b, 'authenticated', 'authenticated', 'rls-b-' || b || '@test.invalid');
  insert into public.decks (id, name, created_at, updated_at, user_id) values
    (deck_a, 't', now(), now(), a),
    (deck_a2, 't', now(), now(), a),
    (deck_b, 't', now(), now(), b);
  insert into public.topics (id, deck_id, name, created_at, updated_at, user_id) values
    (topic_a, deck_a, 't', now(), now(), a),
    (topic_b, deck_b, 't', now(), now(), b);
  insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id) values
    (card_a, deck_a, 'q', 'a', 'FLIP', now(), now(), a),
    (card_b, deck_b, 'q', 'a', 'FLIP', now(), now(), b);

  ---------------------------------------------------------------- usuario A
  perform set_config('request.jwt.claims',
    json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;

  for t in
    select * from (values
      (1, 'A inserta una flashcard en su propio deck', true,
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_a, a)),
      (2, 'A inserta una flashcard con su deck y su topic', true,
        format($q$insert into public.flashcards (id, deck_id, topic_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_a, topic_a, a)),
      (3, 'A NO puede colgar una flashcard (user_id = A) del deck de B', false,
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_b, a)),
      (4, 'A NO puede usar el topic de B en una flashcard de su deck', false,
        format($q$insert into public.flashcards (id, deck_id, topic_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_a, topic_b, a)),
      (5, 'A NO puede insertar una flashcard a nombre de B', false,
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_a, b)),
      (6, 'A NO puede insertar con un deck que no existe (padre aun sin subir)', false,
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, ghost, a)),
      (7, 'A NO puede insertar con un topic que no existe', false,
        format($q$insert into public.flashcards (id, deck_id, topic_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_a, ghost, a)),
      (8, 'A NO puede mover su flashcard al deck de B (UPDATE)', false,
        format('update public.flashcards set deck_id = %L where id = %L', deck_b, card_a)),
      (9, 'A NO puede poner el topic de B a su flashcard (UPDATE)', false,
        format('update public.flashcards set topic_id = %L where id = %L', topic_b, card_a)),
      (10, 'A NO puede traspasar su flashcard a B (user_id)', false,
        format('update public.flashcards set user_id = %L where id = %L', b, card_a)),
      (11, 'A NO puede mover su flashcard al deck de B con un upsert', false,
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (%L, %L, 'q', 'a', 'FLIP', now(), now(), %L)
                  on conflict (id) do update set deck_id = excluded.deck_id$q$, card_a, deck_b, a)),
      (12, 'A puede mover su flashcard a otro deck propio', true,
        format('update public.flashcards set deck_id = %L where id = %L', deck_a2, card_a)),
      (13, 'A puede borrar logicamente su deck (deleted_at)', true,
        format('update public.decks set deleted_at = now() where id = %L', deck_a2)),
      (14, 'A puede subir una flashcard a un deck borrado logicamente (el padre sigue existiendo)', true,
        format($q$insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 'q', 'a', 'FLIP', now(), now(), %L)$q$, deck_a2, a)),
      (15, 'A inserta un topic en su deck', true,
        format($q$insert into public.topics (id, deck_id, name, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 't', now(), now(), %L)$q$, deck_a, a)),
      (16, 'A NO puede insertar un topic en el deck de B', false,
        format($q$insert into public.topics (id, deck_id, name, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 't', now(), now(), %L)$q$, deck_b, a)),
      (17, 'A NO puede insertar un topic en un deck que no existe', false,
        format($q$insert into public.topics (id, deck_id, name, created_at, updated_at, user_id)
                  values (gen_random_uuid(), %L, 't', now(), now(), %L)$q$, ghost, a)),
      (18, 'A NO puede mover su topic al deck de B (UPDATE)', false,
        format('update public.topics set deck_id = %L where id = %L', deck_b, topic_a)),
      (19, 'A puede renombrar su topic', true,
        format($q$update public.topics set name = 'otro' where id = %L$q$, topic_a)),
      (20, 'A NO puede borrar fisicamente una flashcard (sin permiso DELETE)', false,
        format('delete from public.flashcards where id = %L', card_a)),
      (21, 'A NO puede borrar fisicamente un deck (sin permiso DELETE)', false,
        format('delete from public.decks where id = %L', deck_a)),
      (22, 'authenticated NO puede TRUNCATE flashcards', false, 'truncate public.flashcards'),
      (23, 'authenticated NO puede TRUNCATE topics', false, 'truncate public.topics'),
      (24, 'authenticated NO puede TRUNCATE decks', false, 'truncate public.decks')
    ) as x(n, label, should_succeed, stmt)
    order by n
  loop
    begin
      execute t.stmt;
      if t.should_succeed then
        report := report || E'\n[OK]   ' || t.label;
      else
        fails := fails + 1;
        report := report || E'\n[FAIL] (se permitio) ' || t.label;
      end if;
    exception when insufficient_privilege then
      if t.should_succeed then
        fails := fails + 1;
        report := report || E'\n[FAIL] (denegado) ' || t.label || ': ' || sqlerrm;
      else
        report := report || E'\n[OK]   ' || t.label;
      end if;
    when others then
      fails := fails + 1;
      report := report || E'\n[FAIL] error inesperado en "' || t.label || '": ' || sqlerrm;
    end;
  end loop;

  ---------------------------------------------------------------- usuario B
  perform set_config('request.jwt.claims',
    json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;

  begin
    insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
    values (gen_random_uuid(), deck_b, 'q', 'a', 'FLIP', now(), now(), b);
    report := report || E'\n[OK]   B inserta una flashcard en su propio deck';
  exception when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] B no pudo insertar en su deck: ' || sqlerrm;
  end;

  select count(*) into n from public.flashcards;
  if n = 2 then
    report := report || E'\n[OK]   B ve exactamente 2 flashcards (card_b y la que acaba de insertar), ninguna de A';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] B ve ' || n || ' flashcards (esperado 2)';
  end if;

  ---------------------------------------------------------------- anonimo (sin sesion)
  perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
  set local role anon;

  foreach tbl in array array['decks', 'topics', 'flashcards'] loop
    begin
      execute format('select count(*) from public.%I', tbl) into n;
      if n = 0 then
        report := report || E'\n[OK]   anon ve 0 filas en ' || tbl;
      else
        fails := fails + 1;
        report := report || E'\n[FAIL] anon ve ' || n || ' filas en ' || tbl;
      end if;
    exception when insufficient_privilege then
      report := report || E'\n[OK]   anon no tiene permiso de lectura en ' || tbl;
    end;
  end loop;

  begin
    insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id)
    values (gen_random_uuid(), deck_b, 'q', 'a', 'FLIP', now(), now(), b);
    fails := fails + 1;
    report := report || E'\n[FAIL] anon pudo insertar una flashcard';
  exception when insufficient_privilege then
    report := report || E'\n[OK]   anon NO puede escribir';
  when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] error inesperado (anon escribe): ' || sqlerrm;
  end;

  ---------------------------------------------------------------- vuelta a propietario
  reset role;

  -- Excepcion final a proposito: fuerza el ROLLBACK de todo lo creado arriba.
  raise exception E'RESULTADO DEL TEST RLS (rollback forzado, no se ha guardado nada) -- fallos = %\n%', fails, report;
end;
$$;
