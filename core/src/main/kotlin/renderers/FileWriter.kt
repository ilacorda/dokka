package org.jetbrains.dokka.renderers

import com.intellij.util.io.isDirectory
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.*

class FileWriter(val root: String, override val extension: String): OutputWriter {
    private val createdFiles: MutableSet<String> = mutableSetOf()

    override fun write(path: String, text: String, ext: String) {
        if (createdFiles.contains(path)) {
            println("ERROR. An attempt to write $root/$path several times!")
            return
        }
        createdFiles.add(path)

        try {
//            println("Writing $root/$path$ext")
            val dir = Paths.get(root, path.dropLastWhile { it != '/' }).toFile()
            dir.mkdirsOrFail()
            Paths.get(root, "$path$ext").toFile().writeText(text)
        } catch (e: Throwable) {
            println("Failed to write $this. ${e.message}")
            e.printStackTrace()
        }
    }

    override fun writeResources(pathFrom: String, pathTo: String) {
        val rebase = fun(path: String) =
            "$pathTo/${path.removePrefix(pathFrom)}"
        val dest = Paths.get(root, pathTo).toFile()
        dest.mkdirsOrFail()
        val uri = javaClass.getResource(pathFrom).toURI()
        val fs = getFileSystemForURI(uri)
        val path = fs.getPath(pathFrom)
        for (file in Files.walk(path).iterator()) {
            if (file.isDirectory()) {
                val dirPath = file.toAbsolutePath().toString()
                Paths.get(root, rebase(dirPath)).toFile().mkdirsOrFail()
            } else {
                val filePath = file.toAbsolutePath().toString()
                Paths.get(root, rebase(filePath)).toFile().writeBytes(
                    javaClass.getResourceAsStream(filePath).readBytes()
                )
            }
        }
    }

    private fun File.mkdirsOrFail() {
        if (!mkdirs() && !exists()) {
            throw IOException("Failed to create directory $this")
        }
    }

    private fun getFileSystemForURI(uri: URI): FileSystem =
        try {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        } catch (e: FileSystemAlreadyExistsException) {
            FileSystems.getFileSystem(uri)
        }
}