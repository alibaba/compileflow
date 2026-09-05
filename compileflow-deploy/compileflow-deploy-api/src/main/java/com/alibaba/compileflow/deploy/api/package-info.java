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
/**
 * Public control-plane facade for immutable process publication and rollout management.
 *
 * <p>The API records desired deployment state. Publication does not compile or install a runtime
 * on the calling node.
 *
 * @author yusu
 */
package com.alibaba.compileflow.deploy.api;
