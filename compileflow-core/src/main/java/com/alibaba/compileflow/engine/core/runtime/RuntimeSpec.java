package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.ProcessSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unique fingerprint for a compiled process runtime.
 * <p>
 * Combines content and ClassLoader digests for cache keys.
 *
 * @author yusu
 */
public class RuntimeSpec {

    private final String contentDigest;
    private final String classLoaderDigest;
    private final String digest;

    public RuntimeSpec(ProcessSource processSource, ClassLoader classLoader) {
        this.contentDigest = generateContentDigest(processSource.getContent());
        this.classLoaderDigest = generateClassLoaderDigest(classLoader);
        String version = processSource.getVersion();
        version = StringUtils.isBlank(version) ? "NULL" : version.trim();
        this.digest = "COD:" + processSource.getCode() + "@VER:" + version + "@CNT:" + this.contentDigest + "@CL:" + this.classLoaderDigest;
    }

    public static RuntimeSpec of(ProcessSource processSource, ClassLoader classLoader) {
        return new RuntimeSpec(processSource, classLoader);
    }

    public String getContentDigest() {
        return contentDigest;
    }

    public String getClassLoaderDigest() {
        return classLoaderDigest;
    }

    public String getDigest() {
        return digest;
    }

    private String generateContentDigest(String content) {
        return StringUtils.isBlank(content) ? "NULL" : sha256Hex(content);
    }

    private String generateClassLoaderDigest(ClassLoader classLoader) {
        if (classLoader == null) {
            return "NULL";
        }

        // Use the ClassLoader's class name and key properties for the digest.
        StringBuilder digest = new StringBuilder();
        digest.append(classLoader.getClass().getName());

        // For URLClassLoader, include its URL information to be precise.
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlCL = (URLClassLoader) classLoader;
            Arrays.stream(urlCL.getURLs())
                    .map(URL::toString)
                    .sorted()
                    .forEach(digest::append);
        }

        // Include parent ClassLoader information recursively.
        ClassLoader parent = classLoader.getParent();
        if (parent != null) {
            digest.append("@PARENT:").append(generateClassLoaderDigest(parent));
        }

        return DigestUtils.sha256Hex(digest.toString());
    }

    private String sha256Hex(String content) {
        return DigestUtils.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RuntimeSpec)) {
            return false;
        }
        RuntimeSpec that = (RuntimeSpec) o;
        return digest.equals(that.digest);
    }

    @Override
    public int hashCode() {
        return digest.hashCode();
    }

    @Override
    public String toString() {
        return digest;
    }

}
