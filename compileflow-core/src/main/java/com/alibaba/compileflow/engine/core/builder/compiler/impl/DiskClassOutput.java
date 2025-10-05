package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.core.builder.compiler.ClassOutput;
import com.alibaba.compileflow.engine.core.builder.compiler.CompileOption;
import com.alibaba.compileflow.engine.core.builder.compiler.CompiledArtifact;
import com.alibaba.compileflow.engine.core.builder.compiler.constants.FileConstants;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author yusu
 */
@ExtensionRealization(priority = 600)
public class DiskClassOutput implements ClassOutput {

    private File outputDir;

    @Override
    public void open(String fullClassName, CompileOption option) throws Exception {
        String base = System.getProperty("user.dir") + File.separator + ".compileflow-classes" + File.separator;
        outputDir = new File(base);
        Files.createDirectories(outputDir.toPath());
    }

    @Override
    public void writeClass(String className, byte[] bytes) throws Exception {
        Path classFilePath = outputDir.toPath().resolve(className.replace('.', File.separatorChar) + FileConstants.CLASS_FILE_SUFFIX);
        Files.createDirectories(classFilePath.getParent());
        try (FileOutputStream fos = new FileOutputStream(classFilePath.toFile())) {
            fos.write(bytes);
            fos.flush();
        }
    }

    @Override
    public CompiledArtifact finish() {
        final URL[] urls = new URL[1];
        try {
            urls[0] = outputDir.toPath().toUri().toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new UrlCompiledArtifact(urls);
    }

    private static class UrlCompiledArtifact implements CompiledArtifact {
        private final URL[] urls;

        UrlCompiledArtifact(URL[] urls) {
            this.urls = urls;
        }

        @Override
        public URL[] getUrls() {
            return urls;
        }

        @Override
        public java.util.Map<String, byte[]> getClassBytes() {
            return null;
        }

        @Override
        public void close() { /* persistent output, no-op */ }
    }

}


