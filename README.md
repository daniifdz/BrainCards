# BrainCards

App de flashcards con repetición espaciada (SM-2) para **Android** y **Desktop**, escrita en Kotlin Multiplatform con Compose Multiplatform. Funciona sin cuenta y sin conexión; con cuenta, sincroniza tus mazos entre dispositivos.

<!-- CAPTURAS: añadir 3-4 imágenes (Home, estudio, estadísticas, Desktop) en docs/screenshots/ y enlazarlas aquí -->

## Qué puedes hacer

- Crear mazos, temas y tarjetas.
- **Estudiar con SM-2:** la app calcula qué tarjetas te tocan hoy y reprograma cada una según cómo respondas.
- **Repaso libre:** repasa todas las tarjetas de un mazo (o de todos), barajadas, sin alterar la programación.
- Ver tus **estadísticas** y las tarjetas pendientes desde la pantalla de inicio.
- Empezar con un **mazo de ejemplo** («Primeros pasos») para conocer la app.
- **Sin cuenta:** todo funciona en local. **Con cuenta:** sincronización automática entre dispositivos (registro con código de 6 dígitos y recuperación de contraseña).
- Tema claro y oscuro, y envío de comentarios desde Ajustes.

## Tecnología

| Área | Elección |
| --- | --- |
| Lenguaje y UI | Kotlin Multiplatform, Compose Multiplatform |
| Arquitectura | Clean Architecture + MVVM, flujo de datos unidireccional |
| Datos locales | Room KMP (migraciones con test) y DataStore |
| Backend | Supabase (auth y Postgres con políticas RLS) |
| Inyección de dependencias | Koin |
| Navegación | Navigation Compose con rutas tipadas |
| Pruebas | Más de 3.600 ejecuciones de test (JVM y Android), con Room real y sincronización entre dos dispositivos simulados |

## Qué la hace interesante

- **Sincronización offline-first** con cursor incremental, última escritura gana por fila y borrados lógicos.
- **El Repaso libre no puede escribir en la base de datos por construcción**, y un test lo comprueba comparando todas las tablas.
- **Una sola definición de «tarjeta activa»** compartida por todas las pantallas e impuesta por un test.
- **Tests guardián** que impiden que el dominio dependa de librerías de red.

Todo está explicado, con sus alternativas descartadas y las mejoras pendientes, en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Cómo ejecutarla

Requisitos: **JDK 17**. Para la app de Android, además, el SDK de Android (`sdk.dir` en `local.properties`, que Android Studio crea solo). Para compilar la versión de escritorio y ejecutar los tests no hace falta nada más: ni claves ni keystore.

1. Clona el repositorio.
2. (Opcional) Copia `local.properties.example` a `local.properties` y rellena los valores de tu proyecto de Supabase (`SUPABASE_URL` y `SUPABASE_ANON_KEY`) y la ruta de tu SDK. Ese fichero no se sube al repositorio.
3. Ejecuta:

```bash
# Desktop
./gradlew :composeApp:run

# Android (con un dispositivo o emulador conectado)
./gradlew :composeApp:installDebug

# Tests en JVM
./gradlew :composeApp:jvmTest
```

> **Supabase es opcional.** Sin `SUPABASE_URL` y `SUPABASE_ANON_KEY` el proyecto compila (con un aviso de Gradle) y la app funciona sin cuenta: iniciar sesión o crear una muestra «No se pudo conectar» y no hay sincronización.
>
> **Build de release.** Solo `assembleRelease` y `bundleRelease` necesitan firma: `KEYSTORE_PASSWORD` y `KEY_PASSWORD` en `local.properties` (o `BRAINCARDS_KEYSTORE_PASSWORD` y `BRAINCARDS_KEY_PASSWORD` como variables de entorno) y el keystore en `composeApp/keystore/braincards-key.jks`, con alias `key0`. Sin ellos falla enseguida con un mensaje que lo explica. Ejecutar y probar no necesita firma.

El esquema de la base de datos remota está en [`sql/`](sql/).

## Estado y próximos pasos

Es la primera versión y hay ganas de seguir añadiendo funcionalidades. Lo que viene:

- **Racha de días** de estudio, sincronizada entre dispositivos.
- **Tarjetas generadas con IA** a partir de tus apuntes.

También quedan, de forma deliberada, fuera de esta versión: iOS, publicación en tiendas y un dominio propio para los correos de la app.

## Cómo se ha hecho

El diseño, las decisiones de arquitectura y la revisión son míos. He usado asistentes de IA como ayuda para la implementación, y cada cambio se ha validado con tests y comprobaciones propias (por ejemplo, mutando el código para comprobar que los tests fallan cuando deben).

## Contacto

**Daniel Fernández-Muñiz Arribas** · [LinkedIn](https://www.linkedin.com/in/daniel-fern%C3%A1ndez-mu%C3%B1iz-arribas) · braincards.soporte@proton.me

## Licencia

[MIT](LICENSE). Las fuentes incluidas llevan su propia licencia (OFL).
