package com.alibaba.compileflow.engine;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.Objects;

/**
 * Encapsulates the source of a process definition, providing a flexible and unified
 * way to specify how a flow should be loaded.
 * <p>
 * A {@code ProcessSource} can be created in one of three ways:
 * <ul>
 *   <li><b>By Code:</b> Using {@link #fromCode(String)}, which relies on the engine's
 *       default lookup mechanism (e.g., from a database or a specified directory)
 *       to find the process definition based on a unique code.</li>
 *   <li><b>By Content:</b> Using {@link #fromContent(String, String)}, which provides
 *       the raw process definition (e.g., XML) directly as a string. This is useful
 *       for dynamic or test scenarios.</li>
 *   <li><b>By Locator:</b> Using a {@link ResourceLocator} via the fluent builder API
 *       (e.g., {@code ProcessSource.builder("my.code").fromFile("/path/to/flow.bpmn").build()})
 *       to point to an external resource on the file system, classpath, or a URL.</li>
 * </ul>
 * This class is immutable and thread-safe.
 *
 * @author yusu
 * @see ResourceLocator
 */
public final class ProcessSource {

    private final String code;
    @Nullable
    private final String version;
    @Nullable
    private final String content;
    @Nullable
    private final ResourceLocator locator;

    private ProcessSource(@NotNull String code, @Nullable String version, @Nullable String content, @Nullable ResourceLocator locator) {
        if (StringUtils.isEmpty(code)) {
            throw new IllegalArgumentException("Process code cannot be null or empty.");
        }
        this.code = code;
        this.version = version;
        this.content = content;
        this.locator = locator;
    }

    /**
     * The single, private constructor that all factory methods delegate to.
     */
    public static ProcessSource of(@NotNull String code, @Nullable String content, @Nullable ResourceLocator locator) {
        return new ProcessSource(code, null, content, locator);
    }

    /**
     * Overloaded factory with explicit version.
     */
    public static ProcessSource of(@NotNull String code, @Nullable String version, @Nullable String content, @Nullable ResourceLocator locator) {
        return new ProcessSource(code, version, content, locator);
    }

    /**
     * Creates a {@code ProcessSource} that identifies a process by its unique code.
     * The engine will use its pre-configured lookup mechanism to find the corresponding
     * process definition at runtime.
     *
     * <pre>{@code
     * // The engine will look for a process with the code "my.process.code"
     * ProcessSource source = ProcessSource.fromCode("my.process.code");
     * }</pre>
     *
     * @param code The unique identifier for the process.
     * @return A new {@code ProcessSource} instance.
     */
    @NotNull
    public static ProcessSource fromCode(@NotNull String code) {
        return new ProcessSource(code, null, null, null);
    }

    /**
     * Creates a {@code ProcessSource} from an inline string representation of the process.
     * This is useful for dynamically generated flows or for embedding process definitions
     * directly in code for testing.
     *
     * <pre>{@code
     * String bpmnXml = "<definitions>...</definitions>";
     * ProcessSource source = ProcessSource.fromContent("dynamic.process", bpmnXml);
     * }</pre>
     *
     * @param code    The unique identifier for the process.
     * @param content The raw string content of the process definition (e.g., XML).
     * @return A new {@code ProcessSource} instance.
     * @throws IllegalArgumentException if the content is null.
     */
    @NotNull
    public static ProcessSource fromContent(@NotNull String code, @NotNull String content) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null when creating from content.");
        }
        return new ProcessSource(code, null, content, null);
    }

    // Convenience factory methods using the builder

    /**
     * Creates a {@code ProcessSource} from a file path.
     *
     * @param code     The unique identifier for the process.
     * @param filePath The path to the process definition file.
     * @return A new {@code ProcessSource} instance.
     */
    @NotNull
    public static ProcessSource fromFile(@NotNull String code, @NotNull String filePath) {
        return builder(code).fromFile(filePath).build();
    }

    /**
     * Creates a {@code ProcessSource} from a {@link File} object.
     *
     * @param code The unique identifier for the process.
     * @param file The {@link File} object pointing to the process definition.
     * @return A new {@code ProcessSource} instance.
     */
    @NotNull
    public static ProcessSource fromFile(@NotNull String code, @NotNull File file) {
        return builder(code).fromFile(file).build();
    }

    /**
     * Creates a {@code ProcessSource} from a classpath resource.
     *
     * <pre>{@code
     * // Loads from src/main/resources/flows/my_flow.bpmn
     * ProcessSource source = ProcessSource.fromClasspath("onboarding.flow", "flows/my_flow.bpmn");
     * }</pre>
     *
     * @param code         The unique identifier for the process.
     * @param resourcePath The path to the resource on the classpath.
     * @return A new {@code ProcessSource} instance.
     */
    @NotNull
    public static ProcessSource fromClasspath(@NotNull String code, @NotNull String resourcePath) {
        return builder(code).fromClasspath(resourcePath).build();
    }

    /**
     * Creates a {@code ProcessSource} from a URL string.
     *
     * @param code The unique identifier for the process.
     * @param url  The URL pointing to the process definition.
     * @return A new {@code ProcessSource} instance.
     */
    @NotNull
    public static ProcessSource fromUrl(@NotNull String code, @NotNull String url) {
        return builder(code).fromUrl(url).build();
    }

    /**
     * Creates a {@code ProcessSource} from a {@link URL} object.
     *
     * @param code The unique identifier for the process.
     * @param url  The {@link URL} object pointing to the process definition.
     * @return A new {@code ProcessSource} instance.
     */
    @NotNull
    public static ProcessSource fromUrl(@NotNull String code, @NotNull URL url) {
        return builder(code).fromUrl(url).build();
    }

    // Builder pattern for more explicit construction

    /**
     * Returns a new {@link ProcessSourceBuilder} for constructing a {@code ProcessSource}.
     * This fluent API is the recommended way to create a {@code ProcessSource} when loading
     * from a file, URL, or classpath, as it improves readability.
     *
     * <pre>{@code
     * ProcessSource source = ProcessSource.builder("my.versioned.flow")
     *                                     .version("1.2.0")
     *                                     .fromFile("/etc/app/flows/flow.bpmn")
     *                                     .build();
     * }</pre>
     *
     * @param code The unique identifier for the process.
     * @return A new builder instance.
     */
    @NotNull
    public static ProcessSourceBuilder builder(@NotNull String code) {
        return new ProcessSourceBuilder(code);
    }

    // ========= Getters =========

    @NotNull
    public String getCode() {
        return code;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    @Nullable
    public ResourceLocator getLocator() {
        return locator;
    }

    // Internal state-checking methods

    /**
     * Checks if this source was created with inline content.
     *
     * @return {@code true} if content is available, {@code false} otherwise.
     */
    public boolean hasContent() {
        return StringUtils.isNotEmpty(content);
    }

    /**
     * Checks if this source was created with a resource locator (e.g., file, URL).
     *
     * @return {@code true} if a locator is available, {@code false} otherwise.
     */
    public boolean hasLocator() {
        return locator != null;
    }

    /**
     * Checks if this source relies on the default lookup mechanism (i.e., specified by code only).
     *
     * @return {@code true} if neither content nor a locator is specified, {@code false} otherwise.
     */
    public boolean useDefaultLookup() {
        return !hasContent() && !hasLocator();
    }

    @Override
    public String toString() {
        String ver = StringUtils.isBlank(version) ? null : version;
        String verPart = ver == null ? "" : (", version='" + ver + "'");
        if (hasContent()) {
            return "ProcessSource{code='" + code + "'" + verPart + ", content=<inline>}";
        } else if (hasLocator()) {
            return "ProcessSource{code='" + code + "'" + verPart + ", locator=" + locator + "}";
        } else {
            return "ProcessSource{code='" + code + "'" + verPart + ", lookup=default}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcessSource)) {
            return false;
        }
        ProcessSource that = (ProcessSource) o;
        return Objects.equals(code, that.code) &&
                Objects.equals(version, that.version) &&
                Objects.equals(content, that.content) &&
                Objects.equals(locator, that.locator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, version, content, locator);
    }

    // Builder implementation

    /**
     * A fluent builder for creating {@link ProcessSource} instances.
     */
    public static class ProcessSourceBuilder {
        private final String code;
        @Nullable
        private String version;
        @Nullable
        private String content;
        @Nullable
        private ResourceLocator locator;

        ProcessSourceBuilder(@NotNull String code) {
            this.code = Objects.requireNonNull(code, "Process code cannot be null");
        }

        /**
         * Specifies the explicit version for this process source.
         *
         * @param version The version identifier (e.g., "v2").
         * @return This builder for chaining.
         */
        @NotNull
        public ProcessSourceBuilder version(@NotNull String version) {
            this.version = version;
            return this;
        }

        /**
         * Specifies that the process definition should be loaded from the given inline string.
         * <p>
         * Calling this method will clear any previously set resource locator (e.g., from a file or URL).
         *
         * @param content The raw string content of the process definition.
         * @return This builder instance for chaining.
         */
        @NotNull
        public ProcessSourceBuilder fromContent(@NotNull String content) {
            this.content = Objects.requireNonNull(content, "Content cannot be null");
            this.locator = null; // Invalidate locator if content is provided
            return this;
        }

        /**
         * Specifies that the process definition should be loaded from the given file path.
         * <p>
         * Calling this method will clear any previously set inline content.
         *
         * @param filePath The path to the process definition file.
         * @return This builder instance for chaining.
         */
        @NotNull
        public ProcessSourceBuilder fromFile(@NotNull String filePath) {
            this.locator = ResourceLocator.file(filePath);
            this.content = null; // Invalidate content if locator is provided
            return this;
        }

        /**
         * Specifies that the process definition should be loaded from the given {@link File} object.
         * <p>
         * Calling this method will clear any previously set inline content.
         *
         * @param file The {@link File} object pointing to the process definition.
         * @return This builder instance for chaining.
         */
        @NotNull
        public ProcessSourceBuilder fromFile(@NotNull File file) {
            this.locator = ResourceLocator.file(file);
            this.content = null;
            return this;
        }

        /**
         * Specifies that the process definition should be loaded from a classpath resource.
         * <p>
         * Calling this method will clear any previously set inline content.
         *
         * @param resourcePath The path to the resource on the classpath.
         * @return This builder instance for chaining.
         */
        @NotNull
        public ProcessSourceBuilder fromClasspath(@NotNull String resourcePath) {
            this.locator = ResourceLocator.classpath(resourcePath);
            this.content = null;
            return this;
        }

        /**
         * Specifies that the process definition should be loaded from the given URL string.
         * <p>
         * Calling this method will clear any previously set inline content.
         *
         * @param url The URL pointing to the process definition.
         * @return This builder instance for chaining.
         */
        @NotNull
        public ProcessSourceBuilder fromUrl(@NotNull String url) {
            this.locator = ResourceLocator.url(url);
            this.content = null;
            return this;
        }

        /**
         * Specifies that the process definition should be loaded from the given {@link URL} object.
         * <p>
         * Calling this method will clear any previously set inline content.
         *
         * @param url The {@link URL} object pointing to the process definition.
         * @return This builder instance for chaining.
         */
        @NotNull
        public ProcessSourceBuilder fromUrl(@NotNull URL url) {
            this.locator = ResourceLocator.url(url);
            this.content = null;
            return this;
        }

        /**
         * Builds the final, immutable {@link ProcessSource} instance based on the provided configuration.
         *
         * @return A new {@code ProcessSource}.
         */
        @NotNull
        public ProcessSource build() {
            return new ProcessSource(code, version, content, locator);
        }
    }
}
