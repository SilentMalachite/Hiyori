plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
    id("org.beryx.jlink") version "3.0.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    // Suppress SLF4J binding warning at runtime (no logging needed for now)
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("app.MainApp")
}

tasks.named<org.gradle.api.tasks.JavaExec>("run") {
    // Improve font rendering on some platforms and enable UTF-8 default
    jvmArgs("-Dfile.encoding=UTF-8")
}

jlink {
    options = listOf("--strip-debug", "--compress=2", "--no-header-files", "--no-man-pages")
    // Non-modular project: name the merged module
    mergedModuleName = "hiyori.merged"
    // Explicit module name for the application (fallback)
    moduleName = "hiyori.merged"
    launcher {
        name = "Hiyori"
    }
    jpackage {
        val os = org.gradle.internal.os.OperatingSystem.current()

        fun prop(name: String, default: String): String =
            (project.findProperty(name) as String?)
                ?: System.getenv(name.replace('.', '_').uppercase())
                ?: default

        val appName = prop("app.name", "Hiyori")
        val appVersionVal = prop("app.version", "1.0.0")
        val vendorName = prop("app.vendor", "Hiyori")
        val macBundleId = prop("app.macBundleId", "dev.hiyori.app")
        val installerTypeProp = prop("app.installerType", if (os.isMacOsX) "pkg" else "")

        val macIcon = file("packaging/icons/hiyori.icns")
        val winIcon = file("packaging/icons/hiyori.ico")

        imageName = appName
        installerName = appName
        appVersion = appVersionVal
        vendor = vendorName

        val imgOpts = mutableListOf("--name", appName)
        if (os.isMacOsX && macIcon.exists()) imgOpts += listOf("--icon", macIcon.absolutePath)
        if (os.isWindows && winIcon.exists()) imgOpts += listOf("--icon", winIcon.absolutePath)
        imageOptions = imgOpts

        val instOpts = mutableListOf("--name", appName)
        if (os.isMacOsX) {
            instOpts += listOf("--mac-package-name", appName, "--mac-package-identifier", macBundleId)
        }
        if (os.isWindows) {
            instOpts += listOf("--win-dir-chooser", "--win-menu", "--win-menu-group", appName, "--win-shortcut")
        }
        if (os.isLinux) {
            instOpts += listOf("--linux-shortcut", "--linux-menu-group", appName)
        }
        if (os.isWindows && winIcon.exists()) instOpts += listOf("--icon", winIcon.absolutePath)
        if (os.isMacOsX && macIcon.exists()) instOpts += listOf("--icon", macIcon.absolutePath)
        installerOptions = instOpts

        // Allow overriding installerType via property/ENV across all OS
        if (installerTypeProp.isNotBlank()) {
            installerType = installerTypeProp
        }
    }
}
