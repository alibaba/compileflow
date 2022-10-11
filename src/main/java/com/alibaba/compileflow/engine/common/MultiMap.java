package com.alibaba.compileflow.engine.common;

import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

/**
 * @author yusu
 */
public class MultiMap<K, V> {

    private Map<K, List<V>> map;

    public MultiMap() {
        this.map = new HashMap<>();
    }

    public MultiMap(Map<K, List<V>> map) {
        this.map = map;
    }

    public static MultiMap newMap() {
        return new MultiMap();
    }

    public static MultiMap emptyMap() {
        return new MultiMap(Collections.emptyMap());
    }

    public void put(K key, V value) {
        List<V> list = map.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(value);
    }

    public void put(K key, List<V> value) {
        List<V> list = map.computeIfAbsent(key, k -> new ArrayList<>());
        list.addAll(value);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public List<V> get(final K key) {
        List<V> list = map.get(key);
        return list == null ? Collections.emptyList() : list;
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<List<V>> values() {
        return map.values();
    }

    public List<V> remove(K key) {
        return map.remove(key);
    }

    public boolean remove(final K key, final V value) {
        List<V> list = map.get(key);
        if (CollectionUtils.isNotEmpty(list)) {
            boolean success = list.remove(value);
            if (list.isEmpty()) {
                remove(key);
            }
            return success;
        }
        return false;
    }

    public void clear() {
        map.clear();
    }

}
