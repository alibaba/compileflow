package com.alibaba.compileflow.engine.core.version;

import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;

/**
 * @author yusu
 */
@ExtensionRealization(priority = 0)
public class DefaultVersionSelector implements VersionSelector {

    @Override
    public String selectVersion(VersionSelectContext ctx) {
        return null;
    }

}
