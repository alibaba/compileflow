package com.alibaba.compileflow.engine.process;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.builder.compiler.impl.CompileConstants;
import com.alibaba.compileflow.engine.process.builder.converter.impl.TbbpmModelConverter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yusu
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:bean/common.xml", "classpath:bean/ktv.xml", "classpath:bean/orderFulfillment.xml"
})
public class ProcessEngineTest {

    @Test
    public void testProcessEngine() {
        final String code = "bpm.ktv.ktvExample";
        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        System.out.println(processEngine.getJavaCode(code));
        System.out.println(processEngine.execute(code, context));
        Assert.assertNotNull(processEngine);
    }

    /**
     * 验证完整的构建器模式是否可以正常工作。
     * 测试链式调用、多个配置项以及最终的 build() 方法。
     */
    @Test
    public void testFullBuilderPattern() {
        System.out.println("--- Running testFullBuilderPattern ---");

        ProcessEngine<?> engine = ProcessEngineFactory.builder()
                .enableConcurrentCompilation(true)
                .compilationExecutorService(null)
                .flowModelType(FlowModelType.TBBPM)
                .classLoader(null)
                .build();

        Assert.assertNotNull("Engine should be built successfully", engine);
        engine.preCompile("bpm.ktv.ktvExample", "bpm.gateway.sampleBranchMerge", "bpm.om.generalorderFulfillmentFlow");
        System.out.println("Builder pattern test completed.");
    }

    /**
     * 验证并发编译功能（使用内部线程池）。
     * 测试 newInstanceWithConcurrentCompilation() 快捷方法
     */
    @Test
    public void testConcurrentCompilationWithInternalPool() {
        System.out.println("--- Running testConcurrentCompilationWithInternalPool ---");
        long startTime = System.currentTimeMillis();
        ProcessEngine<?> engine = ProcessEngineFactory.newInstanceWithConcurrentCompilation();
        Assert.assertNotNull(engine);
        engine.preCompile("bpm.ktv.ktvExample", "bpm.gateway.sampleBranchMerge", "bpm.om.generalorderFulfillmentFlow");
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Concurrent compilation finished in " + duration + " ms.");
    }

    /**
     * 验证注入自定义线程池的功能。
     */
    @Test
    public void testConcurrentCompilationWithCustomPool() throws InterruptedException {
        System.out.println("--- Running testConcurrentCompilationWithCustomPool ---");
        final String customThreadPoolNamePrefix = "MY-CUSTOM-POOL-";
        ExecutorService myCustomPool = Executors.newFixedThreadPool(4,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, customThreadPoolNamePrefix + counter.incrementAndGet());
                    }
                });

        ProcessEngine<?> engine = ProcessEngineFactory.builder().compilationExecutorService(myCustomPool).build();
        Assert.assertNotNull(engine);
        engine.preCompile("bpm.ktv.ktvExample", "bpm.gateway.sampleBranchMerge", "bpm.om.generalorderFulfillmentFlow");
    }

    @Test
    public void testProcessEngineBpmn20() {
        final String code = "bpmn20.ktv.ktvExample";
        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine(FlowModelType.BPMN);
        System.out.println(processEngine.execute(code, context));
        Assert.assertNotNull(processEngine);
    }

    @Test
    public void testTbbpmModelConvert() {
        final String code = "bpm.om.waitPaySuccessFlow";
        final ProcessEngine<TbbpmModel> processEngine = ProcessEngineFactory.getProcessEngine();
        final TbbpmModel tbbpmModel = processEngine.load(code);
        final OutputStream outputStream = TbbpmModelConverter.getInstance().convertToStream(tbbpmModel);
        System.out.println(outputStream.toString());
        final String srcCode = processEngine.getJavaCode(code);
        System.out.println(srcCode);
        Assert.assertNotNull(tbbpmModel);
    }

    @Test
    public void testWaitPayProcess() {
        String code = "bpm.om.waitPaySuccessFlow";
        String javaCode = ProcessEngineFactory.getProcessEngine().getJavaCode(code);
        System.out.println(javaCode);
        Map<String, Object> context = new HashMap<>();
        context.put("num", 100d);
        ProcessEngineFactory.getProcessEngine().execute(code, context);
        Assert.assertNotNull(javaCode);
    }

    @Test
    public void testTriggerWaitPayProcess() {
        String code = "bpm.om.waitPaySuccessFlow";
        Map<String, Object> context = new HashMap<>();
        context.put("num", 100d);
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        try {
            System.out.println(processEngine.getJavaCode(code));
            System.out.println("------receiver not real event------");
            System.out.println(processEngine.trigger(code, "randomEvent", null, context));
            System.out.println("------receiver real event------");
            System.out.println(processEngine.trigger(code, "PaymentPendingCallback", "PaymentPendingCallback", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testPaymentHandlingSubFlow() {
        String code = "bpm.om.paymentHandlingSubFlow";
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();

        System.out.println(ProcessEngineFactory.getProcessEngine().getJavaCode(code));

        Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        try {
            System.out.println(processEngine.getJavaCode(code));
            System.out.println(processEngine.trigger(code, "paymentHandlingSubFlow", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testStatefulProcessEngine() {
        String code = "bpm.om.generalOrderFulfillmentFlow";
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        System.out.println(ProcessEngineFactory.getProcessEngine().getJavaCode(code));
        Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        try {
            System.out.println(processEngine.getJavaCode(code));
            System.out.println(processEngine.trigger(code, "PaymentPendingCallback", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testProcessEngineLoadFromContent() {
        final String code = "bpm.ktv.ktvExample";
        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        System.out.println(processEngine.execute(code, context, bpmXmlContent));
        Assert.assertNotNull(processEngine);
    }

    @Test
    public void testSampleBranchMergeFlow() {
        final String code = "bpm.gateway.sampleBranchMerge";
        final Map<String, Object> context = new HashMap<>();
        context.put("route", "A");
        context.put("next", "D");
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testComplexBranchMergeFlow() {
        final String code = "bpm.gateway.complexBranchMerge";
        final Map<String, Object> context = new HashMap<>();
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testSimple4LevelGateway() {
        final String code = "bpm.gateway.simple4LevelGateway";
        final Map<String, Object> context = new HashMap<>();
        context.put("flag", true);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testNested4LevelGateway() {
        final String code = "bpm.gateway.nested4LevelGateway";
        final Map<String, Object> context = new HashMap<>();
        context.put("flag1", true);
        context.put("flag2", true);
        context.put("flag3", true);
        context.put("flag4", true);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testUltraComplexStructure() {
        final String code = "bpm.gateway.ultraComplexStructure";
        final Map<String, Object> context = new HashMap<>();
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testConcurrentExecution() throws InterruptedException {
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        final String code = "bpm.ktv.ktvExample";
        final int threadCount = 20;
        final int executionPerThread = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 等待所有线程准备就绪
                    startLatch.await();

                    // 每个线程执行多次流程
                    for (int j = 0; j < executionPerThread; j++) {
                        try {
                            Map<String, Object> context = new HashMap<>();
                            List<String> pList = new ArrayList<>();
                            pList.add("user" + threadId + "_" + j);
                            pList.add("user" + threadId + "_" + (j + 1));
                            context.put("pList", pList);

                            // 执行流程
                            Map<String, Object> result = processEngine.execute(code, context);

                            // 验证结果
                            Assert.assertNotNull("Result should not be null for thread " + threadId + ", execution " + j, result);
                            Assert.assertTrue("Result should contain price for thread " + threadId + ", execution " + j,
                                    result.containsKey("price"));

                            successCount.incrementAndGet();

                            // 添加小延迟模拟真实场景
                            Thread.sleep(1);

                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            exceptions.add(e);
                            System.err.println("Thread " + threadId + " execution " + j + " failed: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动所有线程
        startLatch.countDown();

        // 等待所有线程完成
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);

        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证结果
        Assert.assertTrue("Test should complete within timeout", completed);
        Assert.assertEquals("All threads should complete", 0, endLatch.getCount());

        int totalExecutions = threadCount * executionPerThread;
        int actualSuccessCount = successCount.get();
        int actualFailureCount = failureCount.get();

        System.out.println("Concurrent execution results:");
        System.out.println("Total executions: " + totalExecutions);
        System.out.println("Successful executions: " + actualSuccessCount);
        System.out.println("Failed executions: " + actualFailureCount);
        System.out.println("Success rate: " + (actualSuccessCount * 100.0 / totalExecutions) + "%");

        Assert.assertTrue("Success rate should be 100%",
                actualSuccessCount * 100.0 / totalExecutions == 100.0);

        // 失败次数应该很少
        Assert.assertTrue("Failure count should be minimal", actualFailureCount <= totalExecutions * 0.05);

        // 如果有异常，打印详细信息
        if (!exceptions.isEmpty()) {
            System.err.println("Exceptions occurred during concurrent execution:");
            for (Exception e : exceptions) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testConcurrentExecutionWithDifferentFlows() throws InterruptedException {
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        final String[] flowCodes = {
                "bpm.ktv.ktvExample",
                "bpm.gateway.simple4LevelGateway",
                "bpm.gateway.complexBranchMerge"
        };
        final int threadCount = 15;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final Map<String, AtomicInteger> flowSuccessCounts = new ConcurrentHashMap<>();

        // 初始化每个流程的成功计数器
        for (String flowCode : flowCodes) {
            flowSuccessCounts.put(flowCode, new AtomicInteger(0));
        }

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // 每个线程执行不同的流程
                    for (int j = 0; j < 5; j++) {
                        String flowCode = flowCodes[j % flowCodes.length];

                        try {
                            Map<String, Object> context = new HashMap<>();

                            // 根据流程类型设置不同的上下文
                            if (flowCode.contains("ktv")) {
                                List<String> pList = new ArrayList<>();
                                pList.add("user" + threadId + "_" + j);
                                context.put("pList", pList);
                            } else if (flowCode.contains("gateway")) {
                                context.put("flag", (j % 2) == 0);
                                context.put("route", "A");
                            }

                            // 执行流程
                            Map<String, Object> result = processEngine.execute(flowCode, context);

                            // 验证结果
                            Assert.assertNotNull("Result should not be null for flow " + flowCode +
                                    ", thread " + threadId + ", execution " + j, result);

                            successCount.incrementAndGet();
                            flowSuccessCounts.get(flowCode).incrementAndGet();

                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            System.err.println("Flow " + flowCode + ", thread " + threadId +
                                    ", execution " + j + " failed: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动所有线程
        startLatch.countDown();

        // 等待所有线程完成
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);

        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证结果
        Assert.assertTrue("Test should complete within timeout", completed);

        int totalExecutions = threadCount * 5;
        int actualSuccessCount = successCount.get();
        int actualFailureCount = failureCount.get();

        System.out.println("Concurrent execution with different flows results:");
        System.out.println("Total executions: " + totalExecutions);
        System.out.println("Successful executions: " + actualSuccessCount);
        System.out.println("Failed executions: " + actualFailureCount);

        // 打印每个流程的成功率
        for (String flowCode : flowCodes) {
            int flowSuccess = flowSuccessCounts.get(flowCode).get();
            // 计算每个流程的实际执行次数
            int flowTotal = 0;
            for (int i = 0; i < threadCount; i++) {
                for (int j = 0; j < 5; j++) {
                    if (flowCodes[j % flowCodes.length].equals(flowCode)) {
                        flowTotal++;
                    }
                }
            }
            System.out.println("Flow " + flowCode + " success rate: " +
                    (flowSuccess * 100.0 / flowTotal) + "% (executed " + flowSuccess + "/" + flowTotal + " times)");
        }

        Assert.assertTrue("Overall success rate should be 100%",
                actualSuccessCount * 100.0 / totalExecutions == 100.0);
    }

    @Test
    public void testConcurrentCompilationAndExecution() throws InterruptedException {
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        final String code = "bpm.ktv.ktvExample";
        final int threadCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final AtomicInteger compilationSuccessCount = new AtomicInteger(0);
        final AtomicInteger executionSuccessCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // 混合执行编译和流程执行
                    for (int j = 0; j < 3; j++) {
                        try {
                            if (j % 2 == 0) {
                                // 执行代码生成/编译
                                String javaCode = processEngine.getJavaCode(code);
                                Assert.assertNotNull("Generated Java code should not be null", javaCode);
                                Assert.assertTrue("Generated code should contain class definition",
                                        javaCode.contains("public class"));
                                compilationSuccessCount.incrementAndGet();
                            } else {
                                // 执行流程
                                Map<String, Object> context = new HashMap<>();
                                List<String> pList = new ArrayList<>();
                                pList.add("user" + threadId + "_" + j);
                                context.put("pList", pList);

                                Map<String, Object> result = processEngine.execute(code, context);
                                Assert.assertNotNull("Execution result should not be null", result);
                                executionSuccessCount.incrementAndGet();
                            }

                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            System.err.println("Thread " + threadId + ", operation " + j +
                                    " failed: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动所有线程
        startLatch.countDown();

        // 等待所有线程完成
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);

        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证结果
        Assert.assertTrue("Test should complete within timeout", completed);

        int totalOperations = threadCount * 3;
        int actualCompilationSuccess = compilationSuccessCount.get();
        int actualExecutionSuccess = executionSuccessCount.get();
        int actualFailureCount = failureCount.get();

        System.out.println("Concurrent compilation and execution results:");
        System.out.println("Total operations: " + totalOperations);
        System.out.println("Successful compilations: " + actualCompilationSuccess);
        System.out.println("Successful executions: " + actualExecutionSuccess);
        System.out.println("Failed operations: " + actualFailureCount);

        int totalSuccess = actualCompilationSuccess + actualExecutionSuccess;
        double successRate = totalSuccess * 100.0 / totalOperations;
        System.out.println("Overall success rate: " + String.format("%.1f", successRate) + "%");
        Assert.assertTrue("Overall success rate should be 100%",
                successRate == 100.0);
    }

    /**
     * 验证通过构建器设置的ClassLoader是否被用作默认加载器。
     */
    @Test
    public void testSetClassLoaderViaBuilder() {
        System.out.println("--- Running testSetClassLoaderViaBuilder ---");
        // Arrange: 创建一个带标志位的自定义ClassLoader
        FlaggedClassLoader customClassLoader;
        try {
            customClassLoader = new FlaggedClassLoader(new URL[]{new URL("file:///" + CompileConstants.FLOW_COMPILE_CLASS_DIR)});
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // 使用构建器设置默认的ClassLoader
        ProcessEngine engine = ProcessEngineFactory.builder()
                .classLoader(customClassLoader)
                .build();
        // Act: 调用一个不带ClassLoader参数的preCompile方法。
        // 期望它会使用我们在构建时设置的默认ClassLoader。
        engine.preCompile("bpm.ktv.ktvExample", "bpm.gateway.sampleBranchMerge", "bpm.om.generalorderFulfillmentFlow");

        // Assert: 验证我们的自定义ClassLoader上的标志位是否被设置为true
        Assert.assertTrue("The custom ClassLoader set via builder should have been used.",
                customClassLoader.wasUsed);
        System.out.println("Successfully verified that the default ClassLoader was used.");
    }

    /**
     * 验证在方法调用时传入的ClassLoader是否会覆盖引擎的默认设置。
     */
    @Test
    public void testClassLoaderOverrideOnMethodCall() {
        System.out.println("--- Running testClassLoaderOverrideOnMethodCall ---");
        // Arrange: 创建两个不同的自定义ClassLoader
        FlaggedClassLoader defaultClassLoader;
        try {
            defaultClassLoader = new FlaggedClassLoader(new URL[]{new URL("file:///" + CompileConstants.FLOW_COMPILE_CLASS_DIR)});
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        FlaggedClassLoader overrideClassLoader;
        try {
            overrideClassLoader = new FlaggedClassLoader(new URL[]{new URL("file:///" + CompileConstants.FLOW_COMPILE_CLASS_DIR)});
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // 使用构建器设置一个默认的ClassLoader
        ProcessEngine engine = ProcessEngineFactory.builder()
                .classLoader(defaultClassLoader)
                .build();

        // Act: 调用一个带ClassLoader参数的preCompile方法，并传入另一个ClassLoader。
        // 期望这次调用会使用 overrideClassLoader，而不是 defaultClassLoader。
        engine.reCompile(overrideClassLoader, "bpm.ktv.ktvExample", "bpm.gateway.sampleBranchMerge", "bpm.om.generalorderFulfillmentFlow");

        // Assert: 验证默认的ClassLoader未被使用，而用于覆盖的ClassLoader被使用了。
        Assert.assertFalse("The default ClassLoader should NOT have been used.",
                defaultClassLoader.wasUsed);
        Assert.assertTrue("The override ClassLoader SHOULD have been used.",
                overrideClassLoader.wasUsed);
        System.out.println("Successfully verified that the method-level ClassLoader overrides the default.");
    }

    /**
     * 一个用于测试的自定义ClassLoader。
     * 当它的 loadClass 方法被调用时，会设置一个标志位。
     */
    private static class FlaggedClassLoader extends URLClassLoader {
        public volatile boolean wasUsed = false;

        public FlaggedClassLoader(URL[] urls) {
            super(urls);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // 设置标志位，表示这个ClassLoader被使用过了
            this.wasUsed = true;
            System.out.println("FlaggedClassLoader was used to load: " + name);
            return super.loadClass(name);
        }
    }

    private final String bpmXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<bpm code=\"bpm.ktv.ktvExample\" name=\"ktv example\" type=\"process\" description=\"ktv example\">\n" +
            "    <var name=\"price\" description=\"支付价格\" dataType=\"java.lang.Integer\" inOutType=\"return\"/>\n" +
            "    <var name=\"totalPrice\" description=\"实付价\" dataType=\"java.lang.Integer\" inOutType=\"inner\"/>\n" +
            "    <var name=\"pList\" description=\"人员\" dataType=\"java.util.List&lt;java.lang.String&gt;\" inOutType=\"param\"/>\n" +
            "    <end id=\"11\" name=\"结束\" g=\"101,549,30,30\"/>\n" +
            "    <autoTask id=\"12\" name=\"付款\" g=\"72,469,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"11\"/>\n" +
            "        <action type=\"java\">\n" +
            "            <actionHandle clazz=\"com.alibaba.compileflow.engine.process.mock.KtvService\" method=\"payMoney\">\n" +
            "                <var name=\"p1\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"param\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </autoTask>\n" +
            "    <scriptTask id=\"9\" name=\"原价\" g=\"132,389,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"12\"/>\n" +
            "        <action type=\"ql\">\n" +
            "            <actionHandle expression=\"price*1\">\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </scriptTask>\n" +
            "    <loopProcess id=\"13\" name=\"循环节点\" g=\"20,75,198,190\" collectionVarName=\"pList\" variableName=\"p\" indexVarName=\"i\"\n" +
            "                 variableClass=\"java.lang.String\" startNodeId=\"13-1\" endNodeId=\"13-1\">\n" +
            "        <transition g=\":-15,20\" to=\"8\"/>\n" +
            "        <autoTask id=\"13-1\" name=\"每人唱一首歌\" g=\"70,95,88,48\">\n" +
            "            <action type=\"spring-bean\">\n" +
            "                <actionHandle bean=\"ktvService\" clazz=\"com.alibaba.compileflow.engine.process.mock.KtvService\" method=\"sing\">\n" +
            "                    <var name=\"p1\" description=\"\" dataType=\"java.lang.String\" contextVarName=\"p\" defaultValue=\"\"\n" +
            "                         inOutType=\"param\"/>\n" +
            "                </actionHandle>\n" +
            "            </action>\n" +
            "        </autoTask>\n" +
            "    </loopProcess>\n" +
            "    <decision id=\"8\" name=\"计算费用\" g=\"72,309,88,48\">\n" +
            "        <transition expression=\"\" name=\"不超过400\" priority=\"1\" g=\":-15,20\" to=\"9\"/>\n" +
            "        <transition expression=\"totalPrice&gt;=400\" name=\"超过400\" priority=\"10\" g=\":-15,20\" to=\"10\"/>\n" +
            "        <action type=\"java\">\n" +
            "            <actionHandle clazz=\"com.alibaba.compileflow.engine.process.mock.MockJavaClazz\" method=\"calPrice\">\n" +
            "                <var name=\"p1\" description=\"人数\" dataType=\"java.lang.Integer\" contextVarName=\"pList.size()\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"p2\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </decision>\n" +
            "    <start id=\"1\" name=\"开始\" g=\"105,17,30,30\">\n" +
            "        <transition g=\":-15,20\" to=\"13\"/>\n" +
            "    </start>\n" +
            "    <note id=\"14\" g=\"273,82,93,55\" comment=\"外框为循环节点\" visible=\"true\">\n" +
            "        <transition g=\":-15,20\" to=\"13\"/>\n" +
            "    </note>\n" +
            "    <scriptTask id=\"10\" name=\"9折优惠\" g=\"12,389,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"12\"/>\n" +
            "        <action type=\"ql\">\n" +
            "            <actionHandle expression=\"(round(price*0.9,0)).intValue()\">\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </scriptTask>\n" +
            "</bpm>";

}
