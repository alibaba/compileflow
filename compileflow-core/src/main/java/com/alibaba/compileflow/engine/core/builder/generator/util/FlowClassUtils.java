package com.alibaba.compileflow.engine.core.builder.generator.util;

import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for generating fully qualified class names for dynamic flow classes.
 *
 * @author yusu
 */
public final class FlowClassUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FlowClassUtils() {
    }

    /**
     * Generates the full class name for a flow's implementation class.
     *
     * @param code The unique code of the process.
     * @return The fully qualified class name.
     */
    public static String getFlowClassFullName(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Process code cannot be null.");
        }
        int lastDotIndex = code.lastIndexOf('.');
        String basePackage = (lastDotIndex > 0) ? code.substring(0, lastDotIndex) : "";
        String simpleName = (lastDotIndex > 0) ? code.substring(lastDotIndex + 1) : code;
        String className = getFlowClassName(simpleName);

        String safePackage = StringUtils.isEmpty(basePackage)
                ? ""
                : JavaIdentifierUtils.toPackageName(basePackage);
        String qualifiedName = StringUtils.isEmpty(safePackage) ? className : safePackage + "." + className;
        // Prepend a fixed package to avoid conflicts with user's own classes.
        return "compileflow." + qualifiedName;
    }

    /**
     * Generates a valid Java class name from a process code and id.
     */
    private static String getFlowClassName(String code) {
        String flowClassName = JavaIdentifierUtils.toClassName(code);
        if (!flowClassName.endsWith("Flow")) {
            flowClassName = flowClassName + "Flow";
        }
        return flowClassName;
    }

}
