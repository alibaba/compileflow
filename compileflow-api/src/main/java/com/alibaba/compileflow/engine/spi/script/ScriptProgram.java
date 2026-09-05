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
package com.alibaba.compileflow.engine.spi.script;

/**
 * Runtime-local executable representation of one script.
 * <p>
 * Implementations may hold an AST, compiled form or cache handle. A script program
 * is deliberately opaque, disposable and non-serializable; process recovery must
 * always be possible from the semantic language name and exact source alone.
 * Providers that share one program across invocations must support concurrent evaluation or
 * isolate evaluation themselves.
 *
 * @author yusu
 */
public interface ScriptProgram {
    /**
     * Returns the canonical semantic language implemented by this program.
     *
     * @return canonical language name
     */
    String language();
}
