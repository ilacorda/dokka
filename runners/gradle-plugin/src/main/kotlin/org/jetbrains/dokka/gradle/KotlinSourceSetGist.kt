package org.jetbrains.dokka.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.jetbrains.dokka.gradle.KotlinSourceSetGist.PredictedType.Main
import org.jetbrains.dokka.gradle.KotlinSourceSetGist.PredictedType.Test
import org.jetbrains.dokka.utilities.cast
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

// TODO NOW: Test this all
private typealias KotlinCompilation =
        org.jetbrains.kotlin.gradle.plugin.KotlinCompilation<KotlinCommonOptions>

internal data class KotlinSourceSetGist(
    val name: String,
    val platform: String,
    val classpath: FileCollection,
    val sourceRoots: List<File>,
    val dependentSourceSets: List<String>,
) {
    enum class PredictedType { Main, Test }

    val predictedType: PredictedType = if ("test" in name.toLowerCase()) Test else Main
}

/**
 * @return null if the kotlin extension cannot be found,
 * A list of [KotlinSourceSetGist] for every currently registered kotlin source set
 */
internal fun Project.findKotlinSourceSets(): List<KotlinSourceSetGist>? {
    val kotlin = kotlinExtensionOrNull ?: return null
    return kotlin.sourceSets.map { sourceSet -> kotlin.gistOf(sourceSet) }
}

internal fun KotlinProjectExtension.gistOf(sourceSet: KotlinSourceSet): KotlinSourceSetGist {
    return KotlinSourceSetGist(
        name = sourceSet.name,
        platform = platformOf(sourceSet),
        classpath = classpathOf(sourceSet),
        sourceRoots = sourceSet.kotlin.sourceDirectories.toList().filter(File::exists),
        dependentSourceSets = sourceSet.dependsOn.map { dependentSourceSet -> dependentSourceSet.name },
    )
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
    val targetNames = compilationsOf(sourceSet).map { compilation -> compilation.target.platformType.name }.distinct()
    return when (targetNames.size) {
        0 -> KotlinPlatformType.common.name
        1 -> targetNames.single()
        else -> throw IllegalArgumentException(
            "Source set ${sourceSet.name} is expected to have only one target. Found $targetNames"
        )
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
    return allCompilations.filter { compilation -> sourceSet in compilation.kotlinSourceSets }
}

private fun KotlinSingleTargetExtension.compilationsOf(sourceSet: KotlinSourceSet): List<KotlinCompilation> {
    return target.compilations.filter { compilation -> sourceSet in compilation.kotlinSourceSets }
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
