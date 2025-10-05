package com.alibaba.compileflow.engine;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * An abstraction for locating resources from various sources, such as the file system,
 * classpath, or a URL.
 * <p>
 * This class provides a uniform way to reference process definition files, regardless of
 * their physical location. Instances are immutable and should be created via the static
 * factory methods: {@link #file(String)}, {@link #classpath(String)}, or {@link #url(String)}.
 *
 * @author yusu
 * @see ProcessSource
 */
public abstract class ResourceLocator {

    protected final String scheme;
    protected final String path;

    protected ResourceLocator(String scheme, String path) {
        this.scheme = Objects.requireNonNull(scheme, "Scheme cannot be null");
        this.path = Objects.requireNonNull(path, "Path cannot be null");
    }

    /**
     * Creates a {@link FileLocator} for a resource on the local file system.
     *
     * @param path The absolute or relative path to the file.
     * @return A new {@code FileLocator} instance.
     */
    public static FileLocator file(String path) {
        return new FileLocator(path);
    }

    /**
     * Creates a {@link FileLocator} for a resource on the local file system from a {@link File} object.
     *
     * @param file The {@code File} object representing the resource.
     * @return A new {@code FileLocator} instance.
     */
    public static FileLocator file(File file) {
        return new FileLocator(file.getAbsolutePath());
    }

    /**
     * Creates a {@link ClasspathLocator} for a resource on the classpath.
     *
     * @param resourcePath The path to the resource within the classpath (e.g., "processes/my_flow.bpmn").
     * @return A new {@code ClasspathLocator} instance.
     */
    public static ClasspathLocator classpath(String resourcePath) {
        return new ClasspathLocator(resourcePath);
    }

    /**
     * Creates a {@link UrlLocator} for a resource at a given URL.
     *
     * @param url The string representation of the URL.
     * @return A new {@code UrlLocator} instance.
     * @throws IllegalArgumentException if the URL string is malformed.
     */
    public static UrlLocator url(String url) {
        return new UrlLocator(url);
    }

    /**
     * Creates a {@link UrlLocator} for a resource at a given {@link URL} object.
     *
     * @param url The {@code URL} object representing the resource.
     * @return A new {@code UrlLocator} instance.
     */
    public static UrlLocator url(URL url) {
        return new UrlLocator(url.toString());
    }

    /**
     * Gets the scheme of the resource locator (e.g., "file", "classpath", "url").
     *
     * @return The resource scheme.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Gets the path or identifier of the resource within its scheme.
     *
     * @return The resource path.
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the full, scheme-prefixed identifier for the resource.
     * For example, "file:/path/to/flow.bpmn" or "classpath:flows/my-flow.bpmn".
     *
     * @return The unique identifier for the resource.
     */
    public String getFullIdentifier() {
        return scheme + ":" + path;
    }

    @Override
    public String toString() {
        return getFullIdentifier();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceLocator)) {
            return false;
        }
        ResourceLocator that = (ResourceLocator) o;
        return Objects.equals(scheme, that.scheme) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, path);
    }

    /**
     * A {@link ResourceLocator} for file system resources.
     */
    public static final class FileLocator extends ResourceLocator {
        FileLocator(String path) {
            super("file", path);
        }

        /**
         * @return a {@link File} object representing the resource.
         */
        public File getFile() {
            return new File(path);
        }
    }

    /**
     * A {@link ResourceLocator} for classpath resources. The path is normalized
     * to remove any leading slashes.
     */
    public static final class ClasspathLocator extends ResourceLocator {
        ClasspathLocator(String resourcePath) {
            super("classpath", normalizeClasspathResource(resourcePath));
        }

        private static String normalizeClasspathResource(String resourcePath) {
            if (resourcePath.startsWith("/")) {
                return resourcePath.substring(1);
            }
            return resourcePath;
        }

        /**
         * @return The normalized path to the resource within the classpath.
         */
        public String getResourcePath() {
            return path;
        }
    }

    /**
     * A {@link ResourceLocator} for resources accessible via a URL.
     */
    public static final class UrlLocator extends ResourceLocator {
        private final URL url;

        UrlLocator(String urlString) {
            super("url", urlString);
            try {
                this.url = new URL(urlString);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid URL: " + urlString, e);
            }
        }

        /**
         * @return The {@link URL} object for the resource.
         */
        public URL getUrl() {
            return url;
        }
    }
}
