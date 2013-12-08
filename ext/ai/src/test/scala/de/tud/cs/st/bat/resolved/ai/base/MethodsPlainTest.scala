/* License (BSD Style License):
 * Copyright (c) 2009 - 2013
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
 *  - Neither the name of the Software Technology Group or Technische
 *    Universität Darmstadt nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific
 *    prior written permission.
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
package de.tud.cs.st
package bat
package resolved
package ai
package base

import reader.Java7Framework.ClassFiles
import domain._
import domain.l0._

import de.tud.cs.st.util.{ Answer, Yes, No, Unknown }

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.ParallelTestExecution
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.matchers.MatchResult

/**
 * Basic tests of the abstract interpreter.
 *
 * @author Michael Eichberg
 * @author Dennis Siebert
 */
@RunWith(classOf[JUnitRunner])
class MethodsPlainTest
        extends FlatSpec
        with ShouldMatchers /*with BeforeAndAfterAll */
        with ParallelTestExecution {

    import util.XHTML.dumpOnFailureDuringValidation
    import MethodsPlainTest._

    behavior of "the abstract interpreter"

    //
    // RETURNS
    it should "be able to analyze a method that does nothing" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "nop").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(None)
    }

    it should "be able to analyze a method that returns a fixed integer value" in {
        val domain = new RecordingDomain; import domain._;
        val method = classFile.methods.find(_.name == "iOne").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that returns a fixed long value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lOne").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that returns a fixed double value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dOne").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze a method that returns a fixed float value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fOne").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    //
    // LDC
    it should "be able to analyze a method that returns a fixed string value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "sLDC").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(
            Some(NonNullReferenceValue(Int.MinValue /*<=IGNORED*/ , ObjectType.String)))
    }

    it should "be able to analyze a method that returns a fixed class value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "cLDC").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(
            Some(NonNullReferenceValue(Int.MinValue /*<=IGNORED*/ , ObjectType.Class)))
    }

    //
    // RETURNS PARAMETER
    it should "be able to analyze a method that just returns a parameter value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "identity").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that just returns a parameter string" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "sOne").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(
            NonNullReferenceValue(Int.MinValue /*<=IGNORED*/ , ObjectType.String)
        ))
    }

    //
    // BINARY OPERATIONS ON INT
    it should "be able to analyze a method that adds two int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iAdd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that ands two int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iAnd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that divides two int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iDiv").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that multiplies two int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iMul").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that ors two int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iOr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that shift left an int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iShl").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that shift right an int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iShr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that reminder an int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iRem").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that substracts two int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iSub").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that logical shift right an int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iushr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze a method that XORs an int values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iushr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    //
    // BINARY OPERATIONS ON LONG
    it should "be able to analyze a method that adds two long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lAdd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    it should "be able to analyze a method that ands two long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lAnd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that divides two long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lDiv").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    it should "be able to analyze a method that multiplies two long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lMul").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that ors two long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lOr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that shift left an long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lShl").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that shift right an long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lShr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    it should "be able to analyze a method that reminder an long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lRem").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    it should "be able to analyze a method that substracts two long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lSub").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that logical shift right an long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lushr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that XORs an long values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lushr").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    //
    // BINARY OPERATIONS ON DOUBLE
    it should "be able to analyze a method that adds two double values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dAdd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze a method that divides two double values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dDiv").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a method that multiplies two double values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dMul").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze a method that reminder an double values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dRem").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a method that substracts two double values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dSub").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    //
    // BINARY OPERATIONS ON FLOAT
    it should "be able to analyze a method that adds two float values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fAdd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    it should "be able to analyze a method that divides two float values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fDiv").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a method that multiplies two float values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fMul").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    it should "be able to analyze a method that reminder an float values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fRem").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a method that substracts two float values" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fSub").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    //
    // INTEGER VALUE TO X CONVERSION
    it should "be able to analyze a method that casts an int to a byte" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iToByte").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AByteValue))
    }
    it should "be able to analyze a method that casts an int to a char" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iToChar").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ACharValue))
    }
    it should "be able to analyze a method that casts an int to a double" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iToDouble").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a method that casts an int to a float" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iToFloat").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a method that casts an int to a long" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iToLong").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that casts an int to a short" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iToShort").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AShortValue))
    }

    //
    // LONG VALUE TO X  CONVERSION
    it should "be able to analyze a method that casts an long to a double" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lToDouble").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a method that casts an long to a float" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lToFloat").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a method that casts an long to a int" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lToInt").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    //
    // DOUBLE VALUE TO X  CONVERSION

    it should "be able to analyze a method that casts an double to a float" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dToFloat").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a method that casts an double to a int" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dToInt").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that casts an double to a long" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dToLong").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    //
    // FLOAT VALUE TO X  CONVERSION
    it should "be able to analyze a method that casts an float to a double" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fToDouble").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a method that casts an float to a int" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fToInt").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a method that casts an float to a long" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fToLong").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    //
    // UNARY EXPRESSIONS
    it should "be able to analyze a method that returns a negativ float value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "fNeg").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    it should "be able to analyze a method that returns a negativ double value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "dNeg").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze a method that returns a negativ long value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "lNeg").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze a method that returns a negativ int value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "iNeg").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    //
    // TYPE CHECKS
    it should "be able to correctly handle casts" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "asSimpleMethods").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(TypedValue(-1, ObjectType("ai/MethodsPlain"))))
    }

    it should "be able to correctly handle an instance of" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "asSimpleMethodsInstance").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ABooleanValue))
    }

    //
    // GETTER AND SETTER FOR FIELDS
    it should "be able to analyze a classical setter method" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "setValue").get
        val result = BaseAI(classFile, method, domain)

        result should not be (null)
        domain.returnedValue should be(None)
    }

    it should "be able to analyze a classical getter method" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "getValue").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    //
    // GETTER AND SETTER FOR STATIC FIELDS
    it should "be able to analyze a classical static setter method" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "setSValue").get
        val result = BaseAI(classFile, method, domain)
        result should not be (null)
        domain.returnedValue should be(None)
    }

    it should "be able to analyze a classical static getter method" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "getSValue").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    //
    // LOADS AND STORES
    it should "be able to analyze integer load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localInt").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }

    it should "be able to analyze odd long load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localLongOdd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze even long load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localLongEven").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    it should "be able to analyze odd double load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localDoubleOdd").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze even double load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localDoubleEven").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze float load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localFloat").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    it should "be able to analyze object load and store commands" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "localSimpleMethod").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(
            Some(NonNullReferenceValue(Int.MinValue /*<=IGNORED*/ , ObjectType("ai/MethodsPlain")))
        )
    }

    //
    // PUSH CONSTANT VALUE
    it should "be able to analyze a push of null value" in {
        val domain = new RecordingDomain
        val method = classFile.methods.find(_.name == "pushNull").get
        /*val result =*/ BaseAI(classFile, method, domain)

        assert(
            domain.isNull(domain.returnedValue.get).yes,
            "unexpected nullness property of the returned value: "+domain.returnedValue.get)
    }
    it should "be able to analyze a push of byte value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushBipush").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AByteValue))
    }
    it should "be able to analyze a push of short value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushSipush").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AShortValue))
    }
    it should "be able to analyze a push of double const0 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushDoubleConst0").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a push of double const1 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushDoubleConst1").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }
    it should "be able to analyze a push of float const0 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushFloatConst0").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a push of float const1 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushFloatConst1").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a push of float const2 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushFloatConst2").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }
    it should "be able to analyze a push of int const-1 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConstn1").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of int const0 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConst0").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of int const1 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConst1").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of int const2 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConst2").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of int const3 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConst3").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of int const4 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConst4").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of int const5value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushIntConst5").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze a push of long const0 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushLongConst0").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    it should "be able to analyze a push of long const1 value" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "pushLongConst1").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }

    //
    // CREATE ARRAY
    it should "be able to analyze a new boolean array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewBooleanArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(BooleanType))))
    }
    it should "be able to analyze a new char array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewCharArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(CharType))))
    }
    it should "be able to analyze a new float array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewFloatArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(FloatType))))
    }

    it should "be able to analyze a new double array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewDoubleArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(DoubleType))))
    }
    it should "be able to analyze a new byte array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewByteArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(ByteType))))
    }
    it should "be able to analyze a new short array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewShortArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(ShortType))))
    }
    it should "be able to analyze a new int array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewIntArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(IntegerType))))
    }
    it should "be able to analyze a new long array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewLongArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(LongType))))
    }

    it should "be able to analyze a new Object array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewSimpleMethodsArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(1, ArrayType(ObjectType("ai/MethodsPlain")))))
    }

    it should "be able to analyze a new multidimensional Object array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "createNewMultiSimpleMethodsArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ArrayReferenceValue(2, ArrayType(ArrayType(ObjectType("ai/MethodsPlain"))))))
    }

    //
    // LOAD FROM AND STORE VALUE IN ARRAYS
    it should "be able to analyze to load and store an object in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "objectArray").get
        val result = BaseAI(classFile, method, domain)

        dumpOnFailureDuringValidation(Some(classFile), Some(method), method.body.get, result) {
            domain.returnedValue should be(Some(TypedValue(12, ObjectType("ai/MethodsPlain"))))
        }
    }

    it should "be able to analyze to load and store a byte in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "byteArray").get
        val result = BaseAI(classFile, method, domain)

        dumpOnFailureDuringValidation(Some(classFile), Some(method), method.body.get, result) {
            domain.returnedValue should be(Some(AByteValue))
        }
    }

    it should "be able to analyze to load and store a char in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "charArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ACharValue))
    }

    it should "be able to analyze to load and store a double in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "doubleArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to analyze to load and store a float in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "floatArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AFloatValue))
    }

    it should "be able to analyze to load and store a int in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "intArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AnIntegerValue))
    }
    it should "be able to analyze to load and store a long in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "longArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ALongValue))
    }
    it should "be able to analyze to load and store a short in an array" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "shortArray").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(AShortValue))
    }

    //
    // OTHER
    it should "be able to analyze a method that multiplies a value by two" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "twice").get
        /*val result =*/ BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(ADoubleValue))
    }

    it should "be able to return the correct type of an object if an object that is passed in is directly returned" in {
        implicit val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "asIs").get
        val t = ObjectType("some/Foo")
        val locals = new Array[Value](1)
        val theObject = TypedValue(-1, t)
        locals(0) = theObject
        BaseAI.perform(classFile, method, domain)(Some(locals))

        domain.returnedValue should be(Some(theObject))
    }

    it should "be able to analyze a method that creates an instance of an object using reflection" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "create").get
        val result = BaseAI(classFile, method, domain)

        domain.returnedValue should be(Some(TypedValue(1, ObjectType.Object)))
    }

    it should "be able to analyze a method that creates an object and which calls multiple methods of the new object" in {
        val domain = new RecordingDomain; import domain._
        val method = classFile.methods.find(_.name == "multipleCalls").get
        val result = BaseAI(classFile, method, domain)

        domain.returnedValue should be(None)
    }
}

private object MethodsPlainTest {

    class RecordingDomain extends domain.l0.TypeLevelDomain[String]
            with IgnoreSynchronization
            with IgnoreThrownExceptions {
        def identifier = "SimpleRecordingDomain"
        var returnedValue: Option[DomainValue] = _
        override def areturn(pc: Int, value: DomainValue) { returnedValue = Some(value) }
        override def dreturn(pc: Int, value: DomainValue) { returnedValue = Some(value) }
        override def freturn(pc: Int, value: DomainValue) { returnedValue = Some(value) }
        override def ireturn(pc: Int, value: DomainValue) { returnedValue = Some(value) }
        override def lreturn(pc: Int, value: DomainValue) { returnedValue = Some(value) }
        override def returnVoid(pc: Int) { returnedValue = None }
    }

    val classFile =
        ClassFiles(TestSupport.locateTestResources("classfiles/ai.jar", "ext/ai")).map(_._1).
            find(_.thisType.fqn == "ai/MethodsPlain").get
}