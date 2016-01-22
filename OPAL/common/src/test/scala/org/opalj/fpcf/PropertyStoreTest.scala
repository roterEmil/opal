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
package fpcf

import org.junit.runner.RunWith
import scala.collection.JavaConverters._
import scala.collection.mutable
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.opalj.log.GlobalLogContext
import org.scalatest.BeforeAndAfterEach

/**
 * Tests the property store.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class PropertyStoreTest extends FunSpec with Matchers with BeforeAndAfterEach {

    //// TEST FIXTURE

    final val stringEntities: List[String] = List(
        "a", "b", "c",
        "aa", "bb", "cc",
        "ab", "bc", "cd",
        "aaa",
        "aea",
        "aabbcbbaa",
        "aaaffffffaaa", "aaaffffffffffffffffaaa"
    )
    val psStrings: PropertyStore = {
        PropertyStore(stringEntities, () ⇒ false, debug = false)(GlobalLogContext)
    }

    final val PalindromeKey = {
        PropertyKey.create[PalindromeProperty](
            "Palindrome",
            (ps: PropertyStore, e: Entity) ⇒ ???,
            (ps: PropertyStore, epks: Iterable[SomeEPK]) ⇒ ???
        )
    }
    sealed trait PalindromeProperty extends Property {
        type Self = PalindromeProperty
        def key = PalindromeKey
        def isRefineable = false
    }
    // Multiple properties can share the same property instance
    case object Palindrome extends PalindromeProperty
    case object NoPalindrome extends PalindromeProperty

    final val StringLengthKey: PropertyKey[StringLength] = {
        PropertyKey.create(
            "StringLength",
            (ps: PropertyStore, e: Entity) ⇒ ???,
            (ps: PropertyStore, epks: Iterable[SomeEPK]) ⇒ ???
        )
    }
    case class StringLength(length: Int) extends Property {
        type Self = StringLength
        def key = StringLengthKey
        def isRefineable = false
    }

    object EvenNumberOfChars extends SetProperty[String]

    object StringsWithAtLeastTwoChars extends SetProperty[String]

    class Node(val name: String, val targets: mutable.Set[Node] = mutable.Set.empty) {
        override def hashCode: Int = name.hashCode()
        override def equals(other: Any): Boolean = other match {
            case that: Node ⇒ this.name equals that.name
            case _          ⇒ false
        }
    }
    object Node { def apply(name: String) = new Node(name) }
    val nodeA = Node("a")
    val nodeB = Node("b")
    val nodeC = Node("c")
    val nodeD = Node("d")
    val nodeR = Node("R")
    nodeA.targets += nodeB // the graph:
    nodeB.targets += nodeC // a -> b -> c
    nodeB.targets += nodeD //      b -> d
    nodeD.targets += nodeD //           d ⟲
    nodeD.targets += nodeR //           d -> r
    nodeR.targets += nodeB //       ↖︎-----< r
    val nodeEntities = List[Node](nodeA, nodeB, nodeC, nodeD, nodeR)
    val psNodes: PropertyStore = {
        PropertyStore(nodeEntities, () ⇒ false, debug = false)(GlobalLogContext)
    }

    //    final val ReachableNodesKey: PropertyKey[ReachableNodes] = {
    //        PropertyKey.create(
    //                "ReachableNodes", 
    //                (e: Entity) ⇒ ???, 
    //                (ps : PropertyStore, epks: Iterable[SomeEPK]) ⇒ {
    //                    // in case of a cycle we collect all current targets of all members of the 
    //                    // cycle and assign the result to "one" member
    //                    epks.foldLeft(Set.empty[Node]){(c,epk) => 
    //                        ps(epk.e,ReachableNodesKey).get
    //                        c ++ 
    //                        }
    //                    
    //                    Iterable(
    //                            Result()
    //                            )
    //
    //                }
    //                )
    //    }
    //    case class ReachableNodes(nodes: Set[Node]) extends Property {
    //        type Self = ReachableNodes
    //        def key = ReachableNodesKey
    //        def isRefineable = true
    //    }
    //    object NoReachableNodes extends ReachableNodes(Set.empty)

    override def afterEach(): Unit = {
        psStrings.reset()
        psNodes.reset()
    }

    //// TESTS

    describe("the property store") {

        it("should be in the deault state after calling reset") {
            val ps = psStrings

            // let's fill the property store with:
            //  - an entity based property and 
            //  - a set property
            //  - an on property derivation function
            ps.onPropertyChange(PalindromeKey) { (e, p) ⇒
                if (p == Palindrome && e.toString().size % 2 == 0)
                    ps.add(EvenNumberOfChars)(e.toString())
            }
            ps << { e: Entity ⇒
                val property = if (e.toString.reverse == e.toString) Palindrome else NoPalindrome
                ImmediateResult(e, property)
            }
            ps.onPropertyDerivation(EvenNumberOfChars)((e) ⇒ {})
            ps.waitOnPropertyComputationCompletion(true)

            // let's test the reset method
            ps.entities(EvenNumberOfChars) should not be ('isEmpty)
            ps.entities { x ⇒ true } should not be ('isEmpty)
            ps.reset()
            ps.entities(EvenNumberOfChars) should be('isEmpty)
            ps.entities { x ⇒ true } should be('isEmpty)

        }
    }

    describe("set properties") {

        it("an onPropertyDerivation function should be called if entities are associated with the property after the registration of the function") {
            val ps = psStrings
            val results = new java.util.concurrent.ConcurrentLinkedQueue[String]()

            ps.onPropertyDerivation(EvenNumberOfChars)(results.add(_))

            for (e ← stringEntities if (e.size % 2) == 1) { ps.add(EvenNumberOfChars)(e) }
            ps.waitOnPropertyComputationCompletion(true)

            val expected = Set("aabbcbbaa", "a", "b", "c", "aaa", "aea")
            ps.entities(EvenNumberOfChars).asScala should be(expected)
            results.asScala.toSet should be(expected)
        }

        it("an onPropertyDerivation function should be called for all entities that already have a respective property when the function is registered") {
            val ps = psStrings
            val results = new java.util.concurrent.ConcurrentLinkedQueue[String]()

            for (e ← stringEntities if (e.size % 2) == 1) { ps.add(EvenNumberOfChars)(e) }

            ps.onPropertyDerivation(EvenNumberOfChars)(results.add(_))

            ps.waitOnPropertyComputationCompletion(true)

            val expected = Set("aabbcbbaa", "a", "b", "c", "aaa", "aea")
            ps.entities(EvenNumberOfChars).asScala should be(expected)
            results.asScala.toSet should be(expected)
        }

        it("an onPropertyDerivation function should be called for all entities that already have a respective property and also for those that are associated with it afterwards") {
            val ps = psStrings
            val results = new java.util.concurrent.ConcurrentLinkedQueue[String]()

            for (e ← stringEntities if e.size == 1) { ps.add(EvenNumberOfChars)(e) }
            ps.onPropertyDerivation(EvenNumberOfChars)(results.add(_))
            for (e ← stringEntities if e.size % 2 == 1 && e.size != 1) { ps.add(EvenNumberOfChars)(e) }

            ps.waitOnPropertyComputationCompletion(true)

            val expected = Set("aabbcbbaa", "a", "b", "c", "aaa", "aea")
            ps.entities(EvenNumberOfChars).asScala should be(expected)
            results.asScala.toSet should be(expected)
        }

        it("deriving the same property multiple times should have no effect") {
            val ps = psStrings
            val results = new java.util.concurrent.ConcurrentLinkedQueue[String]()

            for (e ← stringEntities if (e.size % 2) == 1) { ps.add(EvenNumberOfChars)(e) }
            for (e ← stringEntities if (e.size % 2) == 1) { ps.add(EvenNumberOfChars)(e) }

            ps.onPropertyDerivation(EvenNumberOfChars)(results.add(_))

            ps.waitOnPropertyComputationCompletion(true)

            val expected = Set("aabbcbbaa", "a", "b", "c", "aaa", "aea")
            ps.entities(EvenNumberOfChars).asScala should be(expected)
            results.asScala.toSet should be(expected)
        }

        it("should be possible to implement properties that are calculated when the base information becomes available") {
            val ps = psStrings

            // In this scenario we only associate the palindrome property with elements
            // that contain at least two chars

            ps.onPropertyDerivation(StringsWithAtLeastTwoChars) { (s: String) ⇒
                ps.update(s, PalindromeKey) { (p: Option[PalindromeProperty]) ⇒
                    assert(p == None)
                    if (s.reverse == s)
                        Some(Palindrome)
                    else
                        Some(NoPalindrome)
                }
            }

            // "run the initial analysis"
            for (e ← stringEntities if (e.size > 1)) { ps.add(StringsWithAtLeastTwoChars)(e) }

            ps.waitOnPropertyComputationCompletion(true)

            ps.entities { p ⇒ p == NoPalindrome } should be(Set("ab", "bc", "cd"))
            ps.entities { p ⇒ p == Palindrome } should be(
                Set("aa", "bb", "cc", "aaa", "aea", "aabbcbbaa",
                    "aaaffffffaaa", "aaaffffffffffffffffaaa")
            )
        }

        it("should be possible to chain property computations") {
            val ps = psStrings

            // In this scenario we only associate the palindrome property with elements
            // that contain at least two chars
            ps.onPropertyDerivation(StringsWithAtLeastTwoChars) { (s: String) ⇒
                if (s.size % 2 == 0) ps.add(EvenNumberOfChars)(s)
            }
            ps.onPropertyDerivation(EvenNumberOfChars) { (s: String) ⇒
                ps.update(s, PalindromeKey) { (p: Option[PalindromeProperty]) ⇒
                    assert(p == None)
                    if (s.reverse == s)
                        Some(Palindrome)
                    else
                        Some(NoPalindrome)
                }
            }

            // "run the initial analysis"
            for (e ← stringEntities if (e.size > 1)) { ps.add(StringsWithAtLeastTwoChars)(e) }

            ps.waitOnPropertyComputationCompletion(true)

            ps.entities { p ⇒ p == NoPalindrome } should be(Set("ab", "bc", "cd"))
            ps.entities { p ⇒ p == Palindrome } should be(
                Set("aa", "bb", "cc", "aaaffffffaaa", "aaaffffffffffffffffaaa")
            )
        }
    }

    describe("per entity properties") {

        describe("properties") {

            it("every element can have an individual property instance") {
                val ps = psStrings

                ps << { e: Entity ⇒ ImmediateResult(e, StringLength(e.toString.length())) }

                ps.waitOnPropertyComputationCompletion(true)

                stringEntities.foreach { e ⇒ ps(e, StringLengthKey).get.length should be(e.length()) }
            }
        }

        describe("computations depending on a group of entities") {
            it("should be executed for each group in parallel") {
                import scala.collection.mutable

                val ps = psStrings

                ps.execute({ case s: String ⇒ s }, { (s: String) ⇒ s.length }) { (k, es) ⇒
                    es.map(e ⇒ EP(e, StringLength(k)))
                }

                ps.waitOnPropertyComputationCompletion(true)

                stringEntities.foreach { e ⇒ ps(e, StringLengthKey).get.length should be(e.length()) }
            }
        }

        describe("computations depending on a specific property") {

            it("should be triggered for every entity that already has the respective property") {
                import scala.collection.mutable
                val ps = psStrings
                val results = mutable.Map.empty[Property, mutable.Set[String]]

                ps << { e: Entity ⇒
                    if (e.toString.reverse == e.toString())
                        ImmediateResult(e, Palindrome)
                    else
                        ImmediateResult(e, NoPalindrome)
                }

                ps.onPropertyChange(PalindromeKey)((e, p) ⇒ results.synchronized {
                    results.getOrElseUpdate(p, mutable.Set.empty[String]).add(e.toString())
                })

                ps.waitOnPropertyComputationCompletion(true)

                val expectedResult = Set(
                    "aabbcbbaa", "aa",
                    "c", "aea", "aaa",
                    "aaaffffffaaa",
                    "aaaffffffffffffffffaaa",
                    "cc", "a", "bb", "b"
                )
                results(Palindrome) should be(expectedResult)
            }

            it("should be triggered for every entity that is associated with the respective property after registering the onPropertyChange function") {
                import scala.collection.mutable
                val ps = psStrings
                val results = mutable.Map.empty[Property, mutable.Set[String]]

                ps.onPropertyChange(PalindromeKey)((e, p) ⇒ results.synchronized {
                    results.getOrElseUpdate(p, mutable.Set.empty).add(e.toString())
                })

                ps << { e: Entity ⇒
                    if (e.toString.reverse == e.toString())
                        ImmediateResult(e, Palindrome)
                    else
                        ImmediateResult(e, NoPalindrome)
                }

                ps.waitOnPropertyComputationCompletion(true)

                val expectedResult = Set(
                    "aabbcbbaa", "aa", "c", "aea",
                    "aaa", "aaaffffffaaa",
                    "aaaffffffffffffffffaaa", "cc", "a", "bb", "b"
                )
                results(Palindrome) should be(expectedResult)
            }

            it("should be triggered for externally computed and then added properties") {
                import scala.collection.mutable
                val ps = psStrings
                val results = mutable.Map.empty[Property, mutable.Set[String]]

                ps.onPropertyChange(PalindromeKey)((e, p) ⇒ results.synchronized {
                    results.getOrElseUpdate(p, mutable.Set.empty).add(e.toString())
                })

                val palindromeProperties = stringEntities.map { e ⇒
                    EP(e, if (e == e.reverse) Palindrome else NoPalindrome)
                }
                ps.set(palindromeProperties) // <= externally computed

                ps.waitOnPropertyComputationCompletion(true)

                val expectedPalindromes = Set(
                    "aabbcbbaa", "aa", "c", "aea",
                    "aaa", "aaaffffffaaa",
                    "aaaffffffffffffffffaaa", "cc", "a", "bb", "b"
                )
                results(Palindrome) should be(expectedPalindromes)
                results(NoPalindrome) should be(Set("ab", "bc", "cd"))
            }

            /*
            //                     it("should be triggered whenever the property is updated") {
            //                        import scala.collection.mutable
            //                        val results = mutable.Map.empty[Entity, ReachableNodes]
            //                        val ps = psNodes()
            //                        
            //                        ps.onPropertyChange(ReachableNodesKey)((e, p) ⇒ results.synchronized {
            //                            results += ((e, p))
            //                        })
            //            
            //                        def analyze(source : Node, target : Node) : Set[Node] = {
            //                            ps(source,ReachableNodesKey) match {
            //                                case Some(ReachableNodes(_,targets)) => targets
            //                                case None => Set.empty
            //                            }
            //                        }
            //                        
            //                        ps << { e: Entity ⇒
            //                            val s @ Node(_/*name*/, targets) = e
            //                            if(targets.isEmpty) {
            //                                ImmediateResult(e,NoReachableNodes);
            //                            } else {
            //                                IntermediateResult( // It is (just an intermediate result!)
            //                                        s,
            //                                        ReachableNodes(
            //                                        targets.foldLeft(Set.empty[Node]){(c,t) => c ++ analyze(s,t) }
            //                                        )
            //                                )
            //                            }
            //                        }
            //            
            //                        ps.waitOnPropertyComputationCompletion(true)
            //                    }
             */

        }

    }

}

