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
package ai
package debug

import java.awt.Desktop
import java.io.FileOutputStream
import java.io.File

import scala.language.existentials
import scala.util.control.ControlThrowable

import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.Unparsed
import scala.xml.Text
import scala.xml.Unparsed

import br._
import br.instructions._

/**
 * Several utility methods to facilitate the development of the abstract interpreter/
 * new domains for the abstract interpreter, by creating various kinds of dumps of
 * the state of the interpreter.
 *
 * ==Thread Safety==
 * This object is thread-safe.
 *
 * @author Michael Eichberg
 */
object XHTML {

    private[this] val dumpMutex = new Object

    /**
     * Stores the time when the last dump was created.
     *
     * We generate dumps on errors only if the specified time has passed by to avoid that
     * we are drowned in dumps. Often, a single bug causes many dumps to be created.
     */
    private[this] var _lastDump = new java.util.concurrent.atomic.AtomicLong(0l)

    private[this] def lastDump_=(currentTimeMillis: Long) {
        _lastDump.set(currentTimeMillis)
    }

    private[this] def lastDump = _lastDump.get()

    def dumpOnFailure[T, D <: Domain](
        classFile: ClassFile,
        method: Method,
        ai: AI[_ >: D],
        theDomain: D,
        minimumDumpInterval: Long = 500l)(
            f: AIResult { val domain: theDomain.type } ⇒ T): T = {
        val result = ai(classFile, method, theDomain)
        val operandsArray = result.operandsArray
        val localsArray = result.localsArray
        try {
            if (result.wasAborted)
                throw new RuntimeException("interpretation aborted")
            f(result)
        } catch {
            case ct: ControlThrowable ⇒ throw ct
            case e: Throwable ⇒
                val currentTime = System.currentTimeMillis()
                if ((currentTime - lastDump) > minimumDumpInterval) {
                    lastDump = currentTime
                    val title = Some("Generated due to exception: "+e.getMessage())
                    val dump =
                        XHTML.dump(
                            title,
                            Some(classFile), Some(method), method.body.get,
                            theDomain
                        )(operandsArray, localsArray)
                    XHTML.writeAndOpenDump(dump) //.map(_.deleteOnExit)
                } else {
                    Console.err.println("[info] dump suppressed: "+e.getMessage())
                }
                throw e
        }
    }

    /**
     * In case that during the validation some exception is thrown, a dump of
     * the current memory layout is written to a temporary file and opened in a
     * browser; the number of dumps that are generated is controlled by
     * `timeInMillisBetweenDumps`. If a dump is suppressed a short message is
     * printed on the console.
     *
     * @param f The funcation that performs the validation of the results.
     */
    def dumpOnFailureDuringValidation[T](
        classFile: Option[ClassFile],
        method: Option[Method],
        code: Code,
        result: AIResult,
        minimumDumpInterval: Long = 500l)(
            f: ⇒ T): T = {
        val operandsArray = result.operandsArray
        val localsArray = result.localsArray
        try {
            if (result.wasAborted) throw new RuntimeException("interpretation aborted")
            f
        } catch {
            case ct: ControlThrowable ⇒ throw ct
            case e: Throwable ⇒
                val currentTime = System.currentTimeMillis()
                if ((currentTime - lastDump) > minimumDumpInterval) {
                    lastDump = currentTime
                    writeAndOpenDump(
                        dump("Dump generated due to exception: "+e.getMessage(),
                            classFile.get,
                            method.get,
                            result)
                    )
                } else {
                    Console.err.println("dump suppressed: "+e.getMessage())
                }
                throw e
        }
    }

    def htmlTemplate(
        title: Option[String] = None,
        body: NodeSeq): Node = {
        // HTML 5 XML serialization (XHTML 5)
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
        <meta http-equiv='Content-Type' content='application/xhtml+xml; charset=utf-8' />
        <style>
        { styles }
        </style>
        </head>
        <body>
        { scala.xml.Unparsed(title.getOrElse("")) }
        { body }
        </body>
        </html>
    }

    def dump(
        header: String,
        classFile: ClassFile,
        method: Method,
        result: AIResult): Node = {
        import result._
        htmlTemplate(
            Some(header),
            dumpTable(
                Some(classFile), Some(method), code, domain)(
                    operandsArray, localsArray))
    }

    def dump(
        header: Option[String],
        classFile: Option[ClassFile],
        method: Option[Method],
        code: Code,
        domain: Domain)(
            operandsArray: TheOperandsArray[domain.Operands],
            localsArray: TheLocalsArray[domain.Locals]): Node = {
        htmlTemplate(
            header,
            dumpTable(classFile, method, code, domain)(
                operandsArray, localsArray))
    }

    def writeAndOpenDump(node: Node): Option[File] = {
        import java.awt.Desktop
        import java.io.FileOutputStream

        try {
            if (Desktop.isDesktopSupported) {
                val desktop = Desktop.getDesktop()
                val file = File.createTempFile("OPAL-AI-Dump", ".html")
                val fos = new FileOutputStream(file)
                fos.write(node.toString.getBytes("UTF-8"))
                fos.close()
                desktop.open(file)
                return Some(file)
            }
            println("No desktop support available - cannot open the dump in a browser.")
        } catch {
            case e: Exception ⇒
                println("Opening the AI dump in the OS's default app failed: "+e.getMessage)
        }
        println(node.toString)

        None
    }

    private def styles: String =
        process(this.getClass().getResourceAsStream("dump.head.fragment.css"))(
            scala.io.Source.fromInputStream(_).mkString
        )

    def dumpTable(
        classFile: Option[ClassFile],
        method: Option[Method],
        code: Code,
        domain: Domain)(
            operandsArray: TheOperandsArray[domain.Operands],
            localsArray: TheLocalsArray[domain.Locals]): Node = {

        val indexedExceptionHandlers = indexExceptionHandlers(code).toSeq.sortWith(_._2 < _._2)
        val exceptionHandlers =
            (
                for ((eh, index) ← indexedExceptionHandlers) yield {
                    "⚡: "+index+" "+eh.catchType.map(_.toJava).getOrElse("<finally>")+
                        " ["+eh.startPC+","+eh.endPC+")"+" => "+eh.handlerPC
                }
            ).map(eh ⇒ <p>{ eh }</p>)

        val annotations =
            this.annotations(method) map { annotation ⇒
                <span class="annotation">
                { Unparsed(annotation.replace("\n", "<br>").replace("\t", "&nbsp;&nbsp;&nbsp;")) }
                </span><br />
            }

        <div>
        <table>
            <caption>
            	<div class="annotations">
                { annotations }
                </div>
        		{ caption(classFile, method) }
        	</caption>
            <thead>
            <tr><th class="pc">PC</th>
                <th class="instruction">Instruction</th>
                <th class="stack">Operand Stack</th>
                <th class="registers">Registers</th>
                <th class="properties">Properties</th></tr>
            </thead>
            <tbody>
            { dumpInstructions(code, domain)(operandsArray, localsArray) }
            </tbody>
        </table>
        { exceptionHandlers }
        </div>
    }

    private def annotations(method: Option[Method]): Seq[String] = {
        val annotations =
            method.map(m ⇒ (m.runtimeVisibleAnnotations ++ m.runtimeInvisibleAnnotations))
        annotations.map(_.map(_.toJava)).getOrElse(Nil)
    }

    private def caption(classFile: Option[ClassFile], method: Option[Method]): String = {
        val modifiers = if (method.isDefined && method.get.isStatic) "static " else ""
        val typeName = classFile.map(_.thisType.toJava).getOrElse("")
        val methodName = method.map(m ⇒ m.toJava).getOrElse("&lt; method &gt;")
        modifiers + typeName+"{ "+methodName+" }"
    }

    private def indexExceptionHandlers(code: Code) =
        code.exceptionHandlers.zipWithIndex.toMap

    private def dumpInstructions(
        code: Code,
        domain: Domain)(
            operandsArray: TheOperandsArray[domain.Operands],
            localsArray: TheLocalsArray[domain.Locals]): Array[Node] = {
        val indexedExceptionHandlers = indexExceptionHandlers(code)
        val instrs = code.instructions.zipWithIndex.zip(operandsArray zip localsArray).filter(_._1._1 ne null)
        for (((instruction, pc), (operands, locals)) ← instrs) yield {
            var exceptionHandlers = code.handlersFor(pc).map(indexedExceptionHandlers(_)).mkString(",")
            if (exceptionHandlers.size > 0) exceptionHandlers = "⚡: "+exceptionHandlers
            dumpInstruction(pc, instruction, Some(exceptionHandlers), domain)(
                operands, locals)
        }
    }

    def dumpInstruction(
        pc: Int,
        instruction: Instruction,
        exceptionHandlers: Option[String],
        domain: Domain)(
            operands: domain.Operands,
            locals: domain.Locals): Node = {
        <tr class={ if (operands eq null /*||/&& locals eq null*/ ) "not_evaluated" else "evaluated" }>
            <td class="pc">{ Unparsed(pc.toString + "<br>" + exceptionHandlers.getOrElse("")) }</td>
            <td class="instruction">{ Unparsed(instruction.toString(pc).replace("\n", "<br>")) }</td>
            <td class="stack">{ dumpStack(operands) }</td>
            <td class="locals">{ dumpLocals(locals) }</td>
            <td class="properties">{ domain.properties(pc).getOrElse("<None>") }</td>
        </tr >
    }

    def dumpStack(operands: Operands[_]): Node =
        if (operands eq null)
            <em>Information about operands is not available.</em>
        else {
            <ul class="Stack">
            { operands.map(op ⇒ <li>{ op.toString() }</li>) }
            </ul>
        }

    def dumpLocals(locals: Locals[_ <: AnyRef /**/ ]): Node =
        if (locals eq null)
            <em>Information about the local variables is not available.</em>
        else {
            <ol start="0" class="registers">
            { locals.map(l ⇒ if (l eq null) "UNUSED" else l.toString()).map(l ⇒ <li>{ l }</li>).iterator }
            </ol>
        }

    def throwableToXHTML(throwable: Throwable): scala.xml.Node = {
        val node =
            if (throwable.getStackTrace() == null ||
                throwable.getStackTrace().size == 0) {
                <div>{ throwable.getClass().getSimpleName() + " " + throwable.getMessage() }</div>
            } else {
                val stackElements =
                    for { stackElement ← throwable.getStackTrace() } yield {
                        <tr>
                        	<td>{ stackElement.getClassName() }</td>
                        	<td>{ stackElement.getMethodName() }</td>
                        	<td>{ stackElement.getLineNumber() }</td>
                        </tr>
                    }
                val summary = throwable.getClass().getSimpleName()+" "+throwable.getMessage()

                <details>
                    <summary>{ summary }</summary>
                    <table>{ stackElements }</table>
                </details>
            }

        if (throwable.getCause() ne null) {
            val causedBy = throwableToXHTML(throwable.getCause())
            <div style="background-color:yellow">{ node } <p>caused by:</p>{ causedBy }</div>
        } else {
            node
        }
    }

    def evaluatedInstructionsToXHTML(evaluated: List[PC]) = {
        val header = "Evaluated instructions:<div style=\"margin-left:2em;\">"
        val footer = "</div>"
        val subroutineStart = "<details><summary>Subroutine</summary><div style=\"margin-left:2em;\">"
        val subroutineEnd = "</div></details>"

        var openSubroutines = 0
        val asStrings = evaluated.reverse.map { instruction ⇒
            instruction match {
                case SUBROUTINE_START ⇒
                    openSubroutines += 1
                    subroutineStart
                case SUBROUTINE_END ⇒
                    openSubroutines -= 1
                    subroutineEnd
                case _ ⇒ instruction.toString+" "
            }
        }

        header +
            asStrings.mkString("") +
            (
                if (openSubroutines > 0) {
                    var missingSubroutineEnds = subroutineEnd
                    openSubroutines -= 1
                    while (openSubroutines > 0) {
                        missingSubroutineEnds += subroutineEnd
                        openSubroutines -= 1
                    }
                    missingSubroutineEnds
                } else
                    ""
            ) +
                footer
    }
}

