/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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
package collection
package immutable

import scala.collection.GenIterable
import scala.collection.GenTraversableOnce
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder
import scala.collection.generic.FilterMonadic

/**
 * A linked list which does not perform any length related checks. I.e., it fails in
 * case of `drop` and `take` etc. if the size of the list is smaller than expected.
 * Furthermore, all directly implemented methods use `while` loops for maxium
 * efficiency.
 *
 * @note	In most cases a `ChainedList` can be used as a drop-in replacement for a standard
 * 			Scala List.
 *
 * @note 	Some core methods, e.g. `drop` and `take`, have different
 * 			semantics when compared to the methods with the same name defined
 * 			by the Scala collections API. In this case these methods may
 * 			fail arbitrarily if the list is not long enough.
 * 			Therefore, `ChainedList` does not inherit from `scala...Seq`.
 *
 * @author Michael Eichberg
 */
sealed trait ChainedList[@specialized(Int) +T]
        extends TraversableOnce[T]
        with FilterMonadic[T, ChainedList[T]]
        with Serializable { self ⇒

    class WithFilter(p: T ⇒ Boolean) extends FilterMonadic[T, ChainedList[T]] {

        def map[B, That](f: T ⇒ B)(implicit bf: CanBuildFrom[ChainedList[T], B, That]): That = {
            val list = self
            val b = bf(list)
            var rest = self
            while (rest.nonEmpty) {
                val x = rest.head
                if (p(x)) b += f(x)
                rest = rest.tail
            }
            b.result
        }

        def flatMap[B, That](
            f: T ⇒ GenTraversableOnce[B]
        )(
            implicit
            bf: CanBuildFrom[ChainedList[T], B, That]
        ): That = {
            val list = self
            val b = bf(list)
            var rest = list
            while (rest.nonEmpty) {
                val x = rest.head
                if (p(x)) b ++= f(x).seq
                rest = rest.tail
            }
            b.result
        }

        def foreach[U](f: T ⇒ U): Unit = {
            var rest = self
            while (rest.nonEmpty) {
                val x = rest.head
                if (p(x)) f(x)
                rest = rest.tail
            }
        }

        def withFilter(q: T ⇒ Boolean): WithFilter = new WithFilter(x ⇒ p(x) && q(x))
    }

    // Defined by TraversableOnce: def isEmpty: Boolean
    final override def hasDefiniteSize: Boolean = true
    final override def isTraversableAgain: Boolean = true
    final override def seq: TraversableOnce[T] = this

    override def foreach[U](f: T ⇒ U): Unit = {
        var rest = this
        while (rest.nonEmpty) {
            f(rest.head)
            rest = rest.tail
        }
    }

    def flatMap[B, That](
        f: (T) ⇒ GenTraversableOnce[B]
    )(
        implicit
        bf: CanBuildFrom[ChainedList[T], B, That]
    ): That = {
        val b = bf(this)
        //OLD: foreach { t ⇒ f(t) foreach { e ⇒ builder += e } }
        var rest = this
        while (rest.nonEmpty) {
            val t = rest.head
            b ++= f(t).seq
            rest = rest.tail
        }
        b.result
    }

    /*
    def map[X](f: T ⇒ X): ChainedList[X] = {
        val result = new :&:(f(head), ChainedNil)
        var last = result
        var rest: ChainedList[T] = this.rest
        while (rest.nonEmpty) {
            val x = f(rest.head)
            rest = rest.tail
            val newLast = new :&:(x, ChainedNil)
            last.rest = newLast
            last = newLast
        }
        result
    }
    */
    def map[B, That](f: (T) ⇒ B)(implicit bf: CanBuildFrom[ChainedList[T], B, That]): That = {
        val builder = bf(this)
        var rest = this
        while (rest.nonEmpty) {
            val t = rest.head
            builder += f(t)
            rest = rest.tail
        }
        builder.result
    }

    def withFilter(p: (T) ⇒ Boolean): FilterMonadic[T, ChainedList[T]] = new WithFilter(p)

    def head: T
    def tail: ChainedList[T]
    def last: T = {
        var rest = this
        while (rest.tail.nonEmpty) { rest = rest.tail }
        rest.head
    }

    override def nonEmpty: Boolean
    def apply(index: Int): T = {
        var count = index
        var rest = this
        while (count > 0) {
            rest = rest.tail
            count -= 1
        }
        rest.head
    }
    def exists(f: T ⇒ Boolean): Boolean = {
        var rest = this
        while (rest.nonEmpty) {
            if (f(rest.head))
                return true;
            rest = rest.tail
        }
        false
    }
    def forall(f: T ⇒ Boolean): Boolean = {
        var rest = this
        while (rest.nonEmpty) {
            if (!f(rest.head))
                return false;
            rest = rest.tail
        }
        true
    }
    def contains[X >: T](e: X): Boolean = {
        var rest = this
        while (rest.nonEmpty) {
            if (rest.head == e)
                return true;
            rest = rest.tail
        }
        false
    }
    def find(p: T ⇒ Boolean): Option[T] = {
        var rest = this
        while (rest.nonEmpty) {
            val e = rest.head
            if (p(e))
                return Some(e);
            rest = rest.tail
        }
        None
    }
    override def size: Int = {
        var result = 0
        var rest = this
        while (rest.nonEmpty) {
            result += 1
            rest = rest.tail
        }
        result
    }
    def :&:[X >: T](x: X): ChainedList[X] = new :&:(x, this)
    def take(n: Int): ChainedList[T]
    def takeWhile(f: T ⇒ Boolean): ChainedList[T]
    def filter(f: T ⇒ Boolean): ChainedList[T]
    def drop(n: Int): ChainedList[T]
    def zip[X](other: GenIterable[X]): ChainedList[(T, X)] = {
        if (this.isEmpty)
            return this.asInstanceOf[ChainedNil.type];
        val otherIt = other.iterator
        if (!otherIt.hasNext)
            return ChainedNil;

        var thisIt = this.tail
        val result: :&:[(T, X)] = new :&:((this.head, otherIt.next), ChainedNil)
        var last = result
        while (thisIt.nonEmpty && otherIt.hasNext) {
            val newLast = new :&:((thisIt.head, otherIt.next), ChainedNil)
            last.rest = newLast
            last = newLast
            thisIt = thisIt.tail
        }
        result
    }
    def zip[X](other: ChainedList[X]): ChainedList[(T, X)] = {
        if (this.isEmpty)
            return this.asInstanceOf[ChainedNil.type];
        if (other.isEmpty)
            return other.asInstanceOf[ChainedNil.type];

        var thisIt = this.tail
        var otherIt = other.tail
        val result: :&:[(T, X)] = new :&:((this.head, other.head), ChainedNil)
        var last = result
        while (thisIt.nonEmpty && otherIt.nonEmpty) {
            val newLast = new :&:((thisIt.head, otherIt.head), ChainedNil)
            last.rest = newLast
            last = newLast
            thisIt = thisIt.tail
            otherIt = otherIt.tail
        }
        result
    }
    def zipWithIndex: ChainedList[(T, Int)] = {
        var index = 0
        map { e ⇒
            val currentIndex = index
            index += 1
            (e, currentIndex)
        }
    }
    def corresponds[X](other: ChainedList[X])(f: (T, X) ⇒ Boolean): Boolean = {
        if (this.isEmpty)
            return other.isEmpty;
        if (other.isEmpty)
            return false;
        // both lists have at least one element...
        if (!f(this.head, other.head))
            return false;

        var thisIt = this.tail
        var otherIt = other.tail
        while (thisIt.nonEmpty && otherIt.nonEmpty) {
            if (!f(thisIt.head, otherIt.head))
                return false;
            thisIt = thisIt.tail
            otherIt = otherIt.tail
        }
        thisIt.isEmpty && otherIt.isEmpty
    }
    def mapConserve[X >: T <: AnyRef](f: T ⇒ X): ChainedList[X]
    def reverse: ChainedList[T] = {
        var result: ChainedList[T] = ChainedNil
        var rest = this
        while (rest.nonEmpty) {
            result :&:= rest.head
            rest = rest.tail
        }
        result
    }
    override def mkString(pre: String, sep: String, post: String): String = {
        val result = new StringBuilder(pre)
        var rest = this
        if (rest.nonEmpty) {
            result.append(head.toString)
            rest = rest.tail
            while (rest.nonEmpty) {
                result.append(sep)
                result.append(rest.head.toString)
                rest = rest.tail
            }
        }

        result.append(post)
        result.toString
    }

    override def toIterable(): Iterable[T] = {
        new scala.collection.Iterable[T] {
            def iterator: Iterator[T] = self.toIterator
        }
    }

    def toIterator(): Iterator[T] = {
        new scala.collection.Iterator[T] {
            var rest = self
            def hasNext: Boolean = rest.nonEmpty
            def next(): T = {
                val result = rest.head
                rest = rest.tail
                result
            }
        }
    }

    /**
     * Returns a newly created `Traversable[T]` collection.
     */
    def toTraversable(): Traversable[T] = {
        new scala.collection.Traversable[T] {
            def foreach[U](f: T ⇒ U): Unit = self.foreach(f)
        }
    }

    def toStream(): Stream[T] = toTraversable.toStream

    def copyToArray[B >: T](xs: Array[B], start: Int, len: Int): Unit = {
        val max = xs.length
        var copied = 0
        var rest = this
        while (copied < len && start + copied < max) {
            xs(start + copied) = rest.head
            copied += 1
            rest = rest.tail
        }
    }

}
object ChainedList {

    class ChainedListBuilder[T] extends Builder[T, ChainedList[T]] {
        private var list: ChainedList[T] = ChainedNil
        private var last: :&:[T] = null
        def +=(elem: T): this.type = {
            val newLast = new :&:(elem, ChainedNil)
            if (list.isEmpty) {
                list = newLast
            } else {
                last.rest = newLast
            }
            last = newLast
            this
        }
        def clear(): Unit = list = ChainedNil
        def result(): ChainedList[T] = list
    }

    implicit def canBuildFrom[A]: CanBuildFrom[ChainedList[_], A, ChainedList[A]] = {
        new CanBuildFrom[ChainedList[_], A, ChainedList[A]] {
            def apply(from: ChainedList[_]) = newBuilder
            def apply() = newBuilder
        }
    }
    def newBuilder[T]: ChainedListBuilder[T] = new ChainedListBuilder[T]

    def empty[T]: ChainedList[T] = ChainedNil

    def apply[T](e: T) = new :&:(e, ChainedNil)

    def apply[T](t: Traversable[T]): ChainedList[T] = {
        if (t.isEmpty)
            return ChainedNil;
        val result = new :&:(t.head, ChainedNil)
        var last = result
        t.tail.foreach { e ⇒
            val newLast = new :&:(e, ChainedNil)
            last.rest = newLast
            last = newLast
        }
        result
    }

}

case object ChainedNil extends ChainedList[Nothing] {

    def head: Nothing = throw new IllegalStateException("the list is empty")
    def tail: Nothing = throw new IllegalStateException("the list is empty")
    def isEmpty: Boolean = true
    override def nonEmpty: Boolean = false
    def take(n: Int): ChainedNil.type = {
        if (n == 0) this else throw new IllegalStateException("the list is empty")
    }
    def takeWhile(f: Nothing ⇒ Boolean): ChainedList[Nothing] = this
    def filter(f: Nothing ⇒ Boolean): ChainedList[Nothing] = this
    def drop(n: Int): ChainedNil.type = {
        if (n == 0) this else throw new IllegalStateException("the list is empty")
    }
    def mapConserve[X >: Nothing <: AnyRef](f: Nothing ⇒ X): ChainedList[X] = this

}
case class :&:[@specialized(Int) T](head: T, private[immutable] var rest: ChainedList[T]) extends ChainedList[T] {

    def tail: ChainedList[T] = rest
    def isEmpty: Boolean = false
    override def nonEmpty: Boolean = true

    def take(n: Int): ChainedList[T] = {
        if (n == 0)
            return ChainedNil;
        var taken = 1
        val result = new :&:(head, ChainedNil)
        var last = result
        var rest: ChainedList[T] = this.rest
        while (taken < n) {
            val x = rest.head
            rest = rest.tail
            val newLast = new :&:(x, ChainedNil)
            last.rest = newLast
            last = newLast
            taken += 1
        }
        result
    }

    def takeWhile(f: T ⇒ Boolean): ChainedList[T] = {
        val head = this.head
        if (!f(head))
            return ChainedNil;

        val result = new :&:(head, ChainedNil)
        var last = result
        var rest: ChainedList[T] = this.rest
        while (rest.nonEmpty && f(rest.head)) {
            val x = rest.head
            rest = rest.tail
            val newLast = new :&:(x, ChainedNil)
            last.rest = newLast
            last = newLast
        }
        result
    }

    def filter(f: T ⇒ Boolean): ChainedList[T] = {
        var result: ChainedList[T] = ChainedNil
        var last = result
        var rest: ChainedList[T] = this
        do {
            val x = rest.head
            rest = rest.tail
            if (f(x)) {
                val newLast = new :&:(x, ChainedNil)
                if (last.nonEmpty) {
                    last.asInstanceOf[:&:[T]].rest = newLast
                } else {
                    result = newLast
                }
                last = newLast
            }
        } while (rest.nonEmpty)
        result
    }

    def drop(n: Int): ChainedList[T] = {
        if (n == 0)
            return this;
        var dropped = 1
        var result: ChainedList[T] = this.rest
        while (dropped < n) {
            dropped += 1
            result = result.tail
        }
        result
    }

    def mapConserve[X >: T <: AnyRef](f: T ⇒ X): ChainedList[X] = {
        val head = this.head
        val newHead = f(head)
        var updated = (head.asInstanceOf[AnyRef] ne newHead)
        val result = new :&:(newHead, ChainedNil)
        var last = result
        var rest: ChainedList[T] = this.rest
        while (rest.nonEmpty) {
            val e = rest.head
            val x = f(e)
            updated = updated || (x ne e.asInstanceOf[AnyRef])
            rest = rest.tail
            val newLast = new :&:(x, ChainedNil)
            last.rest = newLast
            last = newLast
        }
        if (updated)
            result
        else
            this
    }

    override def toString: String = s"$head :&: ${rest.toString}"
}
