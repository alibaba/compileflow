package com.allibaba.compileflow.test.mock;

/**
 * @author pin
 */
public class MockJavaClazz {

    public void mockJavaMethod(int num) {
        System.out.println("java: number is " + num);
    }

    public void mockJavaMethod(long num) {
        System.out.println("spring-bean: number is " + num);
    }

    public int mockReturnMethod(int num) {
        System.out.println("java: minus 100");
        return num - 100;
    }

    public void doException() {
        throw new RuntimeException("mock exception");
    }

    public void mockMonitorMethod(Throwable e) {
        if (e != null) {
            e.printStackTrace();
        }
    }

    public int calPrice(int num) {
        System.out.println("total price: " + 30 * num);
        return 30 * num;
    }

}
