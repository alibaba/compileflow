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
package com.alibaba.compileflow.workbench.server.api.problem;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ApiProblemHttpContractTest {
    @Autowired
    private WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void rendersControllerProblemWithThePublicHttpContract() throws Exception {
        mockMvc
            .perform(get("/api/monitoring/top-processes").param("limit", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:compileflow:problem:invalid-request"))
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("limit must be between 1 and 500"))
            .andExpect(jsonPath("$.instance").value("/api/monitoring/top-processes"))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rendersBeanValidationFailureWithThePublicHttpContract() throws Exception {
        mockMvc
            .perform(post("/api/executions/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                    {
                      "code": "draft.payment",
                      "modelType": "BPMN",
                      "xml": " "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:compileflow:problem:invalid-request"))
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("The request body failed validation"))
            .andExpect(jsonPath("$.instance").value("/api/executions/preview"))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsCallerSelectedWorkbenchNamespace() throws Exception {
        mockMvc
            .perform(post("/api/processes/payment.approve/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                    {
                      "routing": {
                        "namespace": "tenant-a",
                        "alias": "production"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Malformed request body"))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }
}
