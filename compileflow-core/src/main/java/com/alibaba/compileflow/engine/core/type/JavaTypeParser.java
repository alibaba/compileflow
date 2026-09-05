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
package com.alibaba.compileflow.engine.core.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses the Java type subset supported by process definitions.
 *
 * @author yusu
 */
final class JavaTypeParser {
    private static final int MAX_ARRAY_DIMENSIONS = 255;
    private static final Set<String> PRIMITIVES =
            Set.of("boolean", "byte", "char", "double", "float", "int", "long", "short");
    private static final Set<String> RESERVED_WORDS = Set.of("abstract", "assert", "break", "case", "catch", "class",
            "const", "continue", "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto", "if",
            "implements", "import", "instanceof", "interface", "native", "new", "package", "permits", "private",
            "protected", "public", "record", "return", "sealed", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while", "yield", "true", "false",
            "null", "_");

    private JavaTypeParser() {
    }

    static ParsedType parse(String source) {
        Parser parser = new Parser(source);
        ParsedType parsedType = parser.parseType(false);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.failure("Unexpected trailing input");
        }
        return new ParsedType(parsedType.rawName(), parsedType.arrayDimensions(), parsedType.typeArguments(),
                List.copyOf(parser.typeReferences));
    }

    record ParsedType(String rawName, int arrayDimensions, List<String> typeArguments,
            List<TypeReference> typeReferences) {}

    record TypeReference(String rawName, int argumentCount) {}

    private static final class Parser {
        private final String source;
        private final List<TypeReference> typeReferences = new ArrayList<>();
        private int position;

        private Parser(String source) {
            this.source = source;
        }

        private ParsedType parseType(boolean typeArgument) {
            skipWhitespace();
            String rawName = parseQualifiedName();
            boolean primitive = PRIMITIVES.contains(rawName);
            if (!primitive && rawName.indexOf('.') > 0
                    && PRIMITIVES.contains(rawName.substring(0, rawName.indexOf('.')))) {
                throw failure("Primitive type cannot be qualified");
            }

            skipWhitespace();
            List<String> typeArguments = List.of();
            if (peek('<')) {
                if (primitive) {
                    throw failure("Primitive types cannot have type arguments");
                }
                typeArguments = parseTypeArguments();
            }
            typeReferences.add(new TypeReference(rawName, typeArguments.size()));

            int dimensions = 0;
            while (consumeArraySuffix()) {
                dimensions++;
                if (dimensions > MAX_ARRAY_DIMENSIONS) {
                    throw failure("Array dimensions exceed the Java limit of " + MAX_ARRAY_DIMENSIONS);
                }
            }
            if (typeArgument && primitive && dimensions == 0) {
                throw failure("Primitive types cannot be generic type arguments");
            }
            return new ParsedType(rawName, dimensions, typeArguments, List.of());
        }

        private List<String> parseTypeArguments() {
            expect('<');
            skipWhitespace();
            if (peek('>')) {
                throw failure("Type argument list must not be empty");
            }
            List<String> arguments = new ArrayList<>();
            do {
                arguments.add(parseTypeArgument());
                skipWhitespace();
            } while (consume(','));
            expect('>');
            return List.copyOf(arguments);
        }

        private String parseTypeArgument() {
            skipWhitespace();
            int start = position;
            if (!consume('?')) {
                parseType(true);
                return compact(source.substring(start, position));
            }

            skipWhitespace();
            if (consumeWord("extends") || consumeWord("super")) {
                parseType(true);
            }
            return compact(source.substring(start, position));
        }

        private static String compact(String value) {
            StringBuilder result = new StringBuilder(value.length());
            value
                .codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(result::appendCodePoint);
            return result.toString();
        }

        private String parseQualifiedName() {
            StringBuilder name = new StringBuilder(parseIdentifier());
            while (true) {
                skipWhitespace();
                if (!consume('.')) {
                    return name.toString();
                }
                skipWhitespace();
                name.append('.').append(parseIdentifier());
            }
        }

        private String parseIdentifier() {
            if (atEnd()) {
                throw failure("Expected a Java type name");
            }
            int first = source.codePointAt(position);
            if (!Character.isJavaIdentifierStart(first) || Character.isIdentifierIgnorable(first)) {
                throw failure("Expected a Java type name");
            }
            int start = position;
            position += Character.charCount(first);
            while (!atEnd()) {
                int codePoint = source.codePointAt(position);
                if (!Character.isJavaIdentifierPart(codePoint)) {
                    break;
                }
                if (Character.isIdentifierIgnorable(codePoint)) {
                    throw failure("Java type name contains an ignorable character");
                }
                position += Character.charCount(codePoint);
            }
            String identifier = source.substring(start, position);
            if (RESERVED_WORDS.contains(identifier) && !PRIMITIVES.contains(identifier)) {
                throw failure("Reserved word cannot be used as a type name");
            }
            return identifier;
        }

        private boolean consumeArraySuffix() {
            int start = position;
            skipWhitespace();
            if (!consume('[')) {
                position = start;
                return false;
            }
            skipWhitespace();
            if (!consume(']')) {
                throw failure("Array suffix must end with ']'");
            }
            return true;
        }

        private boolean consumeWord(String word) {
            if (!source.startsWith(word, position)) {
                return false;
            }
            int end = position + word.length();
            if (end < source.length() && Character.isJavaIdentifierPart(source.codePointAt(end))) {
                return false;
            }
            position = end;
            skipWhitespace();
            return true;
        }

        private boolean consume(char expected) {
            if (!peek(expected)) {
                return false;
            }
            position++;
            return true;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw failure("Expected '" + expected + "'");
            }
        }

        private boolean peek(char expected) {
            return !atEnd() && source.charAt(position) == expected;
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                int codePoint = source.codePointAt(position);
                if (!Character.isWhitespace(codePoint)) {
                    break;
                }
                position += Character.charCount(codePoint);
            }
        }

        private boolean atEnd() {
            return position >= source.length();
        }

        private DataTypeException.UnsupportedTypeException failure(String reason) {
            return new DataTypeException.UnsupportedTypeException(source, reason + " at position " + position);
        }
    }
}
