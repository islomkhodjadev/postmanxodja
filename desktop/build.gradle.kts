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

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/postbaby-${version}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "postbaby"
    }
    jpackage {
        val os = OperatingSystem.current()
        imageName = "PostBaby"
        installerName = "PostBaby"
        appVersion = project.version.toString().substringBefore('-')
        vendor = "PostBaby"
        when {
            os.isMacOsX -> {
                installerType = "dmg"
                icon = "src/main/resources/icons/postbaby.icns"
                installerOptions.addAll(listOf(
                    "--mac-package-name", "PostBaby"
                ))
            }
            os.isWindows -> {
                installerType = "exe"
                icon = "src/main/resources/icons/postbaby.ico"
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
