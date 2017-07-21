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
package fpcf
package properties

sealed trait EscapePropertyMetaInforation extends PropertyMetaInformation {
    final type Self = EscapeProperty
}

sealed abstract class EscapeProperty extends Property with EscapePropertyMetaInforation {
    final def key: PropertyKey[EscapeProperty] = EscapeProperty.key
}

/**
 * Describes lifetime of object instance. This is classically used for compiler optimizations
 * such as scalar replacement, stack allocation or removal of synchronization.
 * Choi et al. [1] describes two predicates that can be used to describe the properties relevant
 * to escape information.
 *
 *      "Let O be an object instance and M be a method invocation. O is said to escape M, denoted as
 *      Escapes(O, M), if the lifetime of O may exceed the lifetime of M."
 *
 *      "Let O be an object instance and T be a thread (instance). O is said to escape T, again
 *      denoted as Escapes(O, T), if another thread, T’ != T, may access O."
 *
 * Furthermore it holds that "For any object O, !Escapes(O, M) implies !Escapes(O, T), where method
 * M is invoked in thread T." [1]
 *
 * [[NoEscape]] now refers to the property of an object instance O created in method M for that
 * !Escapes(O, M) holds and no other method than M has access to O. This implies that there is no
 * method M' != M that can access O (at least  when disregarding reflection and native code).
 * Objects with this property can be allocated at the stack or even scalar-replaced [2].
 *
 * An object instance O created in method M and thread T has the property [[ArgEscape]], if it holds
 * !Escapes(O, M) but M passes O as parameter to a method which does not let O escape. This
 * implies that only M and methods M' that are (transitively) called by M have access to O.
 * For objects that have the property [[ArgEscape]] no synchronization is needed and they can
 * be allocated on the stack.
 * Note that Kotzmann and Mössenböck [2] denotes the exact same property as MethodEscape, which is
 * not the same property as [[MethodEscape]].
 *
 * An object instance O created in method M and thread T has the property [[MethodEscape]], if it
 * holds that Escape(O, M) but not Escapes(O, T). This is the case if O is returned by M but all
 * callers of M do not let O escape the thread. The return of O is either directly via the return
 * statement ([[MethodEscapeViaReturn]]), or by assigning O to a field of a parameter
 * ([[MethodEscapeViaParameterAssignment]]) or the return value
 * ([[MethodEscapeViaReturnAssignment]]). For objects that are at least [[MethodEscape]] no
 * synchronization is needed.
 *
 * An object instance O created in method M and thread T has the property [[GlobalEscape]], if it
 * holds that Escapes(O, M) and Escapes(O, T). For example this is the case if O gets assigned to
 * a static field ([[GlobalEscapeViaStaticFieldAssignment]] but also if assigned to a field of an
 * object that has also [[GlobalEscape]] as property ([[GlobalEscapeViaHeapObjectAssignment]]).
 * Objects that have the property  [[GlobalEscape]] have to be allocated on the heap and
 * synchronization mechanisms can not be removed/proper synchronization is required if the
 * object is accessed concurrently – the latter may be the goal of static analyses that find
 * concurrency bugs).
 *
 * The property values are partially ordered:
 *      [[GlobalEscape]] < [[MethodEscape]] < [[ArgEscape]] < [[NoEscape]].
 * Algorithms are free to over approximate this property, i.e. for object
 * instance O with actual property P it is okay to say O has property P' if P < P'.
 * If they simply don't know the actual property they should use [[MaybeEscape]].
 *
 * [[org.opalj.br.AllocationSite]] and [[org.opalj.br.analyses.FormalParameter]] are used as
 * [[Entity]] in combination with this property.
 *
 * [1] Choi, Jong-Deok, Manish Gupta, Mauricio Serrano, Vugranam C. Sreedhar, and Sam Midkiff.
 * "Escape Analysis for Java." In Proceedings of the 14th ACM SIGPLAN Conference on
 * Object-Oriented Programming, Systems, Languages, and Applications, 1–19. OOPSLA ’99.  New
 * York, NY, USA: ACM, 1999.
 *
 * [2] Kotzmann, Thomas, and Hanspeter Mössenböck. “Escape Analysis in the Context of Dynamic
 * Compilation and Deoptimization.” In Proceedings of the 1st ACM/USENIX International Conference
 * on Virtual Execution Environments, 111–120. VEE ’05. New York, NY, USA: ACM, 2005.
 *
 * @author Florian Kuebler
 */
object EscapeProperty extends EscapePropertyMetaInforation {
    final val key: PropertyKey[EscapeProperty] = PropertyKey.create(
        // Name of the property
        "EscapeProperty",
        // fallback value
        MaybeEscape,
        // cycle-resolution strategy
        MaybeEscape
    )
}

/**
 * Used, when we know nothing about the escape property so far or the analysis.
 *
 * @see [[EscapeProperty]] for further details.
 */
case object MaybeEscape extends EscapeProperty {
    final val isRefineable = true
}

/**
 * ''The object is accessible only from within the method of creation. Objects with this
 * escape level are called method-local.''
 *
 * @see [[EscapeProperty]] for further details.
 */
case object NoEscape extends EscapeProperty {
    final val isRefineable = false
}

/**
 * This property is for technical purpose only. It will never be the final property value.
 * It states that an object instance could have the property [[NoEscape]] but also every other
 * final property value down to [[GlobalEscape]].
 * Used if we know that the escape property of an object instance on which its constructor was
 * called only depends on the escape property of the self reference of the called constructor.
 *
 * An object instance passed to a [[ConditionallyNoEscape]] constructor can be
 * at most [[ConditionallyNoEscape]] unless it is refined to [[NoEscape]].
 */
case object ConditionallyNoEscape extends EscapeProperty {
    final val isRefineable: Boolean = true
}

/**
 * The object escapes the current method M via the arguments of a method M', that is called by M
 * but does not let the argument escape. This implies that the object is also local to the thread.
 *
 * @example
 * Given the following code:
 * {{{
 * public class X{
 *  public Object f;
 *  public void foo() {
 *   Object o = new Object();        // ALLOCATION SITE
 *   bar(o);
 *  }
 *  public int bar(Object p) {
 *   if (p == null) // do not let p escape
 *    return -1;
 *   return 0;
 *  }
 * }
 * }}}
 *
 * @see [[EscapeProperty]] for further details.
 *
 * @author Florian Kuebler
 */
case object ArgEscape extends EscapeProperty {
    final val isRefineable = false
}

/**
 * This property is for technical purpose only. It will never be the final property value.
 * It states that an object instance is at most [[ArgEscape]], i.e. it will never be [[NoEscape]].
 * Used if we know that the escape property of an object instance, which was passed to a method, only
 * depends on the escape property of the formal parameter of the target method of the call.
 */
case object ConditionallyArgEscape extends EscapeProperty {
    final val isRefineable: Boolean = true
}

/**
 * The object escapes the method M in which it was created directly via the return of M or
 * indirectly via an assignment to a field of the return value or a parameter of M.
 * Objects that have the property [[MethodEscape]] does not escape its thread.
 *
 * @see [[EscapeProperty]] for further details.
 *
 *
 *
 * @note This property does not refer to the identically named property defined by Kotzmann
 *       and Mössenböck
 */
trait MethodEscape extends EscapeProperty {
    final val isRefineable = false
}
/**
 * Characterizes escapes via a return statement, where no caller let this return value escape
 * globally. (It may additionally escape by other means too, but this property
 * was derived first. It must not be the case that an additional escape has the
 * property [[GlobalEscape]].)
 *
 * @example
 * Given the following code:
 * {{{
 * public class X{
 *  public Object f;
 *  private Object foo() {
 *   Object o = new Object();        // ALLOCATION SITE
 *   return o;
 *  }
 *  public void bar() {
 *   foo(); // do not use the return
 *  }
 * }
 * }}}
 * An analysis is only expected to return [[MethodEscapeViaReturn]] for the object o
 * instantiated in foo, if the analyses knows(!) that foo is called only from bar.
 */
case object MethodEscapeViaReturn extends MethodEscape

/**
 * Characterizes escapes via an assignment to a field of a method parameter, where no caller let
 * this field escape globally. (It may additionally escape by other means too, but this property
 * was derived first. It must not be the case that an additional escape has the
 * property [[GlobalEscape]].)
 *
 * @example
 * Given the following code:
 * {{{
 * public class X{
 *  public Object f;
 *  private void foo(X param) {
 *   param.f = new Object();        // ALLOCATION SITE
 *  }
 *  public void bar() {
 *   foo(new X());
 *  }
 * }
 * }}}
 */
case object MethodEscapeViaParameterAssignment extends MethodEscape

/**
 * Characterizes escapes via an assignment to a field of the return value, where no caller let
 * this field escape globally. (It may additionally escape by other means too, but this property
 * was derived first. It must not be the case that an additional escape has the
 * property [[GlobalEscape]].)
 *
 * @example
 * Given the following code:
 * {{{
 * public class X{
 *  public Object f;
 *  private X foo() {
 *   Object o = new Object();        // ALLOCATION SITE
 *   X x = new X();
 *   x.f = o;
 *   return x;
 *  }
 *  public void bar() {
 *   foo(); // do not use the return
 *  }
 * }
 * }}}
 */
case object MethodEscapeViaReturnAssignment extends MethodEscape

/**
 * This property is for technical purpose only. It will never by the final property value.
 * It states that an object instance is at most [[MethodEscape]], i.e. it could also be
 * [[GlobalEscape]] but not [[ArgEscape]] or [[NoEscape]].
 * Used if we know that the escape property of an object instance, which escapes the method, only
 * depends on the escape property of the references to this object for all callers.
 *
 */
case object ConditionallyMethodEscape extends EscapeProperty {
    final val isRefineable: Boolean = true
}

/**
 * ''The object escapes globally, typically because it is assigned to a static variable or to a
 * field of a heap object.''
 *
 * This property should be used if and only if the analysis is conclusive and could determine
 * that the value definitively escapes globally.
 * If a more advanced analysis – potentially run later – could identify an object
 * as only [[MethodEscape]], [[ArgEscape]] or even [[NoEscape]] then the refineable property
 * [[MaybeEscape]] should be used.
 *
 * @example
 * Given the following library code:
 * {{{
 * public class X{
 *  public static Object o;
 *  public void m(boolean b) {
 *      Object o = new Object();        // ALLOCATION SITE
 *      if (b) X.o = o;
 *      return;
 *  }
 * }
 * }}}
 * An analysis is only expected to return [[GlobalEscapeViaStaticFieldAssignment]] for the object o
 * instantiated in m, if the analyses knows(!) that m is called and the parameter b is
 * potentially `true`. If the above code is found in a library it may very well be the case that
 * certain parameter values/combinations will never be used in a certain setting and – therefore –
 * o does not escape.
 *
 * However, from a pure technical point-of-view it may be useful/necessary to use
 * [[GlobalEscape]] at some point, to let depending computations know that no more
 * changes will happen and therefore the dependencies can be deleted.
 *
 * @see [[EscapeProperty]] for further details.
 * @author Florian Kuebler
 */
trait GlobalEscape extends EscapeProperty {
    final val isRefineable = false
}

/**
 * Characterizes escapes via the write to a static field. (It may additionally escape by other
 * means too, but this property was derived first.)
 *
 * @example
 * Given the following code:
 * {{{
 * public class X{
 *  public static Object o;
 *  public void m() {
 *      Object o = new Object();        // ALLOCATION SITE
 *      X.o = o;
 *      return;
 *  }
 * }
 * }}}
 */
case object GlobalEscapeViaStaticFieldAssignment extends GlobalEscape

/**
 * The object is assigned to a (global) heap object. (It may additionally escape by other
 * means too, but this property was derived first.)
 *
 * @example
 * Given the following code:
 * {{{
 * public class X{
 *  public static X o = new X();
 *  public Object f;
 *  public void m() {
 *      Object o = new Object();        // ALLOCATION SITE
 *     X x = X.o;
 *     x.f = o;
 *      return;
 *  }
 * }
 * }}}
 */
case object GlobalEscapeViaHeapObjectAssignment extends GlobalEscape
