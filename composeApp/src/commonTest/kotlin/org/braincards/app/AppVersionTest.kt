package org.braincards.app

import kotlin.test.Test
import kotlin.test.assertTrue

/** APP_VERSION la genera Gradle desde appVersionName: debe tener el formato MAJOR.MINOR.PATCH que exige el MSI. */
class AppVersionTest {
    @Test
    fun la_version_generada_es_MAJOR_MINOR_PATCH_numerica() {
        assertTrue(Regex("""[1-9]\d*\.\d+\.\d+""").matches(APP_VERSION), "APP_VERSION=$APP_VERSION")
    }
}
