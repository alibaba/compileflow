# CompileFlow Quick Start - Full Example Walkthrough

This guide provides a detailed walkthrough for building a complete Spring Boot application to execute a KTV billing process.
It expands on the Quick Start section in the main `README.md`.

> 💡 **Goal**: This document aims to solidify your understanding of the core concepts (`ProcessEngine`, `ProcessSource`,
`ProcessResult`) through a concrete, runnable example that includes decision logic.

## 1. Project Setup

### a. Dependency

First, ensure your `pom.xml` includes the `compileflow-spring-boot-starter`.

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### b. Spring Boot Application Class

This is a standard entry point for a Spring Boot application.

```java
package com.example.ktv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KtvApplication {
    public static void main(String[] args) {
        SpringApplication.run(KtvApplication.class, args);
    }
}
```

## 2. Process Definition (`ProcessSource`)

We will define our process in `src/main/resources/bpm/ktv/quickstart.bpm`. The `ProcessEngine` loads process files from the
`classpath` by default.

The unique identifier for this process is its `code` attribute: `"bpm.ktv.quickstart"`. We will use this `code` to
create our `ProcessSource`.

This example includes a **decision node** to apply different pricing based on group size.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  code="bpm.ktv.quickstart": This is the unique ID of the process.
  We use this to create a ProcessSource: ProcessSource.fromCode("bpm.ktv.quickstart")
-->
<bpm code="bpm.ktv.quickstart" name="KTV Billing Process (Quickstart)" type="process">
    <!-- Define process variables -->
    <var name="price" dataType="java.lang.Integer" inOutType="return"/>
    <var name="pList" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>

    <!-- The process starts here -->
    <start id="start" name="Start">
        <transition to="checkGroupSize"/>
    </start>

    <!--
      This is a decision node. It directs the flow based on data.
    -->
    <decision id="checkGroupSize" name="Large Group?">
        <!--
          If the expression "pList.size() > 3" is true, move to the "calculateDiscountedPrice" node.
        -->
        <transition to="calculateDiscountedPrice" name="Yes (&gt;3 people)" expression="pList.size() &gt; 3"/>
        <!--
          Otherwise (default path), move to the "calculateStandardPrice" node.
        -->
        <transition to="calculateStandardPrice" name="No"/>
    </decision>

    <!--
      This script task calculates the standard price.
    -->
    <scriptTask id="calculateStandardPrice" name="Calculate Standard Price">
        <transition to="end"/>
        <action type="ql">
            <!-- Expression: pList.size() * 30 -->
            <actionHandle expression="pList.size() * 30">
                <var name="price" dataType="java.lang.Integer" contextVarName="price" inOutType="return"/>
            </actionHandle>
        </action>
    </scriptTask>

    <!--
      This script task calculates a discounted price for larger groups.
    -->
    <scriptTask id="calculateDiscountedPrice" name="Calculate Discounted Price">
        <transition to="end"/>
        <action type="ql">
            <!-- Apply a group discount: 25 per person -->
            <actionHandle expression="pList.size() * 25">
                <var name="price" dataType="java.lang.Integer" contextVarName="price" inOutType="return"/>
            </actionHandle>
        </action>
    </scriptTask>

    <!-- The process ends here -->
    <end id="end" name="End"/>
</bpm>
```

## 3. Business Logic (`ProcessEngine` & `ProcessResult`)

Now, let's create a service to call the process engine.

- **`@Autowired ProcessEngine`**: The Spring Boot Starter auto-configures a global, singleton `ProcessEngine` for us to
  inject.
- **Type-Safe DTOs**: We define static inner classes for our input (`KtvRequest`) and output (`KtvResponse`). This makes
  the API call cleaner and safer.
- **`processEngine.execute(...)`**: This is the core method to execute the process.
- **`result.orElseThrow(...)`**: `ProcessResult` provides convenient, functional-style methods for handling success or
  failure.

```java
package com.example.ktv.service;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class KtvBillingService {

    @Autowired
    private ProcessEngine<TbbpmModel> processEngine;

    // Define an input DTO for type-safe interaction
    public static class KtvRequest {
        // The field name "pList" must match the <var name="pList"> in the process definition
        public List<String> pList;
    }

    // Define an output DTO for type-safe interaction
    public static class KtvResponse {
        // The field name "price" must match the <var name="price"> in the process definition
        public Integer price;
    }

    /**
     * Executes the KTV billing process.
     */
    public KtvResponse calculatePrice(List<String> customers) {
        KtvRequest request = new KtvRequest();
        request.pList = customers;

        // 1. Create the ProcessSource from its unique code
        ProcessSource source = ProcessSource.fromCode("bpm.ktv.quickstart");

        // 2. Execute the process, passing the request DTO and specifying the response DTO type
        ProcessResult<KtvResponse> result = processEngine.execute(
            source,
            request,
            KtvResponse.class
        );

        // 3. Check the result. If it failed, throw an exception; otherwise, return the data.
        if (result.isSuccess()) {
            return result.getData();
        } else {
            throw new RuntimeException("KTV billing process execution failed: " + result.getErrorMessage());
        }
    }
}
```

## 4. Expose an API (Optional)

We can create a simple controller for easy testing.

```java
package com.example.ktv.controller;

import com.example.ktv.service.KtvBillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class KtvController {

    @Autowired
    private KtvBillingService ktvBillingService;

    @PostMapping("/calculate")
    public KtvBillingService.KtvResponse calculate(@RequestBody List<String> customers) {
        return ktvBillingService.calculatePrice(customers);
    }
}
```

### Testing

After starting the application, you can send POST requests to `/calculate`.

#### Test Case 1: Standard Price

`POST http://localhost:8080/calculate`

**Body (raw, JSON):**
A group of 3 people should trigger the standard price logic (`3 > 3` is false).

```json
["customer1", "customer2", "customer3"]
```

**Response:**
The expected price is `3 * 30 = 90`.

```json
{
  "price": 90
}
```

#### Test Case 2: Discounted Price

`POST http://localhost:8080/calculate`

**Body (raw, JSON):**
A group of 4 people should trigger the discount logic (`4 > 3` is true).

```json
["customer1", "customer2", "customer3", "customer4"]
```

**Response:**
The expected price is `4 * 25 = 100`.

```json
{
  "price": 100
}
```

## Next Steps

You now have a deep understanding of how to use CompileFlow end-to-end, including decision logic. We recommend exploring
these topics next:

- **[API Reference](api-reference.md)**: Dive deeper into all available methods for `ProcessEngine`, `ProcessSource`,
  etc.
- **[Advanced Features](advanced-features.md)**: Learn about production-grade features like engine warm-up, hot
  deployment, and monitoring.
