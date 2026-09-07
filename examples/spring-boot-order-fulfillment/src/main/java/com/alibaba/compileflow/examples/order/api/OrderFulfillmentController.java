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
package com.alibaba.compileflow.examples.order.api;

import com.alibaba.compileflow.examples.order.workflow.OrderFulfillmentWorkflow;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP adapter that keeps transport concerns outside the process definition.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/orders")
public class OrderFulfillmentController {
    private final OrderFulfillmentWorkflow workflow;

    public OrderFulfillmentController(OrderFulfillmentWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/fulfill")
    public ResponseEntity<OrderResponse> fulfill(@RequestBody OrderRequest request,
            @RequestHeader(value = "X-Invocation-Id", required = false) String invocationId) {
        if (invocationId != null && !invocationId.isBlank()) {
            try {
                ProcessIdentifiers.requireInvocationId(invocationId);
            } catch (IllegalArgumentException failure) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-Invocation-Id", failure);
            }
        }
        return ResponseEntity.ok(workflow.fulfill(request, invocationId));
    }
}
