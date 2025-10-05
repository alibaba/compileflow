package com.alibaba.compileflow.engine.core.infrastructure.utils;

import com.alibaba.compileflow.engine.core.definition.GatewayElement;
import com.alibaba.compileflow.engine.core.definition.Node;

/**
 * @author yusu
 */
public class ProcessUtils {

    public static String buildBranchKey(Node node, Node branchNode) {
        return node instanceof GatewayElement ? node.getId() + "#" + branchNode.getId() : branchNode.getId();
    }

}
