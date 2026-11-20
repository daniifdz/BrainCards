package org.braincards.app.domain.sample

/** Una tarjeta del mazo de ejemplo: pregunta (anverso) y respuesta (reverso). */
data class SampleCard(val question: String, val answer: String)

/**
 * Nombres visibles del mazo de ejemplo y de sus temas. Salen de `strings.xml` (son etiquetas de producto) y los pasa la UI: el
 * dominio no lee recursos. [topics] tiene un nombre por cada tema de [SampleDeckContent].
 */
data class SampleDeckNames(val deck: String, val topics: List<String>)

/**
 * Contenido del mazo de ejemplo «Primeros pasos»: 2 temas de 5 tarjetas. Son datos que se guardan en la base de datos como
 * cualquier tarjeta del usuario, no textos de interfaz, así que no van en `strings.xml`.
 */
object SampleDeckContent {

    /** Tema 1: cómo estudiar con BrainCards. */
    private val studyTopic = listOf(
        SampleCard(
            "¿Qué es la repetición espaciada?",
            "Es un método de estudio que te muestra cada tarjeta justo antes de que se te olvide. BrainCards calcula el momento " +
                "ideal para repasar cada una, así que revisas menos veces y recuerdas más tiempo."
        ),
        SampleCard(
            "Cuando repasas una tarjeta, ¿qué significa pulsar \"Otra vez\"?",
            "Que no la recordabas. BrainCards la volverá a mostrar muy pronto, en la misma sesión o al día siguiente, para " +
                "reforzarla."
        ),
        SampleCard(
            "¿Qué diferencia hay entre \"Difícil\" y \"Bien\"?",
            "\"Difícil\" significa que la recordaste, pero con esfuerzo: volverá a aparecer en poco tiempo. \"Bien\" significa " +
                "que la recordaste con normalidad: el intervalo hasta el próximo repaso aumenta."
        ),
        SampleCard(
            "¿Qué pasa si pulso \"Fácil\"?",
            "Le dices a la app que ya dominas esa tarjeta. El intervalo hasta volver a verla se alarga mucho más que con \"Bien\"."
        ),
        SampleCard(
            "Ya no me quedan tarjetas pendientes en un mazo. ¿Puedo seguir repasando?",
            "Sí, con Repaso libre. Repasas todas las tarjetas del mazo, en orden aleatorio, tantas veces como quieras — no " +
                "cambia tu programación ni cuenta como un repaso real."
        )
    )

    /** Tema 2: cómo organizar y sincronizar los mazos. */
    private val organizeTopic = listOf(
        SampleCard(
            "¿Qué es un tema dentro de un mazo?",
            "Un tema agrupa tarjetas relacionadas dentro del mismo mazo, para organizarlas por bloques — por ejemplo, " +
                "\"Vocabulario\" y \"Gramática\" dentro de un mazo de inglés."
        ),
        SampleCard(
            "¿Cómo creo una tarjeta nueva?",
            "Entra en un mazo, elige un tema (o crea uno) y pulsa el botón de añadir tarjeta. Escribe el anverso (la pregunta) y " +
                "el reverso (la respuesta)."
        ),
        SampleCard(
            "¿Se guardan mis mazos si cambio de móvil o uso el ordenador?",
            "Sí, si tienes una cuenta: tus mazos y tu progreso se sincronizan entre dispositivos automáticamente."
        ),
        SampleCard(
            "¿Puedo usar BrainCards sin crear una cuenta?",
            "Sí. En modo sin cuenta tus mazos se guardan solo en este dispositivo, pero no se sincronizan ni tienes copia de " +
                "seguridad en la nube. Puedes crear una cuenta más adelante."
        ),
        SampleCard(
            "Este mazo de ejemplo, ¿lo puedo borrar?",
            "Sí. Puedes borrarlo cuando quieras desde tu lista de mazos."
        )
    )

    /** Tarjetas por tema, en el orden en que se muestran. */
    val topics: List<List<SampleCard>> = listOf(studyTopic, organizeTopic)

    val cardCount: Int get() = topics.sumOf { it.size }
}
