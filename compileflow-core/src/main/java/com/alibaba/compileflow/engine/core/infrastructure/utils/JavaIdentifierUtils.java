package com.alibaba.compileflow.engine.core.infrastructure.utils;

import org.apache.commons.lang3.StringUtils;

import javax.lang.model.SourceVersion;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Utilities for creating safe, readable Java names from arbitrary text.
 * - Unicode-friendly
 * - Avoids Java keywords
 * - Ensures legal start characters
 *
 * @author yusu
 */
public final class JavaIdentifierUtils {

    // Fallback keyword set when javax.lang.model.SourceVersion is unavailable
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "record", "sealed", "permits", "var", "yield"
    ));

    private JavaIdentifierUtils() {
    }

    public static boolean isJavaIdentifier(String s) {
        if (StringUtils.isBlank(s)) {
            return false;
        }
        if (hasSourceVersion()) {
            return !SourceVersion.isKeyword(s) && SourceVersion.isIdentifier(s);
        }
        int first = s.codePointAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            return false;
        }
        for (int i = Character.charCount(first); i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return !RESERVED.contains(s);
    }

    public static boolean isJavaKeyword(String s) {
        if (s == null) {
            return false;
        }
        if (hasSourceVersion()) {
            return SourceVersion.isKeyword(s);
        }
        return RESERVED.contains(s);
    }

    /**
     * Sanitize arbitrary text into a **legal Java identifier**.
     * Strategy:
     * - Non-identifier chars → '_'
     * - Squash consecutive '_' and trim edges
     * - Ensure legal start (prefix '_' if needed)
     * - Avoid keywords (prefix '_' if needed)
     */
    public static String toJavaIdentifier(String raw) {
        if (isBlank(raw)) {
            return "_";
        }

        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            sb.append(Character.isJavaIdentifierPart(cp) ? new String(Character.toChars(cp)) : "_");
        }

        String id = squashUnderscores(sb.toString());
        if (id.isEmpty()) {
            id = "_";
        }

        int first = id.codePointAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            id = "_" + id;
        }

        if (!isJavaIdentifier(id)) {
            id = "_" + id;
        }
        return id;
    }

    /**
     * Class names / type names → UpperCamel (PascalCase), safe & readable.
     */
    public static String toClassName(String raw) {
        String name = toUpperCamel(tokenize(raw));
        if (!isJavaIdentifier(name)) {
            name = "_" + name;
        }
        return name;
    }

    /**
     * Method names → lowerCamel, safe & readable.
     */
    public static String toMethodName(String raw) {
        return toFieldName(raw);
    }

    /**
     * Field names → lowerCamel, safe & readable.
     */
    public static String toFieldName(String raw) {
        String name = toLowerCamel(tokenize(raw));
        if (!isJavaIdentifier(name)) {
            name = "_" + name;
        }
        return name;
    }

    public static String toMethodSuffix(String raw) {
        if (isAllDigits(raw)) {
            return raw;
        }
        return toClassName(raw);
    }

    public static String toPackageName(String raw) {
        if (isBlank(raw)) {
            return "x";
        }
        String[] parts = raw.split("\\.");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String id = toJavaIdentifier(part);
            if ("_".equals(id)) {
                id = "x";
            }
            id = id.toLowerCase(Locale.ROOT);
            if (isJavaKeyword(id) || !Character.isJavaIdentifierStart(id.codePointAt(0))) {
                id = "_" + id;
            }
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(id);
        }
        return out.length() == 0 ? "x" : out.toString();
    }

    private static String[] tokenize(String raw) {
        String id = toJavaIdentifier(raw);
        String[] toks = id.split("_+");
        int n = 0;
        for (String t : toks) {
            if (!t.isEmpty()) {
                n++;
            }
        }
        String[] r = new String[n];
        int i = 0;
        for (String t : toks) {
            if (!t.isEmpty()) {
                r[i++] = t;
            }
        }
        if (r.length == 0) {
            return new String[]{"X"};
        }
        return r;
    }

    private static String toUpperCamel(String[] tokens) {
        StringBuilder out = new StringBuilder();
        for (String t : tokens) {
            out.append(capitalize(t));
        }
        String s = out.toString();
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.codePointAt(0))) {
            s = "_" + s;
        }
        return s;
    }

    private static String toLowerCamel(String[] tokens) {
        if (tokens.length == 0) {
            return "x";
        }
        StringBuilder out = new StringBuilder();
        out.append(decapitalize(tokens[0]));
        for (int i = 1; i < tokens.length; i++) {
            out.append(capitalize(tokens[i]));
        }
        String s = out.toString();
        if (!Character.isJavaIdentifierStart(s.codePointAt(0))) {
            s = "_" + s;
        }
        return s;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int cp = s.codePointAt(0);
        int upper = Character.toTitleCase(cp);
        if (cp == upper) {
            return s;
        }
        return new StringBuilder()
                .appendCodePoint(upper)
                .append(s.substring(Character.charCount(cp)))
                .toString();
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int cp = s.codePointAt(0);
        int lower = Character.toLowerCase(cp);
        if (cp == lower) {
            return s;
        }
        return new StringBuilder()
                .appendCodePoint(lower)
                .append(s.substring(Character.charCount(cp)))
                .toString();
    }

    private static String squashUnderscores(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '_') {
                if (!lastUnderscore) {
                    out.append('_');
                }
                lastUnderscore = true;
            } else {
                out.append(ch);
                lastUnderscore = false;
            }
        }

        int start = 0, end = out.length();
        if (start < end && out.charAt(start) == '_') {
            start++;
        }
        if (start < end && out.charAt(end - 1) == '_') {
            end--;
        }
        return (start < end) ? out.substring(start, end) : "";
    }

    private static boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSourceVersion() {
        try {
            SourceVersion.valueOf("RELEASE_0");
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }
}
