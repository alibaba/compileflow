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
package com.alibaba.compileflow.workbench.server.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Provides BrowserRouter entry points when the Workbench SPA is bundled with
 * the executable server.
 *
 * @author yusu
 */
@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
public final class WorkbenchWebController {
    /**
     * Forwards supported browser routes to the bundled SPA entry point.
     *
     * @return internal forward to the static index document
     */
    @GetMapping({"/", "/learn", "/learn/**", "/build", "/build/**", "/operate", "/operate/**", "/settings", "/500"})
    public String index() {
        return "forward:/index.html";
    }
}
