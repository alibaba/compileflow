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
package com.alibaba.compileflow.deploy.protocol;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessArtifactImmutabilityTest {
    @Test
    void snapshotsAndProtectsCallBindings() {
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "demo.flow", "<flow/>");
        ProcessRef.Version ref = ProcessRef.version("default", "demo.flow", "v1");
        List<ProcessCallBinding> bindings = new ArrayList<>();
        bindings.add(new ProcessCallBinding("child", ProcessRef.version("default", "child.flow", "v1")));
        ProcessArtifact artifact = new ProcessArtifact(ref, definition,
                ProcessArtifactDigest.compute(definition,
                        Map.of("child", ProcessRef.version("default", "child.flow", "v1"))), bindings);
        bindings.clear();

        assertThat(artifact.getCallBindings()).containsOnlyKeys("child");
        assertThatThrownBy(() -> artifact.getCallBindings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void exposesNoPublicSetters() {
        assertThat(Arrays
            .stream(ProcessArtifact.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName)
            .filter(name -> name.startsWith("set")))
            .isEmpty();
        assertThat(Modifier.isFinal(ProcessArtifact.class.getModifiers())).isTrue();
    }
}
