/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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
package frb
package analysis

import AnalysisTest._
import analyses._
import br._
import br.analyses._
import java.net.URL

/**
 * Unit test for BoxingImmediatelyUnboxedToPerformCoercion.
 *
 * @author Daniel Klauer
 * @author Peter Spieler
 */
@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TestBoxingImmediatelyUnboxedToPerformCoercion extends AnalysisTest {
    import TestBoxingImmediatelyUnboxedToPerformCoercion._

    it should "detect that BoxingImmediatelyUnboxedToPerformCoercion/"+
        "NewIntegerToDoubleValue:40 creates a new Integer object (boxing) and "+
        "immediately calls doubleValue() on it (unboxing)" in {
            val declaringClass = ObjectType(
                "BoxingImmediatelyUnboxedToPerformCoercion/NewIntegerToDoubleValue")
            results should contain(LineAndColumnBasedReport(
                project.source(declaringClass),
                Severity.Info,
                declaringClass,
                MethodDescriptor.NoArgsAndReturnVoid,
                "test",
                Some(40),
                None,
                "Value boxed and immediately unboxed"))
        }

    it should "find 1 issue in total" in {
        results.size should be(1)
    }
}

object TestBoxingImmediatelyUnboxedToPerformCoercion {
    val project = makeProjectFromJar("BoxingImmediatelyUnboxedToPerformCoercion.jar")
    val results =
        new BoxingImmediatelyUnboxedToPerformCoercion[URL].analyze(project).toSet
}
