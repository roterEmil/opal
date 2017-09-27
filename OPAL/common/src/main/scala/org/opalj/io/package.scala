/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj

import java.nio.file.Files
import java.nio.file.Path
import java.io.File
import java.io.IOException
import java.io.Closeable
import java.awt.Desktop

import scala.io.Source
import scala.xml.Node

/**
 * Various io-related helper methods and classes.
 *
 * @note The implementations of the methods rely on Java NIO(2).
 *
 * @author Michael Eichberg
 * @author Andreas Muttscheller
 */
package object io {

    /**
     * Replaces characters in the given file name (segment) that are (potentially) problematic
     * on some file system and also shortens the filename
     *
     * @see For more information visit https://en.wikipedia.org/wiki/Filename
     *
     * @param fileName The filename or a suffix/prefix thereof which should be sanitized.
     *
     * @return The sanitized file name.
     *
     */
    def sanitizeFileName(fileName: String): String = {
        // take(128+64) ... to have some space for something else...
        fileName.filterNot(_ == ' ').replaceAll("[\\/:*?\"<>|\\[\\]=!@,]", "_").take(128 + 64)
    }

    /**
     * Writes the XML document to a temporary file and opens the file in the
     * OS's default application.
     *
     * @param filenamePrefix A string the identifies the content of the file. (E.g.,
     *      "ClassHierarchy" or "CHACallGraph")
     * @param filenameSuffix The suffix of the file that identifies the used file format.
     *      (E.g., ".xhtml")
     * @return The name of the file if it was possible to write the file and open
     *      the native application.
     */
    @throws[IOException]("if it is not possible to create a temporary file")
    @throws[OpeningFileFailedException]("if it is not possible to open the file")
    def writeAndOpen(
        node:           Node,
        filenamePrefix: String,
        filenameSuffix: String
    ): File = {
        val data = node.toString
        writeAndOpen(data, filenamePrefix, filenameSuffix)
    }

    /**
     * Writes the given string (`data`) to a temporary file using the given prefix and suffix.
     * Afterwards the system's native application that claims to be able to handle
     * files with the given suffix is opened. If this fails, the string is printed to
     * the console.
     *
     * The string is always written using UTF-8 as the encoding.
     *
     * @param filenamePrefix A string the identifies the content of the file. (E.g.,
     *      "ClassHierarchy" or "CHACallGraph")
     * @param filenameSuffix The suffix of the file that identifies the used file format.
     *      (E.g., ".txt")
     * @return The name of the file if it was possible to write the file and open
     *      the native application.
     * @example
     *      Exemplary usage:
     *      {{{
     *      try {
     *          util.writeAndOpen("The Message", "Result", ".txt")
     *      } catch {
     *          case OpeningFileFailedException(file, _) ⇒
     *              Console.err.println("Details can be found in: "+file.toString)
     *      }}}
     */
    @throws[IOException]("if it is not possible to create a temporary file")
    @throws[OpeningFileFailedException]("if it is not possible to open the file")
    def writeAndOpen(
        data:           String,
        filenamePrefix: String,
        filenameSuffix: String
    ): File = {
        val file = write(data, filenamePrefix, filenameSuffix).toFile
        open(file)
        file
    }

    def open(file: File): Unit = {
        try {
            Desktop.getDesktop.open(file)
        } catch {
            case t: Throwable ⇒ throw OpeningFileFailedException(file, t)
        }
    }

    def write(
        data:           String,
        filenamePrefix: String,
        filenameSuffix: String
    ): Path = {

        val path = Files.createTempFile(
            sanitizeFileName(filenamePrefix),
            sanitizeFileName(filenameSuffix)
        )
        write(data.getBytes("UTF-8"), path)
        path
    }

    /**
     * A simple wrapper for `java.nio.Files.write(Path,byte[])`.
     */
    def write(data: Array[Byte], path: Path): Unit = Files.write(path, data)

    /**
     * This function takes a `Closeable` resource and a function `r` that will
     * process the `Closeable` resource.
     * This function takes care of the correct handling of `Closeable` resources.
     * When `r` has finished processing the resource or throws an exception, the
     * resource is closed.
     *
     * @note If `closable` is `null`, `null` is passed to `r`.
     *
     * @param closable The `Closeable` resource.
     * @param r The function that processes the `resource`.
     */
    def process[C <: Closeable, T](closable: C)(r: C ⇒ T): T = {
        // Implementation Note
        // Creating the closeable (I) in the try block doesn't make sense, hence
        // we don't need a by-name parameter. (If creating the closable fails,
        // then there is nothing to close.)
        try {
            r(closable)
        } finally {
            if (closable != null) closable.close()
        }
    }

    /**
     * This function takes a `Source` object and a function `r` that will
     * process the source.
     * This function takes care of the correct handling of resources.
     * When `r` has finished processing the source or throws an exception,
     * the source is closed.
     *
     * @note If `source` is `null`, `null` is passed to `r`.
     */
    def processSource[C <: Source, T](source: C)(r: C ⇒ T): T = {
        try {
            r(source)
        } finally {
            if (source != null) source.close()
        }
    }

}
