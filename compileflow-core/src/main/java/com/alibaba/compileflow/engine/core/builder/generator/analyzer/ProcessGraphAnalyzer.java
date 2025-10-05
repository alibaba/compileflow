package com.alibaba.compileflow.engine.core.builder.generator.analyzer;

import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * An extension point for analyzing the structure of a process definition and
 * converting it into a {@link ProcessGraph}.
 * <p>
 * Implementations of this interface are responsible for traversing the nodes
 * and transitions of a process to build a graph representation that is optimized
 * for code generation. This typically involves identifying control flow structures
 * like branches and merges.
 *
 * @author yusu
 * @see ProcessGraph
 * @see com.alibaba.compileflow.engine.core.builder.generator.AbstractProcessCodeGenerator
 */
public interface ProcessGraphAnalyzer extends Extension {

    String EXT_BUILD_PROCESS_GRAPH = "com.alibaba.compileflow.engine.core.builder.generator.analyzer.ProcessGraphAnalyzer.buildProcessGraph";

    /**
     * Analyzes the given node container and builds a structured {@link ProcessGraph}.
     *
     * @param nodeContainer The container of nodes to be analyzed (e.g., a {@code FlowModel}).
     * @return A {@link ProcessGraph} representing the control flow of the process.
     */
    @ExtensionPoint(code = EXT_BUILD_PROCESS_GRAPH)
    ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer);

}
