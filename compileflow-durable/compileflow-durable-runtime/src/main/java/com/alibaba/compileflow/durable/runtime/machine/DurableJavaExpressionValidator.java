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
package com.alibaba.compileflow.durable.runtime.machine;

import com.alibaba.compileflow.engine.core.java.compiler.GeneratedCodeFileManager;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Fail-closed, typed validator for Java control expressions used by Durable processes.
 *
 * <p>The exact expression is embedded in one synthetic Java 17 method, parsed and
 * attributed by javac, then checked against a small AST and resolved-symbol allowlist.
 * Nothing derived from javac is persisted; current Runtime loading repeats this
 * validation from the immutable process definition.</p>
 *
 * @author yusu
 */
final class DurableJavaExpressionValidator implements DurableExpressionValidator {
    private static final int MAX_SOURCE_BYTES = 8 * 1024;
    private static final int MAX_IDENTIFIERS = 128;
    private static final int MAX_AST_NODES = 512;
    private static final int MAX_AST_DEPTH = 64;
    private static final String CLASS_NAME = "_CfDurableExpression";
    private static final String FILE_NAME = CLASS_NAME + ".java";
    private static final Set<Tree.Kind> ALLOWED_KINDS = EnumSet.of(Tree.Kind.PARENTHESIZED, Tree.Kind.IDENTIFIER,
            Tree.Kind.MEMBER_SELECT, Tree.Kind.METHOD_INVOCATION, Tree.Kind.BOOLEAN_LITERAL, Tree.Kind.CHAR_LITERAL,
            Tree.Kind.INT_LITERAL, Tree.Kind.LONG_LITERAL, Tree.Kind.FLOAT_LITERAL, Tree.Kind.DOUBLE_LITERAL,
            Tree.Kind.STRING_LITERAL, Tree.Kind.NULL_LITERAL, Tree.Kind.CONDITIONAL_AND, Tree.Kind.CONDITIONAL_OR,
            Tree.Kind.EQUAL_TO, Tree.Kind.NOT_EQUAL_TO, Tree.Kind.LESS_THAN, Tree.Kind.LESS_THAN_EQUAL,
            Tree.Kind.GREATER_THAN, Tree.Kind.GREATER_THAN_EQUAL, Tree.Kind.PLUS, Tree.Kind.MINUS, Tree.Kind.MULTIPLY,
            Tree.Kind.DIVIDE, Tree.Kind.REMAINDER, Tree.Kind.AND, Tree.Kind.OR, Tree.Kind.XOR, Tree.Kind.LEFT_SHIFT,
            Tree.Kind.RIGHT_SHIFT, Tree.Kind.UNSIGNED_RIGHT_SHIFT, Tree.Kind.UNARY_PLUS, Tree.Kind.UNARY_MINUS,
            Tree.Kind.LOGICAL_COMPLEMENT, Tree.Kind.BITWISE_COMPLEMENT, Tree.Kind.CONDITIONAL_EXPRESSION);
    private static final Map<String, Set<String>> SAFE_JDK_INSTANCE_METHODS = Map.ofEntries(Map.entry("java.lang.String",
                    Set.of("length", "isEmpty", "equals", "compareTo", "startsWith", "endsWith", "contains")),
            Map.entry("java.lang.Boolean", Set.of("booleanValue", "equals", "compareTo")),
            Map.entry("java.lang.Byte", Set.of("byteValue", "equals", "compareTo")),
            Map.entry("java.lang.Short", Set.of("shortValue", "equals", "compareTo")),
            Map.entry("java.lang.Integer", Set.of("intValue", "equals", "compareTo")),
            Map.entry("java.lang.Long", Set.of("longValue", "equals", "compareTo")),
            Map.entry("java.lang.Float", Set.of("floatValue", "equals", "compareTo")),
            Map.entry("java.lang.Double", Set.of("doubleValue", "equals", "compareTo")),
            Map.entry("java.lang.Character", Set.of("charValue", "equals", "compareTo")),
            Map.entry("java.math.BigInteger", Set.of("signum", "equals", "compareTo")),
            Map.entry("java.math.BigDecimal", Set.of("signum", "equals", "compareTo")),
            Map.entry("java.time.Duration", Set.of("isZero", "isNegative", "equals", "compareTo")),
            Map.entry("java.time.Instant", Set.of("equals", "compareTo", "isBefore", "isAfter")),
            Map.entry("java.time.LocalDate", Set.of("equals", "compareTo", "isBefore", "isAfter")),
            Map.entry("java.time.LocalDateTime", Set.of("equals", "compareTo", "isBefore", "isAfter")),
            Map.entry("java.util.Collection", Set.of("size", "isEmpty", "contains")),
            Map.entry("java.util.List", Set.of("size", "isEmpty", "contains")),
            Map.entry("java.util.Set", Set.of("size", "isEmpty", "contains")),
            Map.entry("java.util.Map", Set.of("size", "isEmpty", "containsKey", "containsValue")));

    /**
     * Validates one exact expression against its visible typed slots and required result type.
     */
    @Override
    public List<String> validate(String expression, Map<String, String> visibleTypes, TargetType targetType) {
        Objects.requireNonNull(visibleTypes, "visibleTypes");
        Objects.requireNonNull(targetType, "targetType");
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        byte[] expressionBytes = expression.getBytes(StandardCharsets.UTF_8);
        if (expressionBytes.length > MAX_SOURCE_BYTES) {
            return List.of("Expression exceeds the " + MAX_SOURCE_BYTES + " byte limit");
        }
        if (visibleTypes.size() > MAX_IDENTIFIERS) {
            return List.of("Expression visibility exceeds the " + MAX_IDENTIFIERS + " identifier limit");
        }

        LinkedHashMap<String, String> slots = new LinkedHashMap<>();
        visibleTypes
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                slots.put(requireIdentifier(entry.getKey()), requireTypeName(entry.getValue()));
            });
        String sourceText = source(expression, slots, targetType.javaType());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return List.of("A full JDK compiler is required to validate Durable expressions");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject source = new StringSource(sourceText);
        List<String> problems = new ArrayList<>();
        try (StandardJavaFileManager files =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            GeneratedCodeFileManager applicationFiles = new GeneratedCodeFileManager(files, contextClassLoader());
            JavacTask task = (JavacTask) compiler.getTask(null, applicationFiles, diagnostics,
                    List.of("--release", "17", "-proc:none", "-Xlint:none"), null, List.of(source));
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            ExpressionShape shape = requireShape(units, sourceText, expression, task, problems);
            task.analyze();
            addDiagnostics(diagnostics, problems);
            if (shape != null && problems.isEmpty()) {
                Trees trees = Trees.instance(task);
                TreePath expressionPath = TreePath.getPath(shape.unit(), shape.expression());
                if (expressionPath == null) {
                    problems.add("Expression tree is detached from its synthetic wrapper");
                } else {
                    new AllowlistScanner(trees, slots.keySet(), problems).scan(expressionPath, null);
                }
            }
        } catch (RuntimeException failure) {
            problems.add("Expression validation failed: " + failure.getClass().getSimpleName());
        } catch (Exception failure) {
            problems.add("Expression compiler failed: " + failure.getClass().getSimpleName());
        }
        addDiagnostics(diagnostics, problems);
        return problems.stream().distinct().limit(32).toList();
    }

    private static ExpressionShape requireShape(List<CompilationUnitTree> units, String source, String expression,
            JavacTask task, List<String> problems) {
        if (units.size() != 1) {
            problems.add("Expression wrapper must contain exactly one compilation unit");
            return null;
        }
        CompilationUnitTree unit = units.get(0);
        if (unit.getPackageName() != null || !unit.getImports().isEmpty() || unit.getTypeDecls().size() != 1
                || !(unit.getTypeDecls().get(0) instanceof ClassTree type)
                || !type.getSimpleName().contentEquals(CLASS_NAME)) {
            problems.add("Expression escaped its synthetic compilation wrapper");
            return null;
        }
        List<? extends Tree> members = type.getMembers();
        List<MethodTree> methods = members
            .stream()
            .filter(MethodTree.class::isInstance)
            .map(MethodTree.class::cast)
            .filter(method -> method.getName().contentEquals("evaluate"))
            .toList();
        if (methods.size() != 1 || members.size() != 1) {
            problems.add("Expression wrapper must contain exactly one evaluate method");
            return null;
        }
        MethodTree method = methods.get(0);
        if (method.getBody() == null || method.getBody().getStatements().size() != 1
                || !(method.getBody().getStatements().get(0) instanceof ReturnTree returned)
                || returned.getExpression() == null) {
            problems.add("Expression changed the synthetic method shape");
            return null;
        }
        SourcePositions positions = Trees.instance(task).getSourcePositions();
        long start = positions.getStartPosition(unit, returned.getExpression());
        long end = positions.getEndPosition(unit, returned.getExpression());
        String expected = '(' + expression + ')';
        if (start < 0 || end < start || end > source.length()
                || !source.substring((int) start, (int) end).equals(expected)) {
            problems.add("Expression source span does not match the exact declared source");
            return null;
        }
        return new ExpressionShape(unit, returned.getExpression());
    }

    private static void addDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics, List<String> problems) {
        diagnostics
            .getDiagnostics()
            .stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> "javac error at " + diagnostic.getLineNumber() + ':' + diagnostic.getColumnNumber()
                    + ": " + diagnostic.getMessage(Locale.ROOT))
            .forEach(problems::add);
    }

    private static String source(String expression, Map<String, String> slots, String returnType) {
        String parameters = slots
            .entrySet()
            .stream()
            .map(entry -> entry.getValue() + ' ' + entry.getKey())
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
        return "final class " + CLASS_NAME + " { static " + returnType + " evaluate(" + parameters + ") { return ("
                + expression + "); } }";
    }

    private static String requireIdentifier(String value) {
        String identifier = Objects.requireNonNull(value, "identifier");
        if (!SourceVersion.isIdentifier(identifier) || SourceVersion.isKeyword(identifier)) {
            throw new IllegalArgumentException("Invalid Java expression slot: " + identifier);
        }
        return identifier;
    }

    private static String requireTypeName(String value) {
        final String type;
        try {
            type = DataTypes.getRawTypeName(Objects.requireNonNull(value, "type"));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Java expression slot type: " + value, invalid);
        }
        if (type.isEmpty()
                || !(type.equals("boolean") || type.equals("byte") || type.equals("short") || type.equals("int")
                || type.equals("long") || type.equals("float") || type.equals("double") || type.equals("char")
                || type.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*(\\[\\])?"))) {
            throw new IllegalArgumentException("Invalid Java expression slot type: " + type);
        }
        return type;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? DurableJavaExpressionValidator.class.getClassLoader() : loader;
    }

    private record ExpressionShape(CompilationUnitTree unit, ExpressionTree expression) {}

    private static final class StringSource extends SimpleJavaFileObject {
        private final String source;

        private StringSource(String source) {
            super(URI.create("string:///" + FILE_NAME), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class AllowlistScanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Set<String> allowedIdentifiers;
        private final List<String> problems;
        private int nodes;
        private int depth;

        private AllowlistScanner(Trees trees, Set<String> allowedIdentifiers, List<String> problems) {
            this.trees = trees;
            this.allowedIdentifiers = allowedIdentifiers;
            this.problems = problems;
        }

        @Override
        public Void scan(Tree tree, Void unused) {
            if (tree == null) {
                return null;
            }
            nodes++;
            depth++;
            try {
                if (nodes > MAX_AST_NODES) {
                    problems.add("Expression exceeds the " + MAX_AST_NODES + " AST node limit");
                    return null;
                }
                if (depth > MAX_AST_DEPTH) {
                    problems.add("Expression exceeds the " + MAX_AST_DEPTH + " AST depth limit");
                    return null;
                }
                if (!ALLOWED_KINDS.contains(tree.getKind())) {
                    problems.add("Disallowed expression construct: " + tree.getKind());
                    return null;
                }
                return super.scan(tree, unused);
            } finally {
                depth--;
            }
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            String name = node.getName().toString();
            Element element = trees.getElement(getCurrentPath());
            if (!allowedIdentifiers.contains(name) || element == null || element.getKind() != ElementKind.PARAMETER) {
                problems.add("Undeclared or non-slot identifier: " + name);
            }
            return null;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            if (node.getIdentifier().contentEquals("class")) {
                problems.add("Class literals are not allowed in Durable expressions");
                return null;
            }
            Element element = trees.getElement(getCurrentPath());
            if (element == null) {
                problems.add("Unresolved member selection");
            } else if (element.getKind() == ElementKind.FIELD) {
                TypeMirror receiver = trees.getTypeMirror(new TreePath(getCurrentPath(), node.getExpression()));
                if (receiver == null || receiver.getKind() != TypeKind.ARRAY
                        || !node.getIdentifier().contentEquals("length")) {
                    problems.add("Field access is not allowed in Durable expressions: " + node.getIdentifier());
                }
            } else if (element.getKind() != ElementKind.METHOD) {
                problems.add("Unsupported member selection: " + element.getKind());
            }
            return super.visitMemberSelect(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            Element resolved = trees.getElement(getCurrentPath());
            if (!(resolved instanceof ExecutableElement method) || !(node.getMethodSelect() instanceof MemberSelectTree)) {
                problems.add("Only resolved instance methods are allowed in Durable expressions");
                return null;
            }
            if (method.getModifiers().contains(Modifier.STATIC)) {
                problems.add("Static method calls are not allowed in Durable expressions");
                return null;
            }
            TypeElement owner = (TypeElement) method.getEnclosingElement();
            String ownerName = owner.getQualifiedName().toString();
            String methodName = method.getSimpleName().toString();
            if (!safeJdkMethod(ownerName, methodName, node.getArguments().size())
                    && !allowedApplicationAccessorShape(owner, method, node.getArguments().size())) {
                problems.add("Method is not an allowed value accessor: " + ownerName + '#' + methodName);
                return null;
            }
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitBinary(BinaryTree node, Void unused) {
            if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO)
                    && !isNull(node.getLeftOperand()) && !isNull(node.getRightOperand())) {
                TypeMirror left = trees.getTypeMirror(new TreePath(getCurrentPath(), node.getLeftOperand()));
                TypeMirror right = trees.getTypeMirror(new TreePath(getCurrentPath(), node.getRightOperand()));
                if (!isPrimitive(left) || !isPrimitive(right)) {
                    problems.add("Reference ==/!= is allowed only for null checks");
                    return null;
                }
            }
            return super.visitBinary(node, unused);
        }

        private static boolean safeJdkMethod(String owner, String method, int argumentCount) {
            Set<String> methods = SAFE_JDK_INSTANCE_METHODS.get(owner);
            if (methods == null || !methods.contains(method)) {
                return false;
            }
            return argumentCount <= 1;
        }

        private static boolean allowedApplicationAccessorShape(TypeElement owner, ExecutableElement method,
                int argumentCount) {
            if (argumentCount != 0 || method.getParameters().size() != 0
                    || method.getReturnType().getKind() == TypeKind.VOID
                    || method.getEnclosingElement().toString().startsWith("java.")) {
                return false;
            }
            String name = method.getSimpleName().toString();
            if (name.equals("getClass") || name.equals("hashCode") || name.equals("toString")) {
                return false;
            }
            if (owner.getKind() == ElementKind.RECORD) {
                return owner
                    .getRecordComponents()
                    .stream()
                    .map(RecordComponentElement::getSimpleName)
                    .anyMatch(component -> component.contentEquals(name));
            }
            return name.matches("get[A-Z].*") || name.matches("is[A-Z].*");
        }

        private static boolean isPrimitive(TypeMirror type) {
            return type != null && type.getKind().isPrimitive();
        }

        private static boolean isNull(ExpressionTree tree) {
            return tree != null && tree.getKind() == Tree.Kind.NULL_LITERAL;
        }
    }
}
