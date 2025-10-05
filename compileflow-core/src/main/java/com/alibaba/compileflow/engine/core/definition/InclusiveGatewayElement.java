package com.alibaba.compileflow.engine.core.definition;

/**
 * Represents an inclusive (OR) gateway, a decision point in a process where one or
 * more paths can be taken.
 * <p>
 * An inclusive gateway evaluates all of its outgoing transitions. For every transition
 * whose condition evaluates to {@code true}, a parallel path of execution is created.
 * If no conditions evaluate to true, and there is a default outgoing transition, that
 * transition will be taken.
 *
 * @author yusu
 */
public interface InclusiveGatewayElement<T extends Transition> extends TransitionNode<T> {

}
