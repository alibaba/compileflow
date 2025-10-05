package com.alibaba.compileflow.engine.test.mock;

import org.springframework.stereotype.Service;

/**
 * @author pin
 */
@Service
public class KtvService {

    public void sing(String name) {
        System.out.println(name + " is singing");
    }

    public void payMoney(int price) {
        System.out.println("actually paid money: " + price);
    }

}
