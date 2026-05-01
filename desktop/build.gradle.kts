import org.gradle.internal.os.OperatingSystem

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

group = "uz.postbaby"
version = "1.1.0"

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

    implementation("org.fxmisc.richtext:richtextfx:0.11.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainModule.set("uz.postbaby.desktop")
    mainClass.set("uz.postbaby.desktop.PostbabyApp")
    applicationDefaultJvmArgs = listOf(
        "--add-exports=javafx.graphics/com.sun.glass.ui=uz.postbaby.desktop"
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "--add-exports", "javafx.graphics/com.sun.glass.ui=uz.postbaby.desktop"
        )
    )
}

val patchMacInfoPlist by tasks.registering {
    onlyIf { OperatingSystem.current().isMacOsX }
    doLast {
        val plist = layout.buildDirectory.file("jpackage/Postbaby.app/Contents/Info.plist").get().asFile
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
        exec {
            commandLine("plutil", "-replace", "LSMultipleInstancesProhibited", "-bool", "true", plist.absolutePath)
        }
        logger.lifecycle("Patched Info.plist (URL scheme + single-instance) at ${plist.absolutePath}")
    }
}

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/postbaby-${version}.zip"))
    options.set(
        listOf(
            "--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages",
            "--add-modules", "jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,java.naming"
        )
    )
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
        imageName = "Postbaby"
        installerName = "Postbaby"
        appVersion = run {
            val v = project.version.toString().substringBefore('-')
            val parts = v.split('.')
            if (parts.isNotEmpty() && parts[0] == "0") "1." + parts.drop(1).joinToString(".") else v
        }
        vendor = "Postbaby"

        val macIcon = file("src/main/resources/icons/postbaby.icns")
        val winIcon = file("src/main/resources/icons/postbaby.ico")

        when {
            os.isMacOsX -> {
                installerType = "dmg"
                if (macIcon.exists()) icon = macIcon.absolutePath
                installerOptions.addAll(
                    listOf(
                        "--mac-package-name", "Postbaby"
                    )
                )
            }

            os.isWindows -> {
                installerType = "exe"
                if (winIcon.exists()) icon = winIcon.absolutePath
                installerOptions.addAll(
                    listOf(
                        "--win-dir-chooser",
                        "--win-shortcut",
                        "--win-menu",
                        "--win-menu-group", "PostBaby"
                    )
                )
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
        image.finalizedBy(patchMacInfoPlist)
    }
    tasks.findByName("jpackage")?.dependsOn(patchMacInfoPlist)
}
