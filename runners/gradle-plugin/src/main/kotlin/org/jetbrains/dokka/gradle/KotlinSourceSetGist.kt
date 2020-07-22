package org.jetbrains.dokka.gradle

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.file.FileCollection
import org.jetbrains.dokka.utilities.cast
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

// TODO NOW: Test this all
private typealias KotlinCompilation =
        org.jetbrains.kotlin.gradle.plugin.KotlinCompilation<KotlinCommonOptions>

internal data class KotlinSourceSetGist(
    val name: String,
    val platform: String,
    val isMain: Boolean,
    val classpath: FileCollection,
    val sourceRoots: List<File>,
    val dependentSourceSets: List<String>,
)

internal fun KotlinProjectExtension.gistOf(sourceSet: KotlinSourceSet): KotlinSourceSetGist {
    return KotlinSourceSetGist(
        name = sourceSet.name,
        platform = platformOf(sourceSet),
        isMain = isMainSourceSet(sourceSet),
        classpath = classpathOf(sourceSet),
        sourceRoots = sourceSet.kotlin.sourceDirectories.toList().filter(File::exists),
        dependentSourceSets = sourceSet.dependsOn.map { dependentSourceSet -> dependentSourceSet.name },
    )
}

// TODO NOW: test also for ANDROID
internal fun KotlinProjectExtension.isMainSourceSet(sourceSet: KotlinSourceSet): Boolean {
    return compilationsOf(sourceSet).any { compilation -> isMainCompilation(compilation) }
}

private fun isMainCompilation(compilation: KotlinCompilation): Boolean {
    // TODO NOW: Doesnt work with kotlin 1.3.*
    val androidVariant = compilation.run { this as? KotlinJvmAndroidCompilation }?.androidVariant
    if (androidVariant != null) {
        return androidVariant is LibraryVariant || androidVariant is ApplicationVariant
    }
    return compilation.name == "main"
}

private fun KotlinProjectExtension.classpathOf(sourceSet: KotlinSourceSet): FileCollection {
    val compilations = compilationsOf(sourceSet)
    if (compilations.isNotEmpty()) {
        return compilations
            .map { compilation -> compileClasspathOf(compilation) }
            .reduce { acc, fileCollection -> acc + fileCollection }
    }

    return sourceSet.withAllDependentSourceSets()
        .toList()
        .map { it.kotlin.sourceDirectories }
        .reduce { acc, fileCollection -> acc + fileCollection }
}

private fun KotlinProjectExtension.platformOf(sourceSet: KotlinSourceSet): String {
    val targetNames = compilationsOf(sourceSet).map { compilation -> compilation.target.platformType }.distinct()
    return when (targetNames.size) {
        1 -> targetNames.single().name
        else -> KotlinPlatformType.common.name
    }
}

private fun KotlinProjectExtension.compilationsOf(
    sourceSet: KotlinSourceSet
): List<KotlinCompilation> {
    return when (this) {
        is KotlinMultiplatformExtension -> compilationsOf(sourceSet)
        is KotlinSingleTargetExtension -> compilationsOf(sourceSet)
        else -> emptyList()
    }
}

private fun KotlinMultiplatformExtension.compilationsOf(sourceSet: KotlinSourceSet): List<KotlinCompilation> {
    val allCompilations = targets.flatMap { target -> target.compilations }
    return allCompilations.filter { compilation -> sourceSet in compilation.allKotlinSourceSets }
}

private fun KotlinSingleTargetExtension.compilationsOf(sourceSet: KotlinSourceSet): List<KotlinCompilation> {
    return target.compilations.filter { compilation -> sourceSet in compilation.allKotlinSourceSets }
}

private fun compileClasspathOf(compilation: KotlinCompilation): FileCollection {
    if (compilation.target.isAndroidTarget()) {
        // This is a workaround for https://youtrack.jetbrains.com/issue/KT-33893
        return compilation.compileKotlinTask.cast<KotlinCompile>().classpath
    }
    return compilation.compileDependencyFiles
}

private fun KotlinSourceSet.withAllDependentSourceSets(): Sequence<KotlinSourceSet> {
    return sequence {
        yield(this@withAllDependentSourceSets)
        for (dependentSourceSet in dependsOn) {
            yieldAll(dependentSourceSet.withAllDependentSourceSets())
        }
    }
}
