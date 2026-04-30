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
    applicationDefaultJvmArgs = listOf(
        "--add-exports=javafx.graphics/com.sun.glass.ui=uz.postbaby.desktop"
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Allow the deep-link Glass URL handler to subclass JavaFX's internal
    // com.sun.glass.ui.Application.EventHandler. Public APIs don't expose URL
    // events to JavaFX apps on macOS — Glass intercepts them via
    // NSApplicationDelegate.application:openURLs: and never forwards to AWT.
    options.compilerArgs.addAll(listOf(
        "--add-exports", "javafx.graphics/com.sun.glass.ui=uz.postbaby.desktop"
    ))
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
        val urlTypesXml = """
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
        exec {
            commandLine("plutil", "-replace", "CFBundleURLTypes", "-xml", urlTypesXml, plist.absolutePath)
        }
        // Force macOS to single-instance the app. Without this, `open postbaby://`
        // launches a fresh duplicate instead of dispatching the URL Apple Event
        // to the running instance — see JDK-8195976.
        exec {
            commandLine("plutil", "-replace", "LSMultipleInstancesProhibited", "-bool", "true", plist.absolutePath)
        }
        logger.lifecycle("Patched Info.plist (URL scheme + single-instance) at ${plist.absolutePath}")
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
        jvmArgs = listOf(
            "--add-exports=javafx.graphics/com.sun.glass.ui=uz.postbaby.desktop"
        )
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
        // finalizedBy guarantees the patch runs whenever jpackageImage runs,
        // not just when something explicitly asks for patchMacInfoPlist.
        image.finalizedBy(patchMacInfoPlist)
    }
    tasks.findByName("jpackage")?.dependsOn(patchMacInfoPlist)
}
