package com.alibaba.compileflow.engine.core.runtime.executor.retry;

/**
 * @author yusu
 */
public final class RetryPolicies {

    public static final RetryPolicy ALWAYS = t -> true;
    public static final RetryPolicy NEVER = t -> false;
    public static final RetryPolicy TRANSIENT = t -> {
        Throwable e = rootCause(t);

        if (e instanceof InterruptedException || e instanceof java.util.concurrent.CancellationException) {
            return false;
        }

        if (e instanceof java.net.ConnectException) {
            return true;
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return true;
        }
        if (e instanceof java.io.InterruptedIOException) {
            return true;
        }

        if (e instanceof java.net.SocketException) {
            String msg = safeMsg(e);
            if (containsAny(msg, "connection reset", "connection aborted", "connection refused", "no route to host")) {
                return true;
            }
        }

        if (e instanceof java.sql.SQLTransientException) {
            return true;
        }
        if (e instanceof java.sql.SQLRecoverableException) {
            return true;
        }
        if (e instanceof java.sql.SQLTransactionRollbackException) {
            return true;
        }
        if (e instanceof java.sql.SQLTimeoutException) {
            return true;
        }

        return false;
    };

    private RetryPolicies() {
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur == null ? t : cur;
    }

    private static String safeMsg(Throwable e) {
        String m = e.getMessage();
        return m == null ? "" : m.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

}
