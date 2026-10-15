-- Verificacion de sql/card_scheduling.sql. NO es una migracion: no crea ni cambia nada
-- permanente (V7 hace rollback forzado).
--
-- COMO USARLO: el SQL Editor de Supabase solo muestra el resultado de la ULTIMA
-- sentencia, asi que pega y ejecuta cada bloque V0..V7 POR SEPARADO, en orden, DESPUES
-- de ejecutar sql/card_scheduling.sql. El resultado esperado va en el comentario previo.


-- ============================================================================
-- V0. Prerrequisito de V7: columnas obligatorias de las tablas que V7 rellena.
-- ============================================================================
-- V7 inserta filas minimas en auth.users, public.decks y public.flashcards. Esta
-- consulta lista las columnas NOT NULL sin valor por defecto de esas tres tablas en el
-- Supabase REAL (el esquema de sql/supabase_schema.sql puede haber derivado). Esperado:
--   auth.users        -> (ninguna, o solo columnas que V7 ya informa: id)
--   public.decks      -> id, name, created_at, updated_at, user_id
--   public.flashcards -> id, deck_id, question, answer, card_type, created_at, updated_at, user_id
-- Si aparece alguna columna obligatoria distinta de las que V7 informa
-- (auth.users: id, aud, role, email; decks y flashcards: las de arriba), hay que anadirla
-- al insert correspondiente de V7 antes de ejecutarlo.

select table_schema, table_name, column_name, data_type
from information_schema.columns
where ((table_schema = 'auth' and table_name = 'users')
    or (table_schema = 'public' and table_name in ('decks', 'flashcards')))
  and is_nullable = 'NO'
  and column_default is null
  and is_generated = 'NEVER'
  and is_identity = 'NO'
order by table_schema, table_name, ordinal_position;


-- ============================================================================
-- V1. Columnas y tipos.
-- ============================================================================
-- Esperado (en este orden):
--   card_id         uuid                      NO
--   user_id         uuid                      NO
--   ease_factor     double precision          NO
--   interval_days   integer                   NO
--   repetitions     integer                   NO
--   next_review     timestamp with time zone  NO
--   updated_at      timestamp with time zone  NO
--   last_synced_at  timestamp with time zone  YES

select column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and table_name = 'card_scheduling'
order by ordinal_position;


-- ============================================================================
-- V2. Constraints y foreign keys.
-- ============================================================================
-- Esperado: 3 filas.
--   card_scheduling_pkey          p  PRIMARY KEY (card_id)
--   card_scheduling_card_id_fkey  f  FOREIGN KEY (card_id) REFERENCES flashcards(id) ON DELETE CASCADE   (confdeltype = 'c')
--   card_scheduling_user_id_fkey  f  FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE   (confdeltype = 'c')

select conname, contype, confrelid::regclass as referencia, confdeltype,
       pg_get_constraintdef(oid) as definicion
from pg_constraint
where conrelid = 'public.card_scheduling'::regclass
order by contype, conname;


-- ============================================================================
-- V3. Indices.
-- ============================================================================
-- Esperado: card_scheduling_pkey (card_id) y
--   card_scheduling_user_id_idx ... USING btree (user_id, updated_at)

select indexname, indexdef
from pg_indexes
where schemaname = 'public' and tablename = 'card_scheduling'
order by indexname;


-- ============================================================================
-- V4. Politicas.
-- ============================================================================
-- Esperado: 4 filas (DELETE, INSERT, SELECT, UPDATE), todas con auth.uid() = user_id;
-- INSERT y UPDATE ademas con el exists sobre flashcards en with_check.

select policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public' and tablename = 'card_scheduling'
order by cmd, policyname;


-- ============================================================================
-- V5. RLS activo.
-- ============================================================================
-- Esperado: relrowsecurity = true.

select relname, relrowsecurity, relforcerowsecurity
from pg_class
where oid = 'public.card_scheduling'::regclass;


-- ============================================================================
-- V6. Permisos de tabla.
-- ============================================================================
-- Esperado: solo authenticated, con SELECT, INSERT, UPDATE y DELETE (sin TRUNCATE,
-- REFERENCES ni TRIGGER, y sin ninguna fila para anon: se revocaron en la seccion 2 de
-- card_scheduling.sql). V7 comprueba ademas que anon ve 0 filas o no tiene permiso.

select grantee, privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and table_name = 'card_scheduling'
order by grantee, privilege_type;


-- ============================================================================
-- V7. Test de RLS con dos usuarios simulados (rollback forzado).
-- ============================================================================
-- UN solo bloque DO = una unica transaccion: crea dos usuarios, mazos y tarjetas de
-- prueba, cambia de rol con "set local role" y de identidad con request.jwt.claims,
-- comprueba cada caso y, al final, lanza una excepcion A PROPOSITO con el informe. Esa
-- excepcion hace ROLLBACK de todo: NO queda ningun dato de prueba. Es normal que el SQL
-- Editor lo muestre como ERROR: el informe esta en el mensaje ("RESULTADO DEL TEST RLS").
-- Esperado: "fallos = 0" y todas las lineas [OK].
-- Columnas contrastadas con sql/supabase_schema.sql, DeckDto y FlashcardDto
-- (decks: id, name, created_at, updated_at, user_id; flashcards: id, deck_id, question,
-- answer, card_type 'FLIP', created_at, updated_at, user_id). Ejecuta V0 antes por si el
-- Supabase real difiere.

do $$
declare
  a uuid := gen_random_uuid();
  b uuid := gen_random_uuid();
  deck_a uuid := gen_random_uuid();
  deck_b uuid := gen_random_uuid();
  card_a uuid := gen_random_uuid();
  card_b uuid := gen_random_uuid();
  report text := '';
  fails int := 0;
  n int;
begin
  -- Preparacion como propietario (postgres, se salta RLS).
  insert into auth.users (id, aud, role, email) values
    (a, 'authenticated', 'authenticated', 'rls-a-' || a || '@test.invalid'),
    (b, 'authenticated', 'authenticated', 'rls-b-' || b || '@test.invalid');
  insert into public.decks (id, name, created_at, updated_at, user_id) values
    (deck_a, 'test', now(), now(), a),
    (deck_b, 'test', now(), now(), b);
  insert into public.flashcards (id, deck_id, question, answer, card_type, created_at, updated_at, user_id) values
    (card_a, deck_a, 'q', 'a', 'FLIP', now(), now(), a),
    (card_b, deck_b, 'q', 'a', 'FLIP', now(), now(), b);

  ---------------------------------------------------------------- usuario A
  perform set_config('request.jwt.claims',
    json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;

  begin
    insert into public.card_scheduling (card_id, user_id, ease_factor, interval_days, repetitions, next_review, updated_at)
    values (card_a, a, 2.5, 1, 1, now(), now());
    report := report || E'\n[OK]   A inserta el scheduling de su propia tarjeta';
  exception when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] A no pudo insertar el suyo: ' || sqlerrm;
  end;

  begin
    insert into public.card_scheduling (card_id, user_id, ease_factor, interval_days, repetitions, next_review, updated_at)
    values (card_b, a, 2.5, 1, 1, now(), now());
    fails := fails + 1;
    report := report || E'\n[FAIL] A pudo colgar una fila (user_id = A) de la tarjeta de B';
  exception when insufficient_privilege then
    report := report || E'\n[OK]   A NO puede insertar scheduling sobre la tarjeta de B (exists/RLS)';
  when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] error inesperado (A sobre tarjeta de B): ' || sqlerrm;
  end;

  begin
    insert into public.card_scheduling (card_id, user_id, ease_factor, interval_days, repetitions, next_review, updated_at)
    values (card_a, b, 2.5, 1, 1, now(), now());
    fails := fails + 1;
    report := report || E'\n[FAIL] A pudo insertar una fila con user_id = B';
  exception when insufficient_privilege then
    report := report || E'\n[OK]   A NO puede insertar filas a nombre de B (with check user_id)';
  when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] error inesperado (A con user_id = B): ' || sqlerrm;
  end;

  ---------------------------------------------------------------- usuario B
  perform set_config('request.jwt.claims',
    json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;

  begin
    insert into public.card_scheduling (card_id, user_id, ease_factor, interval_days, repetitions, next_review, updated_at)
    values (card_b, b, 2.5, 1, 1, now(), now());
    report := report || E'\n[OK]   B inserta el scheduling de su propia tarjeta';
  exception when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] B no pudo insertar el suyo: ' || sqlerrm;
  end;

  select count(*) into n from public.card_scheduling;
  if n = 1 then
    report := report || E'\n[OK]   B ve exactamente 1 fila (la suya) con select sin filtro';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] B ve ' || n || ' filas (esperado 1)';
  end if;

  select count(*) into n from public.card_scheduling where card_id = card_a;
  if n = 0 then
    report := report || E'\n[OK]   B NO puede leer la fila de A';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] B lee la fila de A';
  end if;

  update public.card_scheduling set ease_factor = 1.3 where card_id = card_a;
  get diagnostics n = row_count;
  if n = 0 then
    report := report || E'\n[OK]   B NO puede actualizar la fila de A (0 filas afectadas)';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] B actualizo ' || n || ' fila(s) de A';
  end if;

  delete from public.card_scheduling where card_id = card_a;
  get diagnostics n = row_count;
  if n = 0 then
    report := report || E'\n[OK]   B NO puede borrar la fila de A (0 filas afectadas)';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] B borro ' || n || ' fila(s) de A';
  end if;

  begin
    update public.card_scheduling set user_id = a where card_id = card_b;
    fails := fails + 1;
    report := report || E'\n[FAIL] B pudo traspasar su fila a A (user_id = A)';
  exception when insufficient_privilege then
    report := report || E'\n[OK]   B NO puede cambiar el user_id de su fila a A (with check)';
  when others then
    fails := fails + 1;
    report := report || E'\n[FAIL] error inesperado (B traspasa fila): ' || sqlerrm;
  end;

  ---------------------------------------------------------------- usuario A (otra vez)
  perform set_config('request.jwt.claims',
    json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;

  select count(*) into n from public.card_scheduling;
  if n = 1 then
    report := report || E'\n[OK]   A ve exactamente 1 fila (la suya); la de B no se ve';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] A ve ' || n || ' filas (esperado 1)';
  end if;

  select count(*) into n from public.card_scheduling where card_id = card_b;
  if n = 0 then
    report := report || E'\n[OK]   A NO puede leer la fila de B';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] A lee la fila de B';
  end if;

  update public.card_scheduling set ease_factor = 2.0 where card_id = card_a;
  get diagnostics n = row_count;
  if n = 1 then
    report := report || E'\n[OK]   A actualiza su propia fila';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] A no pudo actualizar la suya (' || n || ' filas)';
  end if;

  ---------------------------------------------------------------- anonimo (sin sesion)
  perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
  set local role anon;

  begin
    select count(*) into n from public.card_scheduling;
    if n = 0 then
      report := report || E'\n[OK]   anon ve 0 filas';
    else
      fails := fails + 1;
      report := report || E'\n[FAIL] anon ve ' || n || ' filas';
    end if;
  exception when insufficient_privilege then
    report := report || E'\n[OK]   anon no tiene ni permiso de lectura';
  end;

  ---------------------------------------------------------------- vuelta a propietario
  reset role;

  -- FK en cascada: borrar de verdad la flashcard de B se lleva su scheduling.
  delete from public.flashcards where id = card_b;
  select count(*) into n from public.card_scheduling where card_id = card_b;
  if n = 0 then
    report := report || E'\n[OK]   ON DELETE CASCADE: al borrar la flashcard de B desaparece su scheduling';
  else
    fails := fails + 1;
    report := report || E'\n[FAIL] la fila de B sobrevivio al borrado de su flashcard';
  end if;

  -- Excepcion final a proposito: fuerza el ROLLBACK de todo lo creado arriba.
  raise exception E'RESULTADO DEL TEST RLS (rollback forzado, no se ha guardado nada) -- fallos = %\n%', fails, report;
end;
$$;
