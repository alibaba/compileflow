package com.alibaba.compileflow.engine;

/**
 * A marker interface for all parsed process model representations within the engine.
 * <p>
 * This interface serves as a common supertype for different process definition models,
 * such as BPMN or TBBPM. It allows the {@link ProcessEngine} and its related services
 * to handle different process dialects in a generic yet type-safe manner.
 * <p>
 * Implementations of this interface are typically Plain Old Java Objects (POJOs) that
 * represent the in-memory structure of a parsed process definition file.
 *
 * @author yusu
 */
public interface FlowModel {
}
