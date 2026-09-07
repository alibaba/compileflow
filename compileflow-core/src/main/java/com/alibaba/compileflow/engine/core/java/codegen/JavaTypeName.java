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
package com.alibaba.compileflow.engine.core.java.codegen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parsed representation of a Java type name for code generation.
 *
 * @author yusu
 */
public final class JavaTypeName {
    private final String name;
    private final String rawName;
    private final String simpleName;
    private final String shortName;
    private final String packageName;
    private final List<JavaTypeName> referencedTypes;

    private JavaTypeName(String sourceName) {
        this(sourceName, true);
    }

    private JavaTypeName(String sourceName, boolean collectReferencedTypes) {
        name = requireSourceName(sourceName);
        List<TypeToken> typeTokens = scanTypeTokens(name);
        if (typeTokens.isEmpty()) {
            throw new IllegalArgumentException("class name must contain a raw type");
        }

        TypeToken rootType = typeTokens.get(0);
        rawName = rootType.qualifiedName();
        int packageSeparator = rawName.lastIndexOf('.');
        simpleName = packageSeparator < 0 ? rawName : rawName.substring(packageSeparator + 1);
        packageName = packageSeparator < 0 ? null : rawName.substring(0, packageSeparator);
        shortName = shortenTypeNames(name, typeTokens);
        referencedTypes = collectReferencedTypes ? referencedTypes(typeTokens) : List.of();
    }

    public static JavaTypeName of(String name) {
        return new JavaTypeName(name);
    }

    public static JavaTypeName of(Class<?> type) {
        Class<?> requiredType = Objects.requireNonNull(type, "type");
        String canonicalName = requiredType.getCanonicalName();
        if (canonicalName == null) {
            throw new IllegalArgumentException(
                    "Class cannot be referenced from generated source: " + requiredType.getName());
        }
        return of(canonicalName);
    }

    private static String requireSourceName(String sourceName) {
        String value = Objects.requireNonNull(sourceName, "name");
        if (value.isBlank()) {
            throw new IllegalArgumentException("class name must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("class name must not contain surrounding whitespace");
        }
        return value;
    }

    private static List<TypeToken> scanTypeTokens(String sourceName) {
        List<TypeToken> result = new ArrayList<>();
        int cursor = 0;
        while (cursor < sourceName.length()) {
            int codePoint = sourceName.codePointAt(cursor);
            if (!Character.isJavaIdentifierStart(codePoint) || Character.isIdentifierIgnorable(codePoint)) {
                cursor += Character.charCount(codePoint);
                continue;
            }

            int start = cursor;
            StringBuilder qualifiedName = new StringBuilder();
            cursor = appendIdentifier(sourceName, cursor, qualifiedName);
            while (true) {
                int separator = skipWhitespace(sourceName, cursor);
                if (separator >= sourceName.length() || sourceName.charAt(separator) != '.') {
                    break;
                }
                int nextIdentifier = skipWhitespace(sourceName, separator + 1);
                if (nextIdentifier >= sourceName.length()
                        || !Character.isJavaIdentifierStart(sourceName.codePointAt(nextIdentifier))) {
                    break;
                }
                qualifiedName.append('.');
                cursor = appendIdentifier(sourceName, nextIdentifier, qualifiedName);
            }
            result.add(new TypeToken(start, cursor, qualifiedName.toString()));
        }
        return List.copyOf(result);
    }

    private static int appendIdentifier(String sourceName, int start, StringBuilder target) {
        int cursor = start;
        while (cursor < sourceName.length()) {
            int codePoint = sourceName.codePointAt(cursor);
            if (!Character.isJavaIdentifierPart(codePoint) || Character.isIdentifierIgnorable(codePoint)) {
                break;
            }
            target.appendCodePoint(codePoint);
            cursor += Character.charCount(codePoint);
        }
        return cursor;
    }

    private static int skipWhitespace(String value, int cursor) {
        int current = cursor;
        while (current < value.length()) {
            int codePoint = value.codePointAt(current);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            current += Character.charCount(codePoint);
        }
        return current;
    }

    private static String shortenTypeNames(String sourceName, List<TypeToken> typeTokens) {
        StringBuilder result = new StringBuilder(sourceName.length());
        int cursor = 0;
        for (TypeToken token : typeTokens) {
            result.append(sourceName, cursor, token.start());
            result.append(token.simpleName());
            cursor = token.end();
        }
        result.append(sourceName, cursor, sourceName.length());
        return result.toString();
    }

    private static List<JavaTypeName> referencedTypes(List<TypeToken> typeTokens) {
        Set<String> names = new LinkedHashSet<>();
        for (TypeToken token : typeTokens) {
            if (token.qualifiedName().indexOf('.') >= 0) {
                names.add(token.qualifiedName());
            }
        }
        return names
            .stream()
            .map(name -> new JavaTypeName(name, false))
            .toList();
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getImportName() {
        return packageName == null ? null : rawName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    /**
     * Returns every qualified type referenced by this source-level type,
     * including nested generic arguments.
     *
     * @return referenced types in source order
     */
    public List<JavaTypeName> getReferencedTypes() {
        return referencedTypes;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof JavaTypeName that && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    private record TypeToken(int start, int end, String qualifiedName) {
        private String simpleName() {
            int separator = qualifiedName.lastIndexOf('.');
            return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
        }
    }
}
