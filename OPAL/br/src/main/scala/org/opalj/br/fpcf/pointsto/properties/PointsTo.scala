/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package br
package fpcf
package pointsto
package properties

import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.FallbackReason
import org.opalj.fpcf.OrderedProperty
import org.opalj.fpcf.PropertyIsNotDerivedByPreviouslyExecutedAnalysis
import org.opalj.fpcf.PropertyKey
import org.opalj.fpcf.PropertyMetaInformation
import org.opalj.fpcf.PropertyStore

/**
 * Represent the set of types that have allocations reachable from the respective entry points.
 *
 * @author Florian Kuebler
 */
// TODO: we should definition sites (real points-to sets) instead of just the types
sealed trait PointsToPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = PointsTo
}

case class PointsTo private[properties] (
    private val orderedTypes: List[ObjectType],
    types:                    UIDSet[ObjectType]
) extends OrderedProperty
        with PointsToPropertyMetaInformation {

    assert(orderedTypes == null || orderedTypes.size == types.size)

    final def key: PropertyKey[PointsTo] = PointsTo.key

    override def toString: String = s"PointsTo(size=${types.size})"

    override def checkIsEqualOrBetterThan(e: Entity, other: PointsTo): Unit = {
        if (!types.subsetOf(other.types)) {
            throw new IllegalArgumentException(s"$e: illegal refinement of property $other to $this")
        }
    }

    def updated(newTypes: TraversableOnce[ObjectType]): PointsTo = {
        var newOrderedTypes = orderedTypes
        var typesUnion = types
        for (t ← newTypes) {
            if (!types.contains(t)) {
                newOrderedTypes ::= t
                typesUnion += t
            }
        }
        new PointsTo(newOrderedTypes, typesUnion)
    }

    /**
     * Will return the types added most recently, dropping the `num` oldest ones.
     */
    def dropOldest(num: Int): Iterator[ObjectType] = {
        orderedTypes.iterator.take(types.size - num)
    }

    def numElements: Int = types.size

    override def equals(obj: Any): Boolean = {
        obj match {
            case that: PointsTo ⇒
                that.numElements == this.numElements && that.orderedTypes == this.orderedTypes
            case _ ⇒ false
        }
    }

    override def hashCode: Int = types.hashCode() * 31
}

object PointsTo extends PointsToPropertyMetaInformation {

    def apply(
        initialPointsTo: UIDSet[ObjectType]
    ): PointsTo = {
        new PointsTo(
            initialPointsTo.toList,
            initialPointsTo
        )
    }

    final val key: PropertyKey[PointsTo] = {
        val name = "opalj.PointsTo"
        PropertyKey.create(
            name,
            (_: PropertyStore, reason: FallbackReason, _: Entity) ⇒ reason match {
                case PropertyIsNotDerivedByPreviouslyExecutedAnalysis ⇒ NoTypes
                case _ ⇒
                    throw new IllegalStateException(s"no analysis is scheduled for property: $name")
            }
        )
    }
}

object NoTypes extends PointsTo(List.empty, UIDSet.empty)
