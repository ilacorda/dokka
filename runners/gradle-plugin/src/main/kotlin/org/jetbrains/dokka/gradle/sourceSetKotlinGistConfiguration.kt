package org.jetbrains.dokka.gradle

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.AbstractTaskDependency
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

// TODO NOW: Test
fun GradleDokkaSourceSetBuilder.configureWithKotlinSourceSet(sourceSet: KotlinSourceSet) {
    configureWithKotlinSourceSetGist(project.kotlinExtension.gistOf(sourceSet))
}

internal fun GradleDokkaSourceSetBuilder.configureWithKotlinSourceSetGist(sourceSet: KotlinSourceSetGist) {
    sourceRoots.addAll(sourceRoots.union(sourceSet.sourceRoots).distinct())
    dependentSourceSets.addAll(dependentSourceSets)
    dependentSourceSets.addAll(sourceSet.dependentSourceSets.map { DokkaSourceSetID(project, it) })
    classpath.from(sourceSet.classpath)
    if (platform == null && sourceSet.platform != "")
        platform = sourceSet.platform
}
