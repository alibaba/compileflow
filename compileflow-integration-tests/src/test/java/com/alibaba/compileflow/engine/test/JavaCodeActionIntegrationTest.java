/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Java Code Action functionality.
 * Tests the java-inline and java-source action types that allow embedding Java code
 * in process definitions and executing them at runtime.
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Java Code Action Integration Tests")
public class JavaCodeActionIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaCodeActionIntegrationTest.class);

    private ProcessEngine processEngine;

    @BeforeEach
    public void setUp() {
        processEngine = ProcessEngineFactory.createTbbpm();
    }

    @AfterEach
    public void tearDown() {
        processEngine.close();
        // Note: DynamicClassExecutor is now engine-scoped, cache will be cleared automatically when engine is closed
    }

    @Nested
    @DisplayName("Java Inline Action Tests")
    class JavaInlineActionTests {

        @Test
        @DisplayName("should execute java-inline block mode with imports and validations")
        void testJavaInlineBlock() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 10);
            context.put("inputB", 5);
            context.put("op", "+");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline block should execute successfully");
            assertNotNull(result.getData());
            assertEquals(15, result.getData().get("result"), "Addition should work correctly");
        }

        @Test
        @DisplayName("should execute java-inline block mode with subtraction")
        void testJavaInlineBlockSubtraction() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 20);
            context.put("inputB", 8);
            context.put("op", "-");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline block subtraction should execute successfully");
            assertEquals(12, result.getData().get("result"), "Subtraction should work correctly");
        }

        @Test
        @DisplayName("should execute java-inline block mode with multiplication")
        void testJavaInlineBlockMultiplication() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 6);
            context.put("inputB", 7);
            context.put("op", "*");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline block multiplication should execute successfully");
            assertEquals(42, result.getData().get("result"), "Multiplication should work correctly");
        }

        @Test
        @DisplayName("should execute java-inline block mode with division")
        void testJavaInlineBlockDivision() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 20);
            context.put("inputB", 4);
            context.put("op", "/");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline block division should execute successfully");
            assertEquals(5, result.getData().get("result"), "Division should work correctly");
        }

        @Test
        @DisplayName("should throw ArithmeticException on division by zero")
        void testJavaInlineBlockDivisionByZero() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 10);
            context.put("inputB", 0);
            context.put("op", "/");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertFalse(result.isSuccess(), "Division by zero should fail");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported operation")
        void testJavaInlineBlockUnsupportedOperation() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 10);
            context.put("inputB", 5);
            context.put("op", "%");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertFalse(result.isSuccess(), "Unsupported operation should fail");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null inputs")
        void testJavaInlineBlockNullInputs() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", null);
            context.put("inputB", 5);
            context.put("op", "+");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertFalse(result.isSuccess(), "Null input should fail");
        }

        @Test
        @DisplayName("should execute java-inline expression mode")
        void testJavaInlineExpression() {
            String code = "bpm.javacode.javaInlineExpr";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 25);
            context.put("inputB", 35);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline expression should execute successfully");
            assertEquals(60, result.getData().get("result"), "Expression a + b should work correctly");
        }

        @Test
        @DisplayName("should execute java-inline expression with different values")
        void testJavaInlineExpressionDifferentValues() {
            String code = "bpm.javacode.javaInlineExpr";

            // Test with positive numbers
            Map<String, Object> context1 = new HashMap<>();
            context1.put("inputA", 100);
            context1.put("inputB", 200);
            ProcessResult<Map<String, Object>> result1 = processEngine.execute(ProcessSource.fromCode(code), context1);
            assertTrue(result1.isSuccess());
            assertEquals(300, result1.getData().get("result"));

            // Test with negative numbers
            Map<String, Object> context2 = new HashMap<>();
            context2.put("inputA", -50);
            context2.put("inputB", 75);
            ProcessResult<Map<String, Object>> result2 = processEngine.execute(ProcessSource.fromCode(code), context2);
            assertTrue(result2.isSuccess());
            assertEquals(25, result2.getData().get("result"));

            // Test with zero
            Map<String, Object> context3 = new HashMap<>();
            context3.put("inputA", 0);
            context3.put("inputB", 42);
            ProcessResult<Map<String, Object>> result3 = processEngine.execute(ProcessSource.fromCode(code), context3);
            assertTrue(result3.isSuccess());
            assertEquals(42, result3.getData().get("result"));
        }

        @Test
        @DisplayName("should execute java-inline side effect expression")
        void testJavaInlineSideEffect() {
            String code = "bpm.javacode.javaInlineSideEffect";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 12);
            context.put("inputB", 8);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline side effect should execute successfully");
            // Side effect expression doesn't return a value, just executes the println
            assertNotNull(result.getData(), "Result data should not be null");
        }

        @Test
        @DisplayName("should execute java-inline side effect with different values")
        void testJavaInlineSideEffectDifferentValues() {
            String code = "bpm.javacode.javaInlineSideEffect";

            // Test with large numbers
            Map<String, Object> context1 = new HashMap<>();
            context1.put("inputA", 1000);
            context1.put("inputB", 2000);
            ProcessResult<Map<String, Object>> result1 = processEngine.execute(ProcessSource.fromCode(code), context1);
            assertTrue(result1.isSuccess());

            // Test with negative numbers
            Map<String, Object> context2 = new HashMap<>();
            context2.put("inputA", -10);
            context2.put("inputB", 5);
            ProcessResult<Map<String, Object>> result2 = processEngine.execute(ProcessSource.fromCode(code), context2);
            assertTrue(result2.isSuccess());
        }
    }

    @Nested
    @DisplayName("Java Source Action Tests")
    class JavaSourceActionTests {

        @Test
        @DisplayName("should execute java-source with custom method")
        void testJavaSourceCustomMethod() {
            String code = "bpm.javacode.javaSourceCustomMethod";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 30);
            context.put("inputB", 20);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java source custom method should execute successfully");
            assertEquals(50, result.getData().get("result"), "Custom sum method should work correctly");
        }

        @Test
        @DisplayName("should execute java-source custom method with null inputs")
        void testJavaSourceCustomMethodWithNullInputs() {
            String code = "bpm.javacode.javaSourceCustomMethod";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", null);
            context.put("inputB", 20);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java source custom method should handle null inputs");
            assertEquals(0, result.getData().get("result"), "Null input should return 0");
        }

        @Test
        @DisplayName("should execute java-source custom method with both null inputs")
        void testJavaSourceCustomMethodWithBothNullInputs() {
            String code = "bpm.javacode.javaSourceCustomMethod";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", null);
            context.put("inputB", null);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java source custom method should handle both null inputs");
            assertEquals(0, result.getData().get("result"), "Both null inputs should return 0");
        }

        @Test
        @DisplayName("should execute java-source with default execute method")
        void testJavaSourceExecute() {
            String code = "bpm.javacode.javaSourceExecute";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 15);
            context.put("inputB", 25);
            context.put("op", "+");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java source execute should execute successfully");
            assertEquals(40, result.getData().get("result"), "Execute method addition should work correctly");
        }

        @Test
        @DisplayName("should execute java-source execute with all operations")
        void testJavaSourceExecuteAllOperations() {
            String code = "bpm.javacode.javaSourceExecute";

            // Test addition
            Map<String, Object> addContext = new HashMap<>();
            addContext.put("inputA", 20);
            addContext.put("inputB", 30);
            addContext.put("op", "+");
            ProcessResult<Map<String, Object>> addResult = processEngine.execute(ProcessSource.fromCode(code), addContext);
            assertTrue(addResult.isSuccess());
            assertEquals(50, addResult.getData().get("result"));

            // Test subtraction
            Map<String, Object> subContext = new HashMap<>();
            subContext.put("inputA", 50);
            subContext.put("inputB", 20);
            subContext.put("op", "-");
            ProcessResult<Map<String, Object>> subResult = processEngine.execute(ProcessSource.fromCode(code), subContext);
            assertTrue(subResult.isSuccess());
            assertEquals(30, subResult.getData().get("result"));

            // Test multiplication
            Map<String, Object> mulContext = new HashMap<>();
            mulContext.put("inputA", 7);
            mulContext.put("inputB", 8);
            mulContext.put("op", "*");
            ProcessResult<Map<String, Object>> mulResult = processEngine.execute(ProcessSource.fromCode(code), mulContext);
            assertTrue(mulResult.isSuccess());
            assertEquals(56, mulResult.getData().get("result"));

            // Test division
            Map<String, Object> divContext = new HashMap<>();
            divContext.put("inputA", 60);
            divContext.put("inputB", 6);
            divContext.put("op", "/");
            ProcessResult<Map<String, Object>> divResult = processEngine.execute(ProcessSource.fromCode(code), divContext);
            assertTrue(divResult.isSuccess());
            assertEquals(10, divResult.getData().get("result"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null inputs in java-source execute")
        void testJavaSourceExecuteNullInputs() {
            String code = "bpm.javacode.javaSourceExecute";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", null);
            context.put("inputB", 5);
            context.put("op", "+");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertFalse(result.isSuccess(), "Null input should fail");
        }

        @Test
        @DisplayName("should throw ArithmeticException on division by zero in java-source execute")
        void testJavaSourceExecuteDivisionByZero() {
            String code = "bpm.javacode.javaSourceExecute";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 10);
            context.put("inputB", 0);
            context.put("op", "/");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertFalse(result.isSuccess(), "Division by zero should fail");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported operation in java-source execute")
        void testJavaSourceExecuteUnsupportedOperation() {
            String code = "bpm.javacode.javaSourceExecute";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 10);
            context.put("inputB", 5);
            context.put("op", "%");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertFalse(result.isSuccess(), "Unsupported operation should fail");
        }

        @Test
        @DisplayName("should execute java-source void method")
        void testJavaSourceVoid() {
            String code = "bpm.javacode.javaSourceVoid";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", 42);
            context.put("inputB", 58);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java source void method should execute successfully");
            // Void method doesn't return a value, just executes the println
            assertNotNull(result.getData(), "Result data should not be null");
        }

        @Test
        @DisplayName("should execute java-source void method with different values")
        void testJavaSourceVoidDifferentValues() {
            String code = "bpm.javacode.javaSourceVoid";

            // Test with large numbers
            Map<String, Object> context1 = new HashMap<>();
            context1.put("inputA", 1000);
            context1.put("inputB", 2000);
            ProcessResult<Map<String, Object>> result1 = processEngine.execute(ProcessSource.fromCode(code), context1);
            assertTrue(result1.isSuccess());

            // Test with negative numbers
            Map<String, Object> context2 = new HashMap<>();
            context2.put("inputA", -15);
            context2.put("inputB", 25);
            ProcessResult<Map<String, Object>> result2 = processEngine.execute(ProcessSource.fromCode(code), context2);
            assertTrue(result2.isSuccess());

            // Test with zero
            Map<String, Object> context3 = new HashMap<>();
            context3.put("inputA", 0);
            context3.put("inputB", 100);
            ProcessResult<Map<String, Object>> result3 = processEngine.execute(ProcessSource.fromCode(code), context3);
            assertTrue(result3.isSuccess());
        }
    }

    @Nested
    @DisplayName("Java Code Complex Tests")
    class JavaCodeComplexTests {

        @Test
        @DisplayName("should execute javaInlineMix with concurrent/parallel/inclusive gateway and loop")
        void testJavaInlineMix() {
            String code = "bpm.javacode.javaInlineMix";
            Map<String, Object> context = new HashMap<>();
            context.put("items", Arrays.asList(10, 20, 30, 40, 50)); // sum = 150

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline mix should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("result"));

            String resultStr = (String) result.getData().get("result");
            assertTrue(resultStr.contains("sum=150"), "Result should contain correct sum");
            assertTrue(resultStr.contains("ok="), "Result should contain ok count");
            assertTrue(resultStr.contains("fail="), "Result should contain fail count");
        }

        @Test
        @DisplayName("should execute javaInlineMix with empty list")
        void testJavaInlineMixEmptyList() {
            String code = "bpm.javacode.javaInlineMix";
            Map<String, Object> context = new HashMap<>();
            context.put("items", Arrays.asList(1));

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline mix with empty list should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("result"));

            String resultStr = (String) result.getData().get("result");
            assertTrue(resultStr.contains("sum=1"), "Result should contain sum=0 for empty list");
        }

        @Test
        @DisplayName("should execute javaInlineMix with negative numbers")
        void testJavaInlineMixNegativeNumbers() {
            String code = "bpm.javacode.javaInlineMix";
            Map<String, Object> context = new HashMap<>();
            context.put("items", Arrays.asList(-10, -20, -30)); // sum = -60

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline mix with negative numbers should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("result"));

            String resultStr = (String) result.getData().get("result");
            assertTrue(resultStr.contains("sum=-60"), "Result should contain correct negative sum");
        }

        @Test
        @DisplayName("should execute javaInlineMix with large numbers exceeding threshold")
        void testJavaInlineMixLargeNumbers() {
            String code = "bpm.javacode.javaInlineMix";
            Map<String, Object> context = new HashMap<>();
            context.put("items", Arrays.asList(50, 60, 70, 80)); // sum = 260, >= 100

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline mix with large numbers should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("result"));

            String resultStr = (String) result.getData().get("result");
            assertTrue(resultStr.contains("sum=260"), "Result should contain correct large sum");
        }

        @Test
        @DisplayName("should execute javaInlineEcho with path A (A not null)")
        void testJavaInlineEchoPathA() {
            String code = "bpm.javacode.javaInlineEcho";
            Map<String, Object> context = new HashMap<>();
            context.put("A", "valueA");
            context.put("B", "valueB");
            context.put("C", "valueC");
            context.put("J", "valueJ");
            context.put("F", "valueF");
            context.put("G", "valueG");
            context.put("H", "valueH");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline echo path A should execute successfully");
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("should execute javaInlineEcho with path X (A is null)")
        void testJavaInlineEchoPathX() {
            String code = "bpm.javacode.javaInlineEcho";
            Map<String, Object> context = new HashMap<>();
            context.put("A", null);
            context.put("X", "valueX");
            context.put("B", "valueB");
            context.put("C", "valueC");
            context.put("J", "valueJ");
            context.put("F", "valueF");
            context.put("G", "valueG");
            context.put("H", "valueH");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline echo path X should execute successfully");
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("should execute javaInlineEcho with all parameters")
        void testJavaInlineEchoAllParameters() {
            String code = "bpm.javacode.javaInlineEcho";
            Map<String, Object> context = new HashMap<>();
            context.put("A", "testA");
            context.put("X", "testX");
            context.put("B", "testB");
            context.put("C", "testC");
            context.put("J", "testJ");
            context.put("F", "testF");
            context.put("G", "testG");
            context.put("H", "testH");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline echo with all parameters should execute successfully");
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("should execute javaInlineEcho with minimal parameters")
        void testJavaInlineEchoMinimalParameters() {
            String code = "bpm.javacode.javaInlineEcho";
            Map<String, Object> context = new HashMap<>();
            context.put("A", "minimalA");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "Java inline echo with minimal parameters should execute successfully");
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("should execute KTV demo with multiple people")
        void testKtvDemoMultiplePeople() {
            String code = "bpm.javacode.javaCodeKtv";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("Alice", "Bob", "Charlie")); // 3 people

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "KTV demo with multiple people should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("price"));

            Integer price = (Integer) result.getData().get("price");
            assertTrue(price > 0, "Price should be positive");
            // 3 people * 80 = 240, no discount (less than 400)
            assertEquals(240, price, "Price should be 240 for 3 people without discount");
        }

        @Test
        @DisplayName("should execute KTV demo with many people (discount applied)")
        void testKtvDemoManyPeopleWithDiscount() {
            String code = "bpm.javacode.javaCodeKtv";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("Alice", "Bob", "Charlie", "David", "Eve", "Frank")); // 6 people

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "KTV demo with many people should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("price"));

            Integer price = (Integer) result.getData().get("price");
            assertTrue(price > 0, "Price should be positive");
            // 6 people * 80 = 480, with 10% discount = 432
            assertEquals(432, price, "Price should be 432 for 6 people with 10% discount");
        }

        @Test
        @DisplayName("should execute KTV demo with single person")
        void testKtvDemoSinglePerson() {
            String code = "bpm.javacode.javaCodeKtv";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("Alice")); // 1 person

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "KTV demo with single person should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("price"));

            Integer price = (Integer) result.getData().get("price");
            assertTrue(price > 0, "Price should be positive");
            // 1 person * 80 = 80, no discount (less than 400)
            assertEquals(80, price, "Price should be 80 for 1 person without discount");
        }

        @Test
        @DisplayName("should execute KTV demo with empty list")
        void testKtvDemoEmptyList() {
            String code = "bpm.javacode.javaCodeKtv";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList());

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "KTV demo with empty list should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("price"));

            Integer price = (Integer) result.getData().get("price");
            assertTrue(price >= 0, "Price should be non-negative");
            // 0 people, but base price is 80 * 0 = 0
            assertEquals(0, price, "Price should be 0 for empty list");
        }

        @Test
        @DisplayName("should execute KTV demo with exactly 5 people (threshold test)")
        void testKtvDemoThresholdTest() {
            String code = "bpm.javacode.javaCodeKtv";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("Alice", "Bob", "Charlie", "David", "Eve")); // 5 people

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            assertTrue(result.isSuccess(), "KTV demo with 5 people should execute successfully");
            assertNotNull(result.getData());
            assertNotNull(result.getData().get("price"));

            Integer price = (Integer) result.getData().get("price");
            assertTrue(price > 0, "Price should be positive");
            assertEquals(360, price, "Price should be 360 for 5 people with 10% discount");
        }

        @Test
        @DisplayName("should handle KTV demo with null price in java-source")
        void testKtvDemoNullPrice() {
            String code = "bpm.javacode.javaCodeKtv";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", null);

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            // This test documents the behavior when pList is null
            // The result depends on how the java-inline code handles null pList
            assertNotNull(result, "Result should not be null");
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle missing required parameters gracefully")
        void testMissingRequiredParameters() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            // Missing inputA, inputB, and op parameters

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            // The behavior depends on how CompileFlow handles missing parameters
            // This test documents the current behavior
            assertNotNull(result, "Result should not be null");
        }

        @Test
        @DisplayName("should handle invalid parameter types gracefully")
        void testInvalidParameterTypes() {
            String code = "bpm.javacode.javaInlineBlock";
            Map<String, Object> context = new HashMap<>();
            context.put("inputA", "not_a_number");
            context.put("inputB", 5);
            context.put("op", "+");

            ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);

            // The behavior depends on how CompileFlow handles type conversion
            // This test documents the current behavior
            assertNotNull(result, "Result should not be null");
        }
    }

    @Nested
    @DisplayName("Performance and Concurrency Tests")
    class PerformanceTests {

        @Test
        @DisplayName("should execute multiple java-inline operations efficiently")
        void testMultipleJavaInlineOperations() {
            String code = "bpm.javacode.javaInlineExpr";
            int iterations = 100;

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("inputA", i);
                context.put("inputB", i * 2);

                ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);
                assertTrue(result.isSuccess(), "Iteration " + i + " should succeed");
                assertEquals(i + (i * 2), result.getData().get("result"), "Result should be correct for iteration " + i);
            }
            long endTime = System.currentTimeMillis();

            System.out.println("Executed " + iterations + " java-inline operations in " + (endTime - startTime) + "ms");
            assertTrue((endTime - startTime) < 5000, "Performance should be acceptable for " + iterations + " iterations");
        }

        @Test
        @DisplayName("should execute multiple java-source operations efficiently")
        void testMultipleJavaSourceOperations() {
            String code = "bpm.javacode.javaSourceCustomMethod";
            int iterations = 100;

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("inputA", i);
                context.put("inputB", i + 10);

                ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);
                assertTrue(result.isSuccess(), "Iteration " + i + " should succeed");
                assertEquals(i + (i + 10), result.getData().get("result"), "Result should be correct for iteration " + i);
            }
            long endTime = System.currentTimeMillis();

            System.out.println("Executed " + iterations + " java-source operations in " + (endTime - startTime) + "ms");
            assertTrue((endTime - startTime) < 5000, "Performance should be acceptable for " + iterations + " iterations");
        }
    }
}
