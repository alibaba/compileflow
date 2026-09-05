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
package com.alibaba.compileflow.engine.test.support.fixtures.om.context;

import java.util.HashMap;
import java.util.Map;

public class BusinessContext {
    private String orderId;
    private Double orderAmount;
    private String customerType;
    private String paymentType;
    private Map<String, Object> additionalData = new HashMap<>();

    public BusinessContext() {
    }

    public BusinessContext(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Double getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(Double orderAmount) {
        this.orderAmount = orderAmount;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public void put(String key, Object value) {
        additionalData.put(key, value);
    }

    public Object get(String key) {
        return additionalData.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = additionalData.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public boolean containsKey(String key) {
        return additionalData.containsKey(key);
    }

    public Map<String, Object> getAdditionalData() {
        return new HashMap<>(additionalData);
    }

    @Override
    public String toString() {
        return "BusinessContext{" + "orderId='" + orderId + '\'' + ", orderAmount=" + orderAmount + ", customerType='"
                + customerType + '\'' + ", paymentType='" + paymentType + '\'' + ", additionalData=" + additionalData
                + '}';
    }
}
