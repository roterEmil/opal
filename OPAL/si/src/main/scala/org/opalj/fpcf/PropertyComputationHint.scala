/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf

/**
 * Hints about the nature of the property computations, which can/are used by the property store
 * to implement different scheduling schemes.
 */
sealed trait PropertyComputationHint

/**
 * A standard property computation.
 */
final case object DefaultPropertyComputation extends PropertyComputationHint

/**
 * The property computation is extremely cheap. Therefore, the computation can/should be
 * processed in the current thread, it is extremely unlikely that we will gain anything
 * from parallelization.
 */
final case object CheapPropertyComputation extends PropertyComputationHint
