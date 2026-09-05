/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.tbbpm.model;

import com.alibaba.compileflow.engine.core.model.ProcessCallModel;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls a separately defined BPM and maps values across the process boundary.
 *
 * @author yusu
 */
public class BpmCallNode extends FlowNode implements ProcessCallModel {
    private final List<InputMapping> inputMappings = new ArrayList<>(8);
    private final List<OutputMapping> outputMappings = new ArrayList<>(4);
    private String code;
    private String classpath;
    private String version;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getCalledProcessCode() {
        return code;
    }

    @Override
    public String getCalledProcessClasspath() {
        return classpath;
    }

    @Override
    public String getCalledProcessVersion() {
        return version;
    }

    @Override
    public List<InputMapping> getInputMappings() {
        return inputMappings;
    }

    @Override
    public List<OutputMapping> getOutputMappings() {
        return outputMappings;
    }
}
