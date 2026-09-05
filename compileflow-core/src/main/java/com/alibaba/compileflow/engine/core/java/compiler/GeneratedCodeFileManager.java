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
package com.alibaba.compileflow.engine.core.java.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

/**
 * Exposes application-loader class bytes to javac without transferring ownership of that loader.
 *
 * <p>The same file-manager contract is used for generated-class compilation and attributed source
 * analysis. Javac closes closeable loaders returned for annotation processing when a task completes,
 * so this adapter resolves resources directly and never returns the caller-owned loader.</p>
 *
 * @author yusu
 */
public final class GeneratedCodeFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final ClassLoader parentClassLoader;
    private final Map<String, BytecodeFileObject> outputs = new LinkedHashMap<>();

    public GeneratedCodeFileManager(StandardJavaFileManager delegate, ClassLoader parentClassLoader) {
        super(delegate);
        this.parentClassLoader = Objects.requireNonNull(parentClassLoader, "parentClassLoader");
    }

    private static void collectPackageResources(Enumeration<URL> resources, String packageName, String packagePath,
            boolean recurse, Set<String> visitedResources, Map<String, ClassLoaderFileObject> files) throws IOException {
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (!visitedResources.add(resource.toExternalForm())) {
                continue;
            }
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                collectDirectoryClasses(resource, packageName, recurse, files);
            } else {
                collectArchiveClasses(resource, packagePath, recurse, files);
            }
        }
    }

    private static void collectDirectoryClasses(URL resource, String packageName, boolean recurse,
            Map<String, ClassLoaderFileObject> files) throws IOException {
        Path directory;
        try {
            directory = Path.of(resource.toURI());
        } catch (URISyntaxException invalidUri) {
            throw new IOException("Invalid classpath directory URL: " + resource, invalidUri);
        }
        if (Files.isRegularFile(directory)) {
            directory = directory.getParent();
        }
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = recurse ? Files.walk(directory) : Files.list(directory)) {
            Iterator<Path> candidates = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(JavaFileObject.Kind.CLASS.extension))
                .iterator();
            while (candidates.hasNext()) {
                Path path = candidates.next();
                String relative =
                        directory.relativize(path).toString().replace(path.getFileSystem().getSeparator(), ".");
                String binaryName = packageName + '.' + stripClassSuffix(relative);
                files.putIfAbsent(binaryName, new ClassLoaderFileObject(binaryName, path.toUri().toURL()));
            }
        }
    }

    private static void collectArchiveClasses(URL resource, String packagePath, boolean recurse,
            Map<String, ClassLoaderFileObject> files) throws IOException {
        URLConnection connection = resource.openConnection();
        if (!(connection instanceof JarURLConnection archiveConnection)) {
            return;
        }
        String prefix = packagePath;
        if (!prefix.endsWith("/")) {
            prefix += '/';
        }
        URL archiveRoot = independentArchiveRoot(archiveConnection);
        URLConnection rootConnection = archiveRoot.openConnection();
        if (!(rootConnection instanceof JarURLConnection ownedConnection)) {
            return;
        }
        rootConnection.setUseCaches(false);
        try (JarFile archive = ownedConnection.getJarFile()) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || !entryName.startsWith(prefix)
                        || !entryName.endsWith(JavaFileObject.Kind.CLASS.extension)) {
                    continue;
                }
                String relative = entryName.substring(prefix.length());
                if (!recurse && relative.indexOf('/') >= 0) {
                    continue;
                }
                String binaryName = stripClassSuffix(entryName).replace('/', '.');
                try (InputStream input = archive.getInputStream(entry)) {
                    files.putIfAbsent(binaryName, new ClassLoaderFileObject(binaryName, input.readAllBytes()));
                }
            }
        }
    }

    private static URL independentArchiveRoot(JarURLConnection packageConnection) throws IOException {
        String root = "jar:" + packageConnection.getJarFileURL().toExternalForm() + "!/";
        try {
            return URI.create(root).toURL();
        } catch (IllegalArgumentException invalidUri) {
            throw new IOException("Invalid classpath archive URL: " + root, invalidUri);
        }
    }

    private static URI memoryUri(String binaryName, JavaFileObject.Kind kind) {
        return URI.create("mem:///" + binaryName.replace('.', '/') + kind.extension);
    }

    private static String classResourceName(String binaryName) {
        return binaryName.replace('.', '/') + JavaFileObject.Kind.CLASS.extension;
    }

    private static String stripClassSuffix(String value) {
        return value.substring(0, value.length() - JavaFileObject.Kind.CLASS.extension.length());
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
            throws IOException {
        JavaFileObject resolved = super.getJavaFileForInput(location, className, kind);
        if (resolved != null || location != StandardLocation.CLASS_PATH || kind != JavaFileObject.Kind.CLASS) {
            return resolved;
        }
        URL resource = parentClassLoader.getResource(classResourceName(className));
        return resource == null ? null : new ClassLoaderFileObject(className, resource);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
            boolean recurse) throws IOException {
        Iterable<JavaFileObject> standardFiles = super.list(location, packageName, kinds, recurse);
        if (location != StandardLocation.CLASS_PATH || !kinds.contains(JavaFileObject.Kind.CLASS)) {
            return standardFiles;
        }

        Map<String, JavaFileObject> filesByName = new LinkedHashMap<>();
        for (JavaFileObject file : standardFiles) {
            filesByName.put(inferBinaryName(location, file), file);
        }
        for (ClassLoaderFileObject file : listParentClasses(packageName, recurse)) {
            filesByName.putIfAbsent(file.binaryName, file);
        }
        return List.copyOf(filesByName.values());
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
            FileObject sibling) {
        BytecodeFileObject output = new BytecodeFileObject(className, kind);
        outputs.put(className, output);
        return output;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof BytecodeFileObject bytecode) {
            return bytecode.binaryName;
        }
        if (file instanceof ClassLoaderFileObject classpathFile) {
            return classpathFile.binaryName;
        }
        return super.inferBinaryName(location, file);
    }

    void writeClasses(ClassOutput output) throws Exception {
        for (BytecodeFileObject classFile : outputs.values()) {
            output.writeClass(classFile.binaryName, classFile.copyBytes());
        }
    }

    private List<ClassLoaderFileObject> listParentClasses(String packageName, boolean recurse) throws IOException {
        String packagePath = packageName.replace('.', '/');
        Set<String> visitedResources = new LinkedHashSet<>();
        Map<String, ClassLoaderFileObject> files = new LinkedHashMap<>();
        collectPackageResources(parentClassLoader.getResources(packagePath), packageName, packagePath, recurse,
                visitedResources, files);
        collectPackageResources(parentClassLoader.getResources(packagePath + '/'), packageName, packagePath, recurse,
                visitedResources, files);
        collectPackageResources(parentClassLoader.getResources(packagePath + "/package-info.class"), packageName,
                packagePath, recurse, visitedResources, files);
        return List.copyOf(files.values());
    }

    private static final class BytecodeFileObject extends SimpleJavaFileObject {
        private final String binaryName;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private BytecodeFileObject(String binaryName, Kind kind) {
            super(memoryUri(binaryName, kind), kind);
            this.binaryName = binaryName;
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        private byte[] copyBytes() {
            return bytes.toByteArray();
        }
    }

    private static final class ClassLoaderFileObject extends SimpleJavaFileObject {
        private final String binaryName;
        private final byte[] bytes;

        private ClassLoaderFileObject(String binaryName, URL resource) throws IOException {
            this(binaryName, readResource(resource));
        }

        private ClassLoaderFileObject(String binaryName, byte[] bytes) {
            super(memoryUri(binaryName, Kind.CLASS), Kind.CLASS);
            this.binaryName = binaryName;
            this.bytes = bytes;
        }

        private static byte[] readResource(URL resource) throws IOException {
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(true);
            try (InputStream input = connection.getInputStream()) {
                return input.readAllBytes();
            }
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
