import java.time.Duration

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
}

tasks.withType<Test> {
    useJUnitPlatform()
    
    // Test configuration
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    
    // JVM arguments for tests
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Djava.awt.headless=true",
        "-Dtestfx.robot=glass",
        "-Dtestfx.headless=true",
        "-Dprism.order=sw",
        "-Dprism.verbose=false"
    )
    
    // Test timeout
    timeout.set(Duration.ofMinutes(5))
    
    // Parallel execution (default=2, override with -Ptest.maxParallelForks or ENV TEST_MAX_PARALLEL_FORKS)
    val forksProp = (project.findProperty("test.maxParallelForks") as String?)?.toIntOrNull()
    val forksEnv = System.getenv("TEST_MAX_PARALLEL_FORKS")?.toIntOrNull()
    val resolvedForks = listOfNotNull(forksProp, forksEnv).firstOrNull() ?: 2
    maxParallelForks = if (resolvedForks < 1) 1 else resolvedForks
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvmToolchain(21)
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Jetpack Compose Desktop
    implementation(compose.desktop.currentOs)
    // Material 3 for Compose Multiplatform + icons
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // SQLite JDBC driver - updated to latest stable
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    
    // SLF4J + Logback (実装)
    // API は implementation、実装は runtimeOnly で解決（logback.xml を利用）
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.12")
    
    // Testing dependencies - updated to latest stable versions
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


compose.desktop {
    application {
        mainClass = "app.compose.AppKt"
        nativeDistributions {
            packageName = "Hiyori"
            packageVersion = (project.findProperty("app.version") as String?) ?: System.getenv("APP_VERSION") ?: "1.0.0"
            description = "Hiyori - Notes & Schedule (Compose Desktop)"
            vendor = (project.findProperty("app.vendor") as String?) ?: System.getenv("APP_VENDOR") ?: "Hiyori"
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm
            )
            macOS {
                val macIcon = file("packaging/icons/hiyori.icns")
                if (macIcon.exists()) iconFile.set(macIcon)
                packageName = (project.findProperty("app.name") as String?) ?: System.getenv("APP_NAME") ?: "Hiyori"
                bundleID = (project.findProperty("app.macBundleId") as String?) ?: System.getenv("APP_MACBUNDLEID") ?: "dev.hiyori.app"
            }
            windows {
                val winIcon = file("packaging/icons/hiyori.ico")
                if (winIcon.exists()) iconFile.set(winIcon)
                menu = true
                shortcut = true
                perUserInstall = true
            }
            linux {
                shortcut = true
            }
        }
    }
}
