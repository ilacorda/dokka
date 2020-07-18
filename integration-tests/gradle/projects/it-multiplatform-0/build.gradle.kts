import org.jetbrains.dokka.gradle.configureWithKotlinSourceSet

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
}

apply(from = "../template.root.gradle.kts")

kotlin {
    jvm()
    linuxX64("linux")
    macosX64("macos")
    js()

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        named("jvmMain") {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
    }
}
