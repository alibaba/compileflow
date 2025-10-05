# CompileFlow 快速开始 - 完整示例演练

本指南提供了 `README.md` 中快速开始部分的详细代码演练。我们将构建一个完整的 Spring Boot 应用来执行一个包含决策逻辑的 KTV 计费流程。

> 💡 **目标**: 本文档旨在通过一个具体的、可运行的例子来加深您对核心概念（`ProcessEngine`, `ProcessSource`, `ProcessResult`）的理解。

## 1. 项目设置

### a. 依赖

首先，确保你的 `pom.xml` 中包含了 `compileflow-spring-boot-starter`。

```xml

<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### b. Spring Boot 启动类

这是一个标准的Spring Boot应用入口。

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

## 2. 流程定义 (`ProcessSource`)

我们将在 `src/main/resources/bpm/ktv/quickstart.bpm` 路径下定义流程。`ProcessEngine` 默认会从 `classpath` 加载流程文件。

此流程的唯一标识是 `code` 属性: `"bpm.ktv.quickstart"`。我们将使用这个 `code` 来创建 `ProcessSource`。

这个例子包含一个**决策节点**，用于根据顾客人数应用不同的定价策略。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  code="bpm.ktv.quickstart": 这是流程的唯一ID。
  我们会用它来创建 ProcessSource: ProcessSource.fromCode("bpm.ktv.quickstart")
-->
<bpm code="bpm.ktv.quickstart" name="KTV 计费流程 (快速开始)" type="process">
    <!-- 定义流程变量 -->
    <var name="price" dataType="java.lang.Integer" inOutType="return"/>
    <var name="pList" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>

    <!-- 流程从这里开始 -->
    <start id="start" name="开始">
        <transition to="checkGroupSize"/>
    </start>

    <!--
      这是一个决策节点，它会根据数据来决定流程的走向。
    -->
    <decision id="checkGroupSize" name="是否为大团体?">
        <!--
          如果表达式 "pList.size() > 3" 为 true，流程将转向 "calculateDiscountedPrice" 节点。
        -->
        <transition to="calculateDiscountedPrice" name="是 (&gt;3 人)" expression="pList.size() &gt; 3"/>
        <!--
          否则（默认路径），流程将转向 "calculateStandardPrice" 节点。
        -->
        <transition to="calculateStandardPrice" name="否"/>
    </decision>

    <!--
      这个脚本任务节点用于计算标准价格。
    -->
    <scriptTask id="calculateStandardPrice" name="计算标准价格">
        <transition to="end"/>
        <action type="ql">
            <!-- 表达式: pList.size() * 30 -->
            <actionHandle expression="pList.size() * 30">
                <var name="price" dataType="java.lang.Integer" contextVarName="price" inOutType="return"/>
            </actionHandle>
        </action>
    </scriptTask>

    <!--
      这个脚本任务节点用于为大团体计算折扣价。
    -->
    <scriptTask id="calculateDiscountedPrice" name="计算折扣价格">
        <transition to="end"/>
        <action type="ql">
            <!-- 为大团体应用折扣：每人 25 -->
            <actionHandle expression="pList.size() * 25">
                <var name="price" dataType="java.lang.Integer" contextVarName="price" inOutType="return"/>
            </actionHandle>
        </action>
    </scriptTask>

    <!-- 流程在这里结束 -->
    <end id="end" name="结束"/>
</bpm>
```

## 3. 业务逻辑 (`ProcessEngine` & `ProcessResult`)

现在我们创建一个服务来调用流程引擎。

- **`@Autowired ProcessEngine`**: Spring Boot Starter 会自动配置一个全局单例的 `ProcessEngine`，我们直接注入即可。
- **类型安全的DTO**: 我们为输入 (`KtvRequest`) 和输出 (`KtvResponse`) 定义了静态内部类。这使得API调用更加清晰和安全。
- **`processEngine.execute(...)`**: 这是执行流程的核心方法。
- **`result.orElseThrow(...)`**: `ProcessResult` 提供了方便的函数式方法来处理成功或失败的情况。

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

    // 为类型安全的交互定义输入DTO
    public static class KtvRequest {
        // 字段名 "pList" 必须与流程定义中的 <var name="pList"> 匹配
        public List<String> pList;
    }

    // 为类型安全的交互定义输出DTO
    public static class KtvResponse {
        // 字段名 "price" 必须与流程定义中的 <var name="price"> 匹配
        public Integer price;
    }

    /**
     * 执行KTV计费流程
     */
    public KtvResponse calculatePrice(List<String> customers) {
        KtvRequest request = new KtvRequest();
        request.pList = customers;

        // 1. 通过唯一编码创建流程源
        ProcessSource source = ProcessSource.fromCode("bpm.ktv.quickstart");

        // 2. 执行流程，传入请求DTO并指定响应DTO类型
        ProcessResult<KtvResponse> result = processEngine.execute(
            source,
            request,
            KtvResponse.class
        );

        // 3. 检查结果。如果失败，抛出异常；如果成功，返回数据。
        if (result.isSuccess()) {
            return result.getData();
        } else {
            throw new RuntimeException("KTV计费流程执行失败: " + result.getErrorMessage());
        }
    }
}
```

## 4. 暴露API (可选)

为了方便测试，我们可以创建一个简单的Controller。

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

### 测试

启动应用后，你可以向 `/calculate` 发送 POST 请求。

#### 测试用例 1: 标准价格

`POST http://localhost:8080/calculate`

**Body (raw, JSON):**
一个 3 人的团队会触发标准价格逻辑 (`3 > 3` 为 false)。

```json
["customer1", "customer2", "customer3"]
```

**响应:**
预期价格为 `3 * 30 = 90`。

```json
{
    "price": 90
}
```

#### 测试用例 2: 折扣价格

`POST http://localhost:8080/calculate`

**Body (raw, JSON):**
一个 4 人的团队会触发折扣逻辑 (`4 > 3` 为 true)。

```json
["customer1", "customer2", "customer3", "customer4"]
```

**响应:**
预期价格为 `4 * 25 = 100`。

```json
{
    "price": 100
}
```

## 下一步

现在您已经对如何端到端地使用CompileFlow（包括决策逻辑）有了深入的了解。建议您接下来浏览：

- **[API 参考](api-reference.md)**: 深入了解 `ProcessEngine`、`ProcessSource` 等的所有可用方法。
- **[高级特性](advanced-features.md)**: 学习引擎预热、热部署和监控等生产级特性。
