import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fauran.fileviewer.viewer"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory("build/generated/ooxml/assets")
    }
}

// Task-only resolution of the office renderer webjar; not exposed to consumers.
val ooxmlWebjarConfig = configurations.create("ooxmlWebjar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    "ooxmlWebjar"(libs.ooxml.webjar)

    api(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
}

// Packages the webjar's dist/ as a single compressed ooxml.zip asset (plus a
// version file for runtime cache invalidation). The app extracts it to
// filesDir on first load — keeps ~14 MB of JS/WASM out of the stored APK
// (~5 MB zip instead). Nothing JS-related lives in git.
val packageOoxmlZip = tasks.register("packageOoxmlZip") {
    val webjarJar = ooxmlWebjarConfig.elements.map { it.single().asFile }
    val assetsDir = layout.buildDirectory.dir("generated/ooxml/assets")
    val version = libs.versions.ooxmlWebjar.get()
    inputs.files(webjarJar)
    outputs.dir(assetsDir)
    doLast {
        val jar = webjarJar.get()
        val outDir = assetsDir.get().asFile.apply { mkdirs() }
        ZipFile(jar).use { zf ->
            // The dist lives under META-INF/resources/webjars/** or package/**
            // depending on how the webjar was published — locate it by marker file.
            val marker = zf.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("dist/docx.mjs") }
                ?: error("${jar.name} does not contain a dist/docx.mjs (unexpected webjar layout)")
            val distRoot = marker.name.removeSuffix("dist/docx.mjs")
            ZipOutputStream(File(outDir, "ooxml.zip").outputStream().buffered()).use { out ->
                for (entry in zf.entries()) {
                    if (entry.isDirectory) continue
                    if (!entry.name.startsWith(distRoot)) continue
                    val name = entry.name.removePrefix(distRoot).removePrefix("dist/")
                    if (name.isEmpty() || name.startsWith("types/") ||
                        name.endsWith(".d.ts") || name == "node.mjs"
                    ) continue
                    out.putNextEntry(ZipEntry(name))
                    zf.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        File(outDir, "ooxml-version.txt").writeText(version)
    }
}

tasks.configureEach {
    // Asset-merging and lint tasks read the generated assets dir.
    if ((name.startsWith("merge") && name.endsWith("Assets")) ||
        name.contains("lint", ignoreCase = true)
    ) {
        dependsOn(packageOoxmlZip)
    }
}
