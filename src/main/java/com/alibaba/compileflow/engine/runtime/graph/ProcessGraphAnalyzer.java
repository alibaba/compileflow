package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.extension.Extension;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;

/**
 * @author yusu
 */
public interface ProcessGraphAnalyzer extends Extension {

    String EXT_BUILD_PROCESS_GRAPH = "com.alibaba.compileflow.engine.runtime.graph.ProcessGraphAnalyzer.buildProcessGraph";

    @ExtensionPoint(code = EXT_BUILD_PROCESS_GRAPH, reducePolicy = ReducePolicy.FISRT_MATCH)
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer);

}
