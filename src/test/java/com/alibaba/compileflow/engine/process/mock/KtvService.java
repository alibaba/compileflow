package com.alibaba.compileflow.engine.process.mock;

/**
 * @author pin
 */
public class KtvService {

    public void sing(String name) {
        System.out.println(name + " is singing");
    }

    public void payMoney(int price) {
        System.out.println("actually paid money: " + price);
    }

}
