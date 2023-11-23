/*
 * Copyright 2023 Gamekins contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gamekins.file

import hudson.FilePath
import org.gamekins.util.Constants.Parameters
import org.gamekins.util.GitUtil
import org.gamekins.util.JacocoUtil
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import java.io.File
import java.io.InputStream
import java.io.Serializable
import java.net.URI
import java.nio.charset.Charset

/**
 * The internal representation of a file received from git.
 *
 * @param parameters Constants about the project, needed for generating new challenges after rejection
 * @param filePath Path of the file, starting in the workspace root directory
 *
 * @author Philipp Straubinger
 * @since 0.4
 */
abstract class FileDetails(var parameters: Parameters, val filePath: String)
    : Serializable, ClientInputFile {

    val changedByUsers: HashSet<GitUtil.GameUser> = hashSetOf()
    val file: File
    val fileName: String
    val fileExtension: String
    val packageName: String

    init {
        val pathSplit = filePath.split("/".toRegex())
        //Compute class, package and extension name
        val lastPartOfFile = pathSplit[pathSplit.size - 1]
        fileName = lastPartOfFile.split("\\.".toRegex())[0]
        fileExtension = lastPartOfFile.removePrefix(lastPartOfFile.split("\\.".toRegex())[0] + ".")
        packageName = JacocoUtil.computePackageName(filePath)

        file = if (!parameters.remote.endsWith("/") && !filePath.startsWith("/")) {
            File(parameters.remote + "/" + filePath)
        } else {
            File(parameters.remote + filePath)
        }
    }

    /**
     * Adds a new [user], who has recently changed the class.
     */
    fun addUser(user: GitUtil.GameUser) {
        changedByUsers.add(user)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileDetails) return false

        if (filePath != other.filePath) return false
        if (file != other.file) return false
        if (fileName != other.fileName) return false
        if (fileExtension != other.fileExtension) return false
        if (packageName != other.packageName) return false

        return true
    }

    //TODO: Does not work on remote workers
    /**
     * Check whether the file exists.
     */
    open fun filesExists(): Boolean {
        return file.exists()
    }

    override fun getCharset(): Charset? {
        return null
    }

    override fun <G : Any?> getClientObject(): G? {
        return null
    }

    override fun contents(): String {
        return if (parameters.workspace.channel != null) {
            FilePath(parameters.workspace.channel, file.absolutePath).readToString()
        } else {
            file.readText()
        }
    }

    override fun getPath(): String {
        return file.absolutePath
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileExtension.hashCode()
        result = 31 * result + packageName.hashCode()
        return result
    }

    override fun inputStream(): InputStream {
        return file.inputStream()
    }

    override fun relativePath(): String {
        return filePath
    }

    /**
     * Updates the working directory according to the current build.
     *
     * @param parameters The current [Parameters] class of the build
     */
    fun update(parameters: Parameters): FileDetails {
        this.parameters.workspace = parameters.workspace
        return this
    }

    override fun uri(): URI {
        return file.toURI()
    }
}