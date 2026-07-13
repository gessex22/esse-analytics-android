import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// Los convention plugins son clases compiladas (no .gradle.kts), así que no
// tienen el accessor `libs` generado automáticamente — este helper lo expone
// igual, para usar `libs.findVersion("...")` / `libs.findLibrary("...")` desde
// cualquier plugin de este módulo.
val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
