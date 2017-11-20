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
package org.opalj.br.invokedynamicrewriting

import java.io.File

import org.opalj.br.FixturesTest
import org.opalj.util.ScalaMajorVersion

/**
 * Test if OPAL is able to rewrite a simple lambda expression and check if the rewritten bytecode
 * is executable.
 *
 * @author Andreas Muttscheller
 */
class SimpleLambdaAddTest extends FixturesTest {
    val fixtureFiles = new File(s"OPAL/bi/target/scala-$ScalaMajorVersion/test-classes/lambdas-1.8-g-parameters-genericsignature.jar")

    describe("a simple lambda add") {
        it("should calculate 2+2 correctly") {
            val c = byteArrayClassLoader.loadClass("lambdas.InvokeDynamics")
            val instance = c.newInstance()
            val m = c.getMethod("simpleLambdaAdd", Integer.TYPE, Integer.TYPE)
            val res = m.invoke(instance, new Integer(2), new Integer(2))

            assert(res.asInstanceOf[Integer] == 4)
        }
    }
}
