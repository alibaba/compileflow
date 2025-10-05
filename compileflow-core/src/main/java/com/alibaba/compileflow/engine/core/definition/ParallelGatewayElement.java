package com.alibaba.compileflow.engine.core.definition;

/**
 * Represents a parallel (AND) gateway, used to model concurrent execution in a process.
 * <p>
 * A parallel gateway has two primary functions based on its position in the flow:
 * <ul>
 *   <li><b>Forking:</b> When a parallel gateway has multiple outgoing transitions, it creates
 *       a concurrent path of execution for each one. The conditions on these transitions
 *       are ignored.</li>
 *   <li><b>Joining:</b> When a parallel gateway has multiple incoming transitions, it waits
 *       for all of them to complete before allowing the process to continue through its
 *       single outgoing transition.</li>
 * </ul>
 *
 * @author yusu
 */
public interface ParallelGatewayElement<T extends Transition> extends TransitionNode<T> {
}
