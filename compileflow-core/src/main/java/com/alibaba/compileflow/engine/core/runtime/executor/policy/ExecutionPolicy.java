package com.alibaba.compileflow.engine.core.runtime.executor.policy;

import com.alibaba.compileflow.engine.core.definition.job.JobPolicy;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime execution policy holding parsed numeric fields and identifiers.
 *
 * @author yusu
 */
public final class ExecutionPolicy {

    // Strict ISO-8601 repeating pattern: R<count>/<interval>, and interval must look like ISO ("P...")
    private static final Pattern RETRY_ISO =
            Pattern.compile("^R(?<count>\\d+)\\/(?<interval>P.+)$", Pattern.CASE_INSENSITIVE);
    // Parsed numeric fields
    private final long timeoutMs;
    private final int retryCount;
    private final long retryIntervalMs;
    // never/transient/always or bean name
    private final String retryOn;
    // incident/continue or bean name
    private final String onFailure;


    private ExecutionPolicy(long timeoutMs, int retryCount, long retryIntervalMs,
                            String retryOn, String onFailure) {
        this.timeoutMs = timeoutMs;
        this.retryCount = retryCount;
        this.retryIntervalMs = retryIntervalMs;
        this.retryOn = retryOn;
        this.onFailure = onFailure;
    }

    /**
     * Factory for pre-parsed numeric fields to avoid runtime parsing costs.
     */
    public static ExecutionPolicy of(long timeoutMs, int retryCount, long retryIntervalMs,
                                     String retryOn, String onFailure) {
        return new ExecutionPolicy(timeoutMs, retryCount, retryIntervalMs, retryOn, onFailure);
    }

    public static ExecutionPolicy of(String timeout, String retry, String retryOn, String onFailure) {
        return parse(timeout, retry, retryOn, onFailure);
    }

    /**
     * Parse with strict ISO-8601 semantics.
     */
    public static ExecutionPolicy parse(String timeout, String retry, String retryOn, String onFailure) {
        Objects.requireNonNull(timeout, "timeout is null");
        Objects.requireNonNull(retry, "retry is null");

        JobPolicy jobPolicy = new JobPolicy();

        long timeoutMs = parseIsoDurationMillis(timeout);

        Matcher matcher = RETRY_ISO.matcher(retry.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid retry format: \"" + retry + "\". Expect ISO-8601 pattern R<count>/<interval>, " +
                            "e.g. R3/PT0.2S, R0/PT0S. The <interval> must be an ISO-8601 duration starting with 'P'.");
        }
        int retryCount = Integer.parseInt(matcher.group("count"));

        String intervalText = matcher.group("interval");
        long retryIntervalMs = parseIsoDurationMillis(intervalText);

        return new ExecutionPolicy(timeoutMs, retryCount, retryIntervalMs, retryOn, onFailure);
    }

    /**
     * Strict ISO-8601 duration to millis. Accepts only standard forms like PT2S, PT0.005S
     */
    private static long parseIsoDurationMillis(String raw) {
        final String s = Objects.requireNonNull(raw, "duration text is null").trim().toUpperCase(Locale.ROOT);
        try {
            Duration d = Duration.parse(s);
            long ms = d.toMillis();
            if (ms < 0) {
                throw new IllegalArgumentException("Negative duration not allowed: \"" + raw + "\"");
            }
            return ms;
        } catch (DateTimeParseException | ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Invalid ISO-8601 duration: \"" + raw + "\". Examples: PT2S (2 seconds), PT0.005S (5 ms).", ex);
        }
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public String getRetryOn() {
        return retryOn;
    }

    public String getOnFailure() {
        return onFailure;
    }

}


