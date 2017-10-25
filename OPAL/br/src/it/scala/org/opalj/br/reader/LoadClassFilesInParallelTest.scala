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
package br
package reader

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.opalj.bytecode.JRELibraryFolder
import org.opalj.bi.TestResources.allBITestJARs

class LoadClassFilesInParallelTest extends FlatSpec with Matchers {

    behavior of "OPAL when reading class files (in parallel)"

    private[this] def commonValidator(classFile: ClassFile): Unit = {
        classFile.thisType should not be null
    }

    private[this] def publicInterfaceValidator(classFile: ClassFile): Unit = {
        commonValidator(classFile)
        // the body of no method should be available
        classFile.methods.forall(m ⇒ m.body.isEmpty)
    }

    for {
        file ← Iterator(JRELibraryFolder) ++ allBITestJARs()
        if file.isFile && file.canRead && file.getName.endsWith(".jar")
        path = file.getPath
    } {
        it should s"it should be able to reify all class files in $path" in {
            Java8Framework.ClassFiles(file) foreach { e ⇒ val (cf, _) = e; commonValidator(cf) }
        }

        it should s"it should be able to reify only the signatures of all methods in $path" in {
            Java8LibraryFramework.ClassFiles(file) foreach { cs ⇒
                val (cf, _) = cs
                publicInterfaceValidator(cf)
            }
        }
    }
}
