package com.alibaba.compileflow.engine.extension.filter;

import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author yusu
 */
public class ReduceFilter {

    private Predicate predicate;

    private ReducePolicy reducePolicy;

    public static ReduceFilter of(Predicate predicate, ReducePolicy reducePolicy) {
        ReduceFilter reduceFilter = new ReduceFilter();
        reduceFilter.predicate = predicate;
        reduceFilter.reducePolicy = reducePolicy;
        return reduceFilter;
    }

    public static ReduceFilter first() {
        return firstMatch(null);
    }

    public static ReduceFilter firstNonNull() {
        return firstMatch(Objects::nonNull);
    }

    public static ReduceFilter firstCollectionNonEmpty() {
        return firstMatch(r -> CollectionUtils.isNotEmpty((Collection) r));
    }

    public static ReduceFilter firstMapNonEmpty() {
        return firstMatch(r -> MapUtils.isNotEmpty((Map) r));
    }

    public static ReduceFilter firstMatch(Predicate predicate) {
        return ReduceFilter.of(predicate, ReducePolicy.FISRT_MATCH);
    }

    public static ReduceFilter allNonNull() {
        return allMatch(Objects::nonNull);
    }

    public static ReduceFilter allCollectionNonEmpty() {
        return allMatch(r -> CollectionUtils.isNotEmpty((Collection) r));
    }

    public static ReduceFilter allMapNonEmpty() {
        return allMatch(r -> MapUtils.isNotEmpty((Map) r));
    }

    public static ReduceFilter allMatch(Predicate predicate) {
        return ReduceFilter.of(predicate, ReducePolicy.ALL_MATCH);
    }

    public static ReduceFilter all() {
        return ReduceFilter.of(null, ReducePolicy.ALL_MATCH);
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public ReducePolicy getReducePolicy() {
        return reducePolicy;
    }

}
