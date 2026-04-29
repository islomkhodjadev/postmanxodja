import org.gradle.internal.os.OperatingSystem

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

group = "uz.postbaby"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    modularity.inferModulePath.set(true)
}

javafx {
    version = "21.0.4"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    // Live syntax highlighting in the editable request body
    implementation("org.fxmisc.richtext:richtextfx:0.11.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainModule.set("uz.postbaby.desktop")
    mainClass.set("uz.postbaby.desktop.PostBabyApp")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

/**
 * Inject CFBundleURLTypes into the macOS app bundle's Info.plist so the OS
 * dispatches `postbaby://` URLs to the desktop app (used by the deep-link
 * sign-in flow). Runs after `jpackageImage` builds the .app and before
 * `jpackage` packages it into a DMG.
 */
val patchMacInfoPlist by tasks.registering {
    onlyIf { OperatingSystem.current().isMacOsX }
    doLast {
        val plist = layout.buildDirectory.file("jpackage/PostBaby.app/Contents/Info.plist").get().asFile
        if (!plist.exists()) {
            logger.warn("Info.plist not found at ${plist.absolutePath}; skipping URL scheme registration.")
            return@doLast
        }
        val xml = """
            <array>
              <dict>
                <key>CFBundleURLName</key>
                <string>uz.postbaby.desktop</string>
                <key>CFBundleURLSchemes</key>
                <array>
                  <string>postbaby</string>
                </array>
              </dict>
            </array>
        """.trimIndent()
        // -replace is idempotent across rebuilds (insert would fail if the key already exists).
        exec {
            commandLine("plutil", "-replace", "CFBundleURLTypes", "-xml", xml, plist.absolutePath)
        }
        logger.lifecycle("Registered postbaby:// URL scheme in ${plist.absolutePath}")
    }
}

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/postbaby-${version}.zip"))
    options.set(listOf(
        "--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages",
        // jdk.crypto.ec is loaded via ServiceLoader at runtime so jlink can't infer it.
        // Without it, TLS handshakes against modern servers fail with handshake_failure
        // (no ECDHE/ECDSA support).
        // jdk.crypto.cryptoki is similar — useful when the host has a PKCS#11 truststore.
        // jdk.localedata + java.naming round out the bundle so URL/DNS/locale paths work.
        "--add-modules", "jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,java.naming"
    ))
    // RichTextFX + transitives are automatic modules — the plugin merges them into a
    // single synthetic module that needs the JavaFX modules visible to compile.
    addExtraDependencies("javafx")
    forceMerge("richtextfx", "flowless", "reactfx", "undofx", "wellbehavedfx")
    launcher {
        name = "postbaby"
    }
    jpackage {
        val os = OperatingSystem.current()
        imageName = "PostBaby"
        installerName = "PostBaby"
        // macOS rejects versions with a leading 0 — coerce 0.x.y to 1.x.y for the bundle only.
        appVersion = run {
            val v = project.version.toString().substringBefore('-')
            val parts = v.split('.')
            if (parts.isNotEmpty() && parts[0] == "0") "1." + parts.drop(1).joinToString(".") else v
        }
        vendor = "PostBaby"

        // Use a custom icon when one is checked into src/main/resources/icons/.
        // Drop in postbaby.icns (mac) and postbaby.ico (win) to brand the installer.
        val macIcon = file("src/main/resources/icons/postbaby.icns")
        val winIcon = file("src/main/resources/icons/postbaby.ico")

        when {
            os.isMacOsX -> {
                installerType = "dmg"
                if (macIcon.exists()) icon = macIcon.absolutePath
                installerOptions.addAll(listOf(
                    "--mac-package-name", "PostBaby"
                ))
            }
            os.isWindows -> {
                installerType = "exe"
                if (winIcon.exists()) icon = winIcon.absolutePath
                installerOptions.addAll(listOf(
                    "--win-dir-chooser",
                    "--win-shortcut",
                    "--win-menu",
                    "--win-menu-group", "PostBaby"
                ))
            }
            else -> {
                installerType = "deb"
            }
        }
    }
}

afterEvaluate {
    tasks.findByName("jpackageImage")?.let { image ->
        patchMacInfoPlist.configure { dependsOn(image) }
    }
    tasks.findByName("jpackage")?.dependsOn(patchMacInfoPlist)
}
