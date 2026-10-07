import java.nio.file.Files
import java.nio.file.Paths

plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

// Native, not JVM. Git runs this on every merge and on every commit in `log -p`, so startup time matters.
val graalvmLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.GRAAL_VM
}

graalvmNative {
    toolchainDetection = true
    binaries {
        named("main") {
            imageName = "git-drivers"
            mainClass = "com.glycin.drivers.MainKt"
            javaLauncher = graalvmLauncher
            buildArgs.add("--no-fallback")
            buildArgs.add("-O2")
        }
    }
}

val repairNativeImageLinks by tasks.registering {
    description = "Restores the native-image symlinks lost when Gradle extracts the GraalVM toolchain."
    doLast {
        val home = graalvmLauncher.get().metadata.installationPath.asFile.toPath()
        listOf("native-image", "native-image-configure").forEach { name ->
            val link = home.resolve("bin/$name")
            if (Files.isRegularFile(link) && Files.size(link) == 0L) {
                Files.delete(link)
                Files.createSymbolicLink(link, Paths.get("../lib/svm/bin/$name"))
            }
        }
    }
}

tasks.named("nativeCompile") {
    dependsOn(repairNativeImageLinks)
}

tasks.test {
    useJUnitPlatform()
}
