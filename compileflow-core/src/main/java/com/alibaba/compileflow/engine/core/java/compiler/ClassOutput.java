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
package com.alibaba.compileflow.engine.core.java.compiler;

/**
 * Receives bytecode emitted by a Java compiler.
 *
 * <p>A compiler may emit the primary class, nested classes, and synthetic classes through the same output.</p>
 *
 * @author yusu
 */
@FunctionalInterface
public interface ClassOutput {
    /**
     * Writes one compiled class.
     *
     * @param className binary name of the compiled class
     * @param bytes     valid JVM class-file bytes
     * @throws Exception when the output cannot accept the class
     */
    void writeClass(String className, byte[] bytes) throws Exception;
}
