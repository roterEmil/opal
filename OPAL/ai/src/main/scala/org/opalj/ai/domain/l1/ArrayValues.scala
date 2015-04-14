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
package org.opalj
package ai
package domain
package l1

import scala.collection.SortedSet
import org.opalj.br._
import org.opalj.collection.commonPrefix
import org.opalj.log.OPALLogger
import org.opalj.log.Warn
import scala.reflect.ClassTag

/**
 * Enables the tracking of various properties related to arrays. It in particular enables
 * the tracking of an array's concrete content in some specific cases or the tracking
 * of information about an array's elements at a higher level. In both cases only
 * arrays up to a specified size (cf. [[maxArraySize]]) are tracked.
 *
 * @note '''This domain does not require modeling the heap'''. This however, strictly limits
 *      the kind of arrays that can be tracked/the information about elements that
 *      can be tracked. Tracking the contents of arrays of arrays
 *      or the content of arrays of mutable values is not possible; unless we only
 *      track abstract properties that do not depend on the concrete array element's value.
 *      For example, if we just want to know the upper type bounds of the values stored
 *      in the array, then it is perfectly possible. This property cannot change in an
 *      unsound fashion without directly accessing the array.
 *
 * @author Michael Eichberg
 */
trait ArrayValues extends l1.ReferenceValues with PerInstructionPostProcessing {
    domain: CorrelationalDomain with IntegerValuesDomain with ConcreteIntegerValues with TypedValuesFactory with Configuration with ClassHierarchy with LogContextProvider ⇒

    /**
     * Determines the maximum size of Integer ranges. The default value is 16.
     *
     * This setting can dynamically be adapted at runtime.
     */
    def maxArraySize: Int = 16

    /**
     * Returns `true` if instances of the given type - including subtypes - are
     * always effectively immutable. For example, `java.lang.String` and `java.lang.Class`
     * objects are effectively immutable.
     *
     * @note This method is used by the default implementation of [[reifyArray]] to
     *      decide if we want to track the array's content.
     */
    protected def isEffectivelyImmutable(objectType: ObjectType): Boolean = {
        (objectType eq ObjectType.String) || (objectType eq ObjectType.Class)
    }

    /**
     * Returns `true` if the specified array should be reified and precisely tracked.
     *
     * '''This method is intended to be overwritten by subclasses to configure which
     * arrays will be reified.''' Depending on the analysis task, it is in general only
     * useful to track selected arrays (e.g, arrays of certain types of values
     * or up to a specific length). For example, to facilitate the the resolution
     * of reflectively called methods, it might be interesting to track arrays
     * that contain string values.
     *
     * By default only arrays of known immutable values up to a size of [[maxArraySize]]
     * are reified.
     *
     * @note Tracking the content of arrays generally has a significant performance
     *      impact and should be limited to cases where it is absolutely necessary.
     *      "Just tracking the contents of arrays" to improve the overall precision
     *      is in most cases not helpful.
     *
     * @note If we track information about the values of an array at a higher-level,
     *      where the properties do not depend on the concrete values, then it is also
     *      possible to track those arrays.
     */
    protected def reifyArray(pc: PC, count: Int, arrayType: ArrayType): Boolean = {
        count <= maxArraySize && (
            arrayType.componentType.isBaseType ||
            (
                arrayType.componentType.isObjectType &&
                isEffectivelyImmutable(arrayType.componentType.asObjectType)
            )
        )
    }

    // We do not refine the type DomainArrayValue any further since we also want
    // to use the super level ArrayValue class to represent arrays for which we have
    // no further knowledge.
    // DON'T: type DomainArrayValue <: ArrayValue with DomainSingleOriginReferenceValue

    type DomainInitializedArrayValue <: InitializedArrayValue with DomainArrayValue
    val DomainInitializedArrayValue: ClassTag[DomainInitializedArrayValue]

    /**
     * Represents some (multi-dimensional) array where the (initialized) dimensions have
     * the given size.
     */
    // NOTE THAT WE CANNOT STORE SIZE INFORMATION ABOUT N-DIMENSIONAL ARRAYS WHERE N IS
    // LARGER THAN 2.
    protected class InitializedArrayValue(
        origin: ValueOrigin,
        theType: ArrayType,
        val lengths: List[Int],
        t: Timestamp)
            extends ArrayValue(origin, isNull = No, isPrecise = true, theType, t) {
        this: DomainInitializedArrayValue ⇒

        def this(
            origin: ValueOrigin,
            theType: ArrayType,
            length: Int,
            t: Timestamp) = {
            this(origin, theType, lengths = List(length), t)
        }

        assert(
            lengths.size >= 1 && lengths.size <= 2,
            "tracking the concrete size of the nth Dimension - for n > 2 - of arrays is not supported")

        override def length: Some[Int] = Some(lengths.head)

        override def updateT(
            t: Timestamp,
            origin: ValueOrigin, isNull: Answer): DomainArrayValue = {
            InitializedArrayValue(origin, theUpperTypeBound, lengths, t)
        }

        override def doJoinWithNonNullValueWithSameOrigin(
            joinPC: PC,
            other: DomainSingleOriginReferenceValue): Update[DomainSingleOriginReferenceValue] = {

            other match {
                case DomainInitializedArrayValue(that) if (this.theUpperTypeBound eq that.theUpperTypeBound) ⇒
                    val prefix = commonPrefix(this.lengths, that.lengths)
                    if (prefix eq this.lengths) {
                        if (this.t == that.t)
                            NoUpdate
                        else
                            TimestampUpdate(that)
                    } else if (prefix eq that.lengths) {
                        StructuralUpdate(that.updateT(nextT()))
                    } else {
                        val newT = if (this.t == that.t) this.t else nextT()
                        if (prefix.nonEmpty)
                            StructuralUpdate(InitializedArrayValue(origin, this.theType, prefix, newT))
                        else
                            StructuralUpdate(ArrayValue(origin, No, true, this.theType, newT))
                    }

                case _ ⇒
                    super.doJoinWithNonNullValueWithSameOrigin(joinPC, other) match {
                        case NoUpdate ⇒
                            // => This array and the other array have a corresponding
                            //    abstract representation (w.r.t. the next abstraction level!)
                            //    but we still need to drop the concrete information
                            val newT = if (other.t == this.t) this.t else nextT()
                            StructuralUpdate(
                                ArrayValue(origin, No, true, theType, newT)
                            )
                        case answer ⇒ answer
                    }
            }
        }

        override def abstractsOver(other: DomainValue): Boolean = {
            if (this eq other)
                return true;

            other match {
                case that: InitializedArrayValue ⇒
                    (this.theUpperTypeBound eq that.theUpperTypeBound) &&
                        this.lengths == that.lengths
                case that: ConcreteArrayValue ⇒
                    (that.theUpperTypeBound eq this.theUpperTypeBound) && {
                        this.lengths.head == that.length.get && (
                            this.lengths.tail.isEmpty || {
                                val subArrayValue = InitializedArrayValue(origin, theType.componentType.asArrayType, this.lengths.tail)
                                that.values.forall { v ⇒ subArrayValue.abstractsOver(v) }
                            }
                        )
                    }

                case _ ⇒ false
            }
        }

        override def adapt(target: TargetDomain, vo: ValueOrigin): target.DomainValue =
            target.InitializedArrayValue(vo, theType, lengths)

        override def equals(other: Any): Boolean = {
            other match {
                case that: InitializedArrayValue ⇒ (
                    (that eq this) ||
                    (
                        (that canEqual this) &&
                        this.origin == that.origin &&
                        (this.upperTypeBound eq that.upperTypeBound) &&
                        this.length == that.length
                    )
                )
                case _ ⇒
                    false
            }
        }

        override protected def canEqual(other: ArrayValue): Boolean =
            other.isInstanceOf[InitializedArrayValue]

        override def hashCode: Int = (origin * 31 + upperTypeBound.hashCode) * 31

        override def toString() = {
            s"${theUpperTypeBound.toJava}[@$origin;length=${length.get};<unknown values>]"
        }

    }

    /**
     * Represents arrays and their content.
     */
    // NOTE THAT WE DO NOT SUPPORT THE CASE WHERE THE ARRAY STORES MUTABLE VALUES!
    // In that case it may be possible to load a value from the array and manipulate
    // it which could lead to a new domain value which is not referred to by the array!
    protected class ConcreteArrayValue(
        origin: ValueOrigin,
        theType: ArrayType,
        val values: Array[DomainValue],
        t: Timestamp)
            extends ArrayValue(origin, isNull = No, isPrecise = true, theType, t) {
        this: DomainArrayValue ⇒

        override def length: Some[Int] = Some(values.size)

        override def doLoad(
            loadPC: PC,
            index: DomainValue,
            potentialExceptions: ExceptionValues): ArrayLoadResult = {
            if (potentialExceptions.nonEmpty) {
                // - a "NullPointerException" is not possible
                // - if an ArrayIndexOutOfBoundsException may be thrown then we certainly
                //   do not have enough information about the index...
                return ComputedValueOrException(
                    TypedValue(loadPC, theUpperTypeBound.componentType),
                    potentialExceptions)
            }

            intValue[ArrayLoadResult](index) { index ⇒
                ComputedValue(values(index))
            } {
                // This handles the case that we know that the index is not precise
                // but it is still known to be valid.
                super.doLoad(loadPC, index, potentialExceptions)
            }
        }

        override def doStore(
            storePC: PC,
            value: DomainValue,
            index: DomainValue,
            potentialExceptions: ExceptionValues): ArrayStoreResult = {
            // Here, a "NullPointerException" is not possible
            if (potentialExceptions.nonEmpty) {
                // In both of the following cases, we are no longer able to trace
                // the contents of the array.
                // - if an ArrayIndexOutOfBoundsException may be thrown then we certainly
                //   do not have enough information about the index, hence we don't
                //   know which value may have changed.
                // - if an ArrayStoreException may be thrown, we are totally lost..

                // When an exception is thrown the array remains untouched,
                // however, if no exception is thrown, we are no longer able to
                // approximate the state of the array's values; some value was changed
                // somewhere...
                val abstractArrayValue = ArrayValue(origin, No, true, theUpperTypeBound, t)
                registerOnRegularControlFlowUpdater(domainValue ⇒
                    domainValue match {
                        case that: ArrayValue if that eq this ⇒ abstractArrayValue
                        case _                                ⇒ domainValue
                    }
                )
                return ComputationWithSideEffectOrException(potentialExceptions);
            }

            // If we reach this point none of the given exceptions is guaranteed to be thrown
            // However, we now have to provide the solution for the happy path
            intValue[ArrayStoreResult](index) { index ⇒
                // let's check if we need to do anything
                if (values(index) != value) {
                    // TODO [BUG] Mark array as dead
                    var newArrayValue: DomainValue = null // <= we create the new array value only on demand and at most once!
                    registerOnRegularControlFlowUpdater { someDomainValue ⇒
                        if (someDomainValue eq ConcreteArrayValue.this) {
                            if (newArrayValue == null) {
                                newArrayValue = ArrayValue(origin, theType, values.updated(index, value))
                            }
                            newArrayValue
                        } else {
                            someDomainValue
                        }
                    }
                }
                ComputationWithSideEffectOnly
            } {
                // This handles the case that the index is not precise, but still
                // known to be valid. In this case we have to resort to the
                // abstract representation of the array.

                // TODO [BUG] Mark array as dead
                ComputationWithSideEffectOrException(potentialExceptions)
            }
        }

        override def doJoinWithNonNullValueWithSameOrigin(
            joinPC: PC,
            other: DomainSingleOriginReferenceValue): Update[DomainSingleOriginReferenceValue] = {

            other match {
                case that: ConcreteArrayValue if this.values.size == that.values.size && this.t == that.t ⇒
                    var update: UpdateType = NoUpdateType
                    var isOther: Boolean = true
                    val allValues = this.values.view.zip(that.values)
                    val newValues =
                        (allValues map { (v) ⇒
                            val (v1, v2) = v
                            if (v1 ne v2) {
                                val joinResult = v1.join(joinPC, v2)
                                joinResult match {
                                    case NoUpdate ⇒
                                        v1
                                    case SomeUpdate(newValue) ⇒
                                        if (v2 ne newValue) {
                                            isOther = false
                                        }
                                        update = joinResult &: update
                                        newValue
                                }
                            } else
                                v1
                        }).toArray // <= forces the evaluation - WHICH IS REQUIRED
                    update match {
                        case NoUpdateType ⇒ NoUpdate
                        case _ ⇒
                            if (isOther) {
                                update(other)
                            } else
                                update(ArrayValue(origin, theType, newValues))
                    }

                case _ ⇒
                    val answer = super.doJoinWithNonNullValueWithSameOrigin(joinPC, other)
                    if (answer == NoUpdate) {
                        // => This array and the other array have a corresponding
                        //    abstract representation (w.r.t. the next abstraction level!)
                        //    but we still need to drop the concrete information
                        StructuralUpdate(
                            ArrayValue(origin, No, true, theUpperTypeBound, nextT)
                        )
                    } else {
                        answer
                    }
            }
        }

        override def adapt(target: TargetDomain, vo: ValueOrigin): target.DomainValue =
            target match {

                case thatDomain: l1.ArrayValues ⇒
                    val adaptedValues =
                        values.map(_.adapt(target, vo).asInstanceOf[thatDomain.DomainValue])
                    thatDomain.ArrayValue(
                        vo, theUpperTypeBound, adaptedValues).
                        asInstanceOf[target.DomainValue]

                case thatDomain: l1.ReferenceValues ⇒
                    thatDomain.ArrayValue(vo, No, true, theUpperTypeBound, thatDomain.nextT()).
                        asInstanceOf[target.DomainValue]

                case thatDomain: l0.TypeLevelReferenceValues ⇒
                    thatDomain.InitializedArrayValue(vo, theUpperTypeBound, List(values.size)).
                        asInstanceOf[target.DomainValue]

                case _ ⇒ super.adapt(target, vo)
            }

        override def equals(other: Any): Boolean = {
            other match {
                case that: ConcreteArrayValue ⇒ (
                    (that eq this) ||
                    (
                        (that canEqual this) &&
                        this.origin == that.origin &&
                        (this.upperTypeBound eq that.upperTypeBound) &&
                        this.values == that.values
                    )
                )
                case _ ⇒ false
            }
        }

        override protected def canEqual(other: ArrayValue): Boolean =
            other.isInstanceOf[ConcreteArrayValue]

        override def hashCode: Int = origin * 79 + upperTypeBound.hashCode

        override def toString() = {
            val valuesAsString = values.mkString("«", ", ", "»")
            s"${theType.toJava}[@$origin;length=${values.size};$valuesAsString]"
        }
    }

    override def NewArray(
        pc: PC,
        count: DomainValue,
        arrayType: ArrayType): DomainArrayValue = {

        val sizeOption = this.intValueOption(count)
        if (sizeOption.isEmpty)
            return ArrayValue(pc, No, isPrecise = true, arrayType, nextT()); // <====== early return

        val size: Int = sizeOption.get
        if (!reifyArray(pc, size, arrayType))
            return InitializedArrayValue(pc, arrayType, List(size));

        if (size >= 256)
            OPALLogger.logOnce(
                Warn(
                    "analysis configuration",
                    s"tracking very large arrays (${arrayType.toJava}) "+
                        "usually incurrs significant overhead without increasing "+
                        "the precision of the analysis.")
            )
        val virtualOrigin = 0xFFFF + pc * 1024

        val array: Array[DomainValue] = new Array[DomainValue](size)
        var i = 0; while (i < size) {
            // we initialize each element with a new instance and also
            // assign each value with a unique PC
            array(i) = DefaultValue(virtualOrigin + i, arrayType.componentType)
            i += 1
        }
        ArrayValue(pc, arrayType, array)

    }

    //
    // DECLARATION OF ADDITIONAL FACTORY METHODS
    //

    protected def ArrayValue( // for ArrayValue
        origin: ValueOrigin,
        theUpperTypeBound: ArrayType,
        values: Array[DomainValue]): DomainArrayValue

    def InitializedArrayValue(
        origin: ValueOrigin,
        arrayType: ArrayType,
        counts: List[Int],
        t: Timestamp): DomainArrayValue

}
