package com.alibaba.compileflow.engine.test.execution;

import org.springframework.stereotype.Service;

/**
 * @author yusu
 */
@Service
public class MonitoringService {
    void alert(String ruleName, String message, Throwable error) {
        System.out.println("Alert: " + ruleName + " - " + message +
                (error != null ? " Error: " + error.getMessage() : ""));
    }

}
