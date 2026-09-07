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
package com.alibaba.compileflow.durable.runtime.codec;

import com.alibaba.compileflow.durable.runtime.kernel.BranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentBranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.MultiInstanceBranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.MultiInstanceState;
import com.alibaba.compileflow.durable.runtime.kernel.ParallelForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor.FrameDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor.FrameKind;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.ScopeFrame;
import com.alibaba.compileflow.durable.runtime.kernel.WhileFrame;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.state.ProcessStateField;
import com.alibaba.compileflow.durable.runtime.state.ProcessStateSchema;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.type.TypeFactory;

/**
 * Engine-owned typed JSON contract for a single immutable Process Definition.
 *
 * @author yusu
 */
public final class DurableValueSerializer {
    private static final int ABSOLUTE_MAX_BYTES = 4 * 1024 * 1024;
    private static final Set<String> TYPE_ID_NAMES = Set.of("@class", "@type", "$type");
    private final ProcessStateSchema schema;
    private final Map<String, ResumeDescriptor> resumes;
    private final Map<String, JavaType> variableTypes;
    private final Map<String, List<JavaType>> frameTypes;
    private final Map<String, ActionPlan> actions;
    private final Map<String, DurableMachinePlan.Iteration> iterations;
    private final Map<String, DurableMachinePlan.Iteration.ForEach> parallelIterations;
    private final Map<String, JavaType> loopCollectionTypes;
    private final Map<String, JavaType> loopElementTypes;
    private final Map<String, Map<String, JavaType>> actionInputTypes;
    private final ObjectMapper mapper;
    private final Limits limits;
    private final ClassLoader classLoader;

    public DurableValueSerializer(DurableMachinePlan machinePlan) {
        this(machinePlan, Limits.defaults(), contextClassLoader());
    }

    public DurableValueSerializer(DurableMachinePlan machinePlan, Limits limits, ClassLoader classLoader) {
        DurableMachinePlan plan = Objects.requireNonNull(machinePlan, "machinePlan");
        this.schema = plan.stateSchema();
        this.resumes = plan.resumes();
        Map<String, ActionPlan> actionPlans = new LinkedHashMap<>();
        plan.semanticPlan().getNodes().forEach((id, node) -> {
            if (node.operation() instanceof ActionPlan action) {
                actionPlans.put(id, action);
            }
        });
        this.actions = Collections.unmodifiableMap(actionPlans);
        this.iterations = plan.iterations();
        Map<String, DurableMachinePlan.Iteration.ForEach> multiInstances = new LinkedHashMap<>();
        this.iterations.forEach((id, iteration) -> {
            if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop
                    && loop.execution() == IterationPlan.Execution.PARALLEL) {
                multiInstances.put(id, loop);
            }
        });
        this.parallelIterations = Collections.unmodifiableMap(multiInstances);
        this.limits = Objects.requireNonNull(limits, "limits");
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        this.classLoader = loader;
        TypeFactory types = TypeFactory.createDefaultInstance().withClassLoader(loader);
        JsonFactory factory = JsonFactory
            .builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS)
            .streamReadConstraints(StreamReadConstraints
                .builder()
                .maxDocumentLength(limits.maxBytes())
                .maxNestingDepth(limits.maxDepth())
                .maxTokenCount(limits.maxTokens())
                .maxStringLength(limits.maxStringBytes())
                .maxNameLength(limits.maxNameBytes())
                .maxNumberLength(limits.maxNumberCharacters())
                .build())
            .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(limits.maxDepth()).build())
            .build();
        this.mapper = JsonMapper
            .builder(factory)
            .typeFactory(types)
            .disable(MapperFeature.USE_ANNOTATIONS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
        this.variableTypes = resolveVariableTypes(types);
        LoopTypes loopTypes = resolveLoopTypes(types);
        this.loopCollectionTypes = loopTypes.collections();
        this.loopElementTypes = loopTypes.elements();
        validateParallelLoopTypes(types);
        this.frameTypes = resolveFrameTypes(types);
        this.actionInputTypes = resolveActionInputTypes(types);
    }

    public byte[] encodeEffectInput(String elementId, Map<String, Object> input) {
        requireEffectAction(elementId);
        Map<String, JavaType> types = actionInputTypes.get(elementId);
        Map<String, Object> values = Objects.requireNonNull(input, "input");
        if (!values.keySet().equals(types.keySet())) {
            throw invalid("Effect input does not match the exact Action contract", null);
        }
        ObjectNode root = mapper.createObjectNode();
        for (Map.Entry<String, JavaType> field : types.entrySet()) {
            root.set(field.getKey(),
                    toTree(values.get(field.getKey()), field.getValue(), "Effect input " + field.getKey()));
        }
        return writeBounded(root, "Effect input");
    }

    public Map<String, Object> decodeEffectInput(String elementId, byte[] source) {
        requireEffectAction(elementId);
        return decodeTypedObject(source, actionInputTypes.get(elementId), "Effect input");
    }

    /**
     * Creates an exact-type detached input graph before application Action code runs.
     * Mutable Process values must never be shared with an Action that can mutate them
     * without returning an explicit state update.
     */
    public Map<String, Object> detachActionInput(String elementId, Map<String, Object> input) {
        String actionId = Objects.requireNonNull(elementId, "elementId");
        if (!actions.containsKey(actionId)) {
            throw invalid("Action is absent from the exact Process Definition: " + actionId, null);
        }
        Map<String, JavaType> contract = actionInputTypes.get(actionId);
        Map<String, Object> values = Objects.requireNonNull(input, "input");
        if (!values.keySet().equals(contract.keySet())) {
            throw invalid("Action input does not match the exact Action contract", null);
        }
        ObjectNode root = mapper.createObjectNode();
        for (Map.Entry<String, JavaType> field : contract.entrySet()) {
            root.set(field.getKey(),
                    toTree(values.get(field.getKey()), field.getValue(), "Action input " + field.getKey()));
        }
        LinkedHashMap<String, Object> detached = new LinkedHashMap<>();
        for (Map.Entry<String, JavaType> field : contract.entrySet()) {
            detached.put(field.getKey(),
                    fromTree(root.get(field.getKey()), field.getValue(), "Action input " + field.getKey()));
        }
        return Collections.unmodifiableMap(detached);
    }

    /**
     * Creates a fully typed detached state/scope graph for an application callback.
     */
    public ContinuationSnapshot detachSnapshot(ResumePoint resumePoint, Map<String, Object> state,
            List<ScopeFrame> frames) {
        ContinuationSnapshot snapshot = new ContinuationSnapshot(resumePoint, state, frames);
        return decode(encode(snapshot));
    }

    /**
     * Encodes one Wait result as a typed partial update of Process-owned state.
     *
     * <p>A Wait result is committed before the next Machine Turn runs, so accepting an
     * undeclared or ill-typed value here would create an unrecoverable poison Run.
     * The exact immutable Process Definition is therefore the authority for every
     * persisted Wait value.</p>
     */
    public byte[] encodeWaitPayload(Map<String, ?> payload) {
        Map<String, ?> values = Objects.requireNonNull(payload, "payload");
        if (!variableTypes.keySet().containsAll(values.keySet())) {
            throw invalid("Wait payload contains an undeclared Process variable", null);
        }
        ObjectNode root = mapper.createObjectNode();
        for (String name : new TreeSet<>(values.keySet())) {
            ProcessStateField field = schema.requireField(name);
            Object value = values.get(name);
            if (value == null && !field.nullable()) {
                throw invalid("Process variable is not nullable: " + name, null);
            }
            root.set(name, toTree(value, variableTypes.get(name), "Wait payload " + name));
        }
        return writeBounded(root, "Wait payload");
    }

    /**
     * Decodes a previously validated typed Wait state update.
     */
    public Map<String, Object> decodeWaitPayload(byte[] source) {
        return decodeProcessValues(source, "Wait payload");
    }

    private Map<String, Object> decodeProcessValues(byte[] source, String location) {
        byte[] bytes = Objects.requireNonNull(source, "source").clone();
        if (bytes.length == 0 || bytes.length > limits.maxBytes()) {
            throw invalid(location + " byte length is invalid", null);
        }
        try {
            JsonNode parsed = mapper.readTree(bytes);
            if (parsed == null || !parsed.isObject()) {
                throw invalid(location + " root must be an object", null);
            }
            ObjectNode root = parsed.asObject();
            rejectUnsafeTree(root);
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> property : root.properties()) {
                String name = property.getKey();
                ProcessStateField field = schema.requireField(name);
                Object value = fromTree(property.getValue(), variableTypes.get(name), location + " " + name);
                if (value == null && !field.nullable()) {
                    throw invalid("Process variable is not nullable: " + name, null);
                }
                result.put(name, value);
            }
            return Collections.unmodifiableMap(result);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw invalid(location + " decoding failed", failure);
        } catch (Exception failure) {
            throw invalid(location + " decoding failed", failure);
        }
    }

    public byte[] encodeEffectOutput(String elementId, Map<String, ?> output) {
        ActionPlan action = requireEffectAction(elementId);
        Map<String, ?> values = Objects.requireNonNull(output, "output");
        Map<String, JavaType> contract = action.output() == null
                ? Map.of()
                : Map.of(action.output().target(), variableTypes.get(action.output().target()));
        if (!values.keySet().equals(contract.keySet())) {
            throw invalid("Effect output does not match the exact Action contract", null);
        }
        ObjectNode root = mapper.createObjectNode();
        for (Map.Entry<String, JavaType> field : contract.entrySet()) {
            requireNullableProcessValue(field.getKey(), values.get(field.getKey()));
            root.set(field.getKey(),
                    toTree(values.get(field.getKey()), field.getValue(), "Effect output " + field.getKey()));
        }
        return writeBounded(root, "Effect output");
    }

    public byte[] encodeProcessResult(Map<String, Object> output) {
        Map<String, Object> values = Objects.requireNonNull(output, "output");
        if (!variableTypes.keySet().containsAll(values.keySet())) {
            throw invalid("Process result contains an undeclared variable", null);
        }
        ObjectNode root = mapper.createObjectNode();
        for (String name : new TreeSet<>(values.keySet())) {
            requireNullableProcessValue(name, values.get(name));
            root.set(name, toTree(values.get(name), variableTypes.get(name), "Process result " + name));
        }
        return writeBounded(root, "Process result");
    }

    /**
     * Decodes a terminal Process result against the exact immutable state schema.
     */
    public Map<String, Object> decodeProcessResult(byte[] source) {
        return decodeProcessValues(source, "Process result");
    }

    public Map<String, Object> decodeEffectOutput(String elementId, byte[] source) {
        ActionPlan action = requireEffectAction(elementId);
        Map<String, JavaType> contract = action.output() == null
                ? Map.of()
                : Map.of(action.output().target(), variableTypes.get(action.output().target()));
        Map<String, Object> result = decodeTypedObject(source, contract, "Effect output");
        result.forEach(this::requireNullableProcessValue);
        return result;
    }

    public byte[] encode(ContinuationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode encodedFrontiers = root.putArray("frontiers");
            Map<FrontierId, FrontierSnapshot> byId = new LinkedHashMap<>();
            snapshot
                .frontiers()
                .forEach(frontier -> byId.put(frontier.frontierId(), frontier));
            for (FrontierSnapshot frontier : snapshot.frontiers()) {
                Map<String, Object> baseline = iterationBaseline(frontier, byId);
                encodedFrontiers.add(encodeFrontier(frontier, baseline));
            }
            rejectUnsafeTree(root);
            byte[] encoded = mapper.writeValueAsBytes(root);
            if (encoded.length == 0 || encoded.length > limits.maxBytes()) {
                throw invalid("ContinuationSnapshot exceeds the byte limit", null);
            }
            return encoded;
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw invalid("ContinuationSnapshot encoding failed", failure);
        } catch (Exception failure) {
            throw invalid("ContinuationSnapshot encoding failed", failure);
        }
    }

    public ContinuationSnapshot decode(byte[] source) {
        byte[] encoded = Objects.requireNonNull(source, "source").clone();
        if (encoded.length == 0 || encoded.length > limits.maxBytes()) {
            throw invalid("ContinuationSnapshot byte length is invalid", null);
        }
        try {
            JsonNode parsed = mapper.readTree(encoded);
            if (parsed == null || !parsed.isObject()) {
                throw invalid("ContinuationSnapshot root must be an object", null);
            }
            ObjectNode root = parsed.asObject();
            requireExactProperties(root, Set.of("frontiers"), "continuation");
            rejectUnsafeTree(root);
            JsonNode frontiersNode = root.get("frontiers");
            if (frontiersNode == null || !frontiersNode.isArray() || frontiersNode.isEmpty()) {
                throw invalid("ContinuationSnapshot frontiers must be a non-empty array", null);
            }
            if (frontiersNode.size() > 256) {
                throw invalid("ContinuationSnapshot contains too many frontiers", null);
            }
            List<JsonNode> encodedFrontiers = new ArrayList<>(frontiersNode.size());
            frontiersNode.forEach(encodedFrontiers::add);
            List<JsonNode> dependencyOrder =
                    encodedFrontiers
                .stream()
                .sorted(Comparator.comparingInt(DurableValueSerializer::branchDepth))
                .toList();
            Map<FrontierId, FrontierSnapshot> decoded = new LinkedHashMap<>();
            Map<JsonNode, ConcurrentBranchFrame> sharedBranches = new LinkedHashMap<>();
            for (JsonNode frontier : dependencyOrder) {
                FrontierSnapshot restored = decodeFrontier(frontier, decoded, sharedBranches);
                if (decoded.putIfAbsent(restored.frontierId(), restored) != null) {
                    throw invalid("ContinuationSnapshot contains duplicate frontier identities", null);
                }
            }
            List<FrontierSnapshot> frontiers = encodedFrontiers
                .stream()
                .map(frontier -> decoded.get(new FrontierId(text(frontier.asObject(), "id"))))
                .toList();
            return new ContinuationSnapshot(frontiers);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw invalid("ContinuationSnapshot decoding failed", failure);
        } catch (Exception failure) {
            throw invalid("ContinuationSnapshot decoding failed", failure);
        }
    }

    private static int branchDepth(JsonNode frontier) {
        if (frontier == null || !frontier.isObject()) {
            return Integer.MAX_VALUE;
        }
        JsonNode branches = frontier.get("branches");
        return branches != null && branches.isArray() ? branches.size() : Integer.MAX_VALUE;
    }

    private ObjectNode encodeFrontier(FrontierSnapshot frontier, Map<String, Object> iterationBaseline) {
        ResumeDescriptor resume = requireResume(frontier.resumePoint());
        requireFrames(frontier.scopeFrames(), resume);
        ObjectNode encoded = mapper.createObjectNode();
        encoded.put("id", frontier.frontierId().value());
        encoded.put("resumeKind", frontier.resumePoint().kind().name());
        if (frontier.resumePoint().elementId() == null) {
            encoded.putNull("resumeElement");
        } else {
            encoded.put("resumeElement", frontier.resumePoint().elementId());
        }
        encoded.set("variables",
                iterationBaseline != null
                ? encodeProcessVariableDelta(frontier.variables(), iterationBaseline)
                : frontier.resumePoint().isStart()
                ? encodeInitialProcessVariables(frontier.variables(), "initial frontier variables")
                : encodeProcessVariables(frontier.variables(), "frontier variables"));
        ArrayNode encodedScopes = encoded.putArray("scopes");
        for (int index = 0; index < frontier.scopeFrames().size(); index++) {
            encodedScopes.add(
                    encodeFrame(frontier.scopeFrames().get(index), resume.expectedFramePath().get(index),
                            frameTypes.get(frontier.resumePoint().key()).get(index)));
        }
        ArrayNode encodedBranches = encoded.putArray("branches");
        for (BranchFrame branch : frontier.branchFrames()) {
            ObjectNode encodedBranch = encodedBranches.addObject();
            if (branch instanceof MultiInstanceBranchFrame iteration) {
                encodedBranch.put("kind", "MULTI_INSTANCE");
                encodedBranch.put("parentId", iteration.parentFrontierId().value());
                encodedBranch.put("loopId", iteration.loopId());
                encodedBranch.put("index", iteration.index());
                continue;
            }
            ConcurrentBranchFrame concurrent = (ConcurrentBranchFrame) branch;
            encodedBranch.put("kind", "CONCURRENT");
            encodedBranch.put("parentId", concurrent.parentFrontierId().value());
            encodedBranch.put("splitId", concurrent.splitId());
            encodedBranch.put("joinId", concurrent.joinId());
            encodedBranch.put("branchOrdinal", concurrent.activation().ordinal());
            encodedBranch.put("branchStartId", concurrent.activation().branchStartId());
            encodedBranch.set("baseline", encodeProcessVariables(concurrent.baselineVariables(), "branch baseline"));
            ArrayNode selected = encodedBranch.putArray("selected");
            concurrent
                .selectedActivations()
                .forEach(activation -> {
                    ObjectNode encodedActivation = selected.addObject();
                    encodedActivation.put("ordinal", activation.ordinal());
                    encodedActivation.put("branchStartId", activation.branchStartId());
                });
            ArrayNode writes = encodedBranch.putArray("writes");
            concurrent.writtenVariables().forEach(writes::add);
        }
        MultiInstanceState controller = frontier.multiInstanceController();
        if (controller == null) {
            encoded.putNull("multiInstance");
        } else {
            DurableMachinePlan.Iteration.ForEach loop = requireParallelIteration(controller.loopId());
            ObjectNode multi = encoded.putObject("multiInstance");
            multi.put("loopId", controller.loopId());
            multi.put("nextIndex", controller.nextIndex());
            multi.put("totalIterations", controller.totalIterations());
            ArrayNode results = multi.putArray("results");
            JavaType resultType = variableTypes.get(loop.outputSourceVariable());
            for (Object result : controller.resultsForEncoding()) {
                results.add(
                        resultType == null ? mapper.nullNode() : toTree(result, resultType, "parallel foreach result"));
            }
            ArrayNode active = multi.putArray("active");
            controller.activeIndices().forEach(active::add);
            ArrayNode completed = multi.putArray("completed");
            controller.completedIndices().forEach(completed::add);
        }
        return encoded;
    }

    private FrontierSnapshot decodeFrontier(JsonNode source, Map<FrontierId, FrontierSnapshot> decodedFrontiers,
            Map<JsonNode, ConcurrentBranchFrame> sharedBranches) {
        if (source == null || !source.isObject()) {
            throw invalid("Continuation frontier must be an object", null);
        }
        ObjectNode encoded = source.asObject();
        Set<String> expected =
                Set.of("id", "resumeKind", "resumeElement", "variables", "scopes", "branches", "multiInstance");
        Set<String> actual = new LinkedHashSet<>();
        encoded
            .properties()
            .forEach(entry -> actual.add(entry.getKey()));
        if (!actual.equals(expected)) {
            throw invalid("frontier properties do not match the persisted contract", null);
        }
        FrontierId frontierId = new FrontierId(text(encoded, "id"));
        ResumePoint position = decodeResumePoint(encoded);
        ResumeDescriptor resume = requireResume(position);
        List<BranchFrame> branches = decodeBranchFrames(encoded.get("branches"), sharedBranches);
        Map<String, Object> iterationBaseline = iterationBaseline(branches, decodedFrontiers);
        Map<String, Object> variables = iterationBaseline != null
                ? decodeProcessVariableDelta(encoded.get("variables"), iterationBaseline)
                : position.isStart()
                ? decodeInitialProcessVariables(encoded.get("variables"), "initial frontier variables")
                : decodeProcessVariables(encoded.get("variables"), "frontier variables");
        JsonNode scopesNode = encoded.get("scopes");
        if (scopesNode == null || !scopesNode.isArray()) {
            throw invalid("Continuation frontier scopes must be an array", null);
        }
        ArrayNode sourceScopes = scopesNode.asArray();
        int expectedFrames = resume == null ? 0 : resume.expectedFramePath().size();
        if (sourceScopes.size() != expectedFrames) {
            throw invalid("Scope-frame depth does not match ResumePoint", null);
        }
        List<ScopeFrame> scopes = new ArrayList<>(expectedFrames);
        for (int index = 0; index < expectedFrames; index++) {
            scopes.add(
                    decodeFrame(sourceScopes.get(index), resume.expectedFramePath().get(index),
                            frameTypes.get(position.key()).get(index)));
        }
        MultiInstanceState controller = null;
        JsonNode multiNode = encoded.get("multiInstance");
        if (multiNode != null && !multiNode.isNull()) {
            controller = decodeMultiInstance(multiNode, position);
        }
        return new FrontierSnapshot(frontierId, position, variables, scopes, branches, controller);
    }

    private List<BranchFrame> decodeBranchFrames(JsonNode branchesNode,
            Map<JsonNode, ConcurrentBranchFrame> sharedBranches) {
        if (branchesNode == null || !branchesNode.isArray() || branchesNode.size() > 64) {
            throw invalid("Continuation frontier branch ancestry is invalid", null);
        }
        List<BranchFrame> branches = new ArrayList<>(branchesNode.size());
        for (JsonNode branchNode : branchesNode) {
            if (branchNode == null || !branchNode.isObject()) {
                throw invalid("Concurrent branch frame must be an object", null);
            }
            ObjectNode branch = branchNode.asObject();
            String branchKind = text(branch, "kind");
            if ("MULTI_INSTANCE".equals(branchKind)) {
                requireExactProperties(branch, Set.of("kind", "parentId", "loopId", "index"),
                        "multi-instance branch frame");
                branches.add(
                        new MultiInstanceBranchFrame(new FrontierId(text(branch, "parentId")), text(branch, "loopId"),
                                integer(branch, "index")));
                continue;
            }
            if (!"CONCURRENT".equals(branchKind)) {
                throw invalid("Branch frame kind is not supported", null);
            }
            // Restore shared ancestry by its complete persisted value. Application values such
            // as byte arrays need not implement Java value equality after independent decoding.
            ConcurrentBranchFrame shared = sharedBranches.get(branchNode);
            if (shared != null) {
                branches.add(shared);
                continue;
            }
            requireExactProperties(branch,
                    Set.of("kind", "parentId", "splitId", "joinId", "branchOrdinal", "branchStartId", "baseline",
                            "selected", "writes"), "branch frame");
            JsonNode selectedNode = branch.get("selected");
            if (selectedNode == null || !selectedNode.isArray() || selectedNode.isEmpty() || selectedNode.size() > 256) {
                throw invalid("Concurrent selected branches must be a bounded non-empty array", null);
            }
            LinkedHashSet<BranchActivation> selected = new LinkedHashSet<>();
            for (JsonNode selectedBranch : selectedNode) {
                if (!selectedBranch.isObject()) {
                    throw invalid("Concurrent selected branches must be activation objects", null);
                }
                ObjectNode encodedActivation = selectedBranch.asObject();
                requireExactProperties(encodedActivation, Set.of("ordinal", "branchStartId"),
                        "selected branch activation");
                BranchActivation activation =
                        new BranchActivation(integer(encodedActivation, "ordinal"),
                                text(encodedActivation, "branchStartId"));
                if (!selected.add(activation)) {
                    throw invalid("Concurrent selected branch activations must be unique", null);
                }
            }
            JsonNode writesNode = branch.get("writes");
            if (writesNode == null || !writesNode.isArray() || writesNode.size() > 256) {
                throw invalid("Concurrent branch writes must be a bounded array", null);
            }
            LinkedHashSet<String> writes = new LinkedHashSet<>();
            for (JsonNode write : writesNode) {
                if (!write.isString() || !writes.add(write.stringValue())) {
                    throw invalid("Concurrent branch writes must be unique text values", null);
                }
            }
            ConcurrentBranchFrame restored = new ConcurrentBranchFrame(new FrontierId(text(branch, "parentId")),
                    text(branch, "splitId"), text(branch, "joinId"),
                    new BranchActivation(integer(branch, "branchOrdinal"), text(branch, "branchStartId")),
                    decodeProcessVariables(branch.get("baseline"), "branch baseline"), selected, writes);
            sharedBranches.put(branchNode, restored);
            branches.add(restored);
        }
        return List.copyOf(branches);
    }

    private MultiInstanceState decodeMultiInstance(JsonNode source, ResumePoint position) {
        if (!source.isObject()) {
            throw invalid("Parallel foreach controller must be an object or null", null);
        }
        ObjectNode multi = source.asObject();
        requireExactProperties(multi, Set.of("loopId", "nextIndex", "totalIterations", "results", "active", "completed"),
                "parallel foreach controller");
        String loopId = text(multi, "loopId");
        DurableMachinePlan.Iteration.ForEach loop = requireParallelIteration(loopId);
        if (!position.equals(ResumePoint.beforeElement(loopId))) {
            throw invalid("Parallel foreach controller coordinate does not match the Process Definition", null);
        }
        int totalIterations = integer(multi, "totalIterations");
        JsonNode resultsNode = multi.get("results");
        int expectedResults = loop.outputSourceVariable() == null ? 0 : totalIterations;
        if (totalIterations <= 0 || totalIterations > limits.maxCollectionEntries() || !resultsNode.isArray()
                || resultsNode.size() != expectedResults) {
            throw invalid("Parallel foreach iteration count/result slots are invalid", null);
        }
        List<Object> results = new ArrayList<>(resultsNode.size());
        JavaType resultType = variableTypes.get(loop.outputSourceVariable());
        for (JsonNode result : resultsNode) {
            results.add(resultType == null ? null : fromTree(result, resultType, "parallel foreach result"));
        }
        return new MultiInstanceState(loopId, totalIterations, results,
                integerSet(multi.get("active"), "parallel active indices"),
                integerSet(multi.get("completed"), "parallel completed indices"), integer(multi, "nextIndex"));
    }

    private Set<Integer> integerSet(JsonNode source, String location) {
        if (source == null || !source.isArray() || source.size() > limits.maxCollectionEntries()) {
            throw invalid(location + " must be a bounded array", null);
        }
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (JsonNode value : source) {
            if (!value.isIntegralNumber() || !value.canConvertToInt() || !values.add(value.intValue())) {
                throw invalid(location + " must contain unique integers", null);
            }
        }
        return values;
    }

    private DurableMachinePlan.Iteration.ForEach requireParallelIteration(String loopId) {
        DurableMachinePlan.Iteration.ForEach loop = parallelIterations.get(loopId);
        if (loop == null) {
            throw invalid("Parallel foreach loop is absent from the exact Process Definition: " + loopId, null);
        }
        return loop;
    }

    private DurableMachinePlan.Iteration.ForEach requireSequentialIteration(String loopId) {
        DurableMachinePlan.Iteration iteration = iterations.get(loopId);
        if (!(iteration instanceof DurableMachinePlan.Iteration.ForEach loop)
                || loop.execution() != IterationPlan.Execution.SEQUENTIAL) {
            throw invalid("Sequential foreach loop is absent from the exact Process Definition: " + loopId, null);
        }
        return loop;
    }

    private ResumePoint decodeResumePoint(ObjectNode root) {
        ResumePoint.Kind kind;
        try {
            kind = ResumePoint.Kind.valueOf(text(root, "resumeKind"));
        } catch (IllegalArgumentException invalidKind) {
            throw invalid("Continuation resumeKind is unsupported", invalidKind);
        }
        JsonNode elementNode = root.get("resumeElement");
        String elementId = null;
        if (elementNode != null && !elementNode.isNull()) {
            if (!elementNode.isString() || elementNode.stringValue().isBlank()) {
                throw invalid("Continuation resumeElement must be null or non-blank text", null);
            }
            elementId = elementNode.stringValue();
        }
        return new ResumePoint(kind, elementId);
    }

    private ObjectNode encodeProcessVariables(Map<String, Object> source, String location) {
        Map<String, Object> variables = schema.validate(source);
        ObjectNode encoded = mapper.createObjectNode();
        for (ProcessStateField field : schema.fields()) {
            Object value = variables.get(field.name());
            if (value == null && !field.nullable()) {
                throw invalid("Process variable is not nullable: " + field.name(), null);
            }
            encoded.set(field.name(), toTree(value, variableTypes.get(field.name()), location + " " + field.name()));
        }
        return encoded;
    }

    private ObjectNode encodeProcessVariableDelta(Map<String, Object> source, Map<String, Object> baseline) {
        Map<String, Object> current = schema.validate(source);
        Map<String, Object> parent = schema.validate(baseline);
        if (!current.keySet().equals(parent.keySet())) {
            throw invalid("Parallel iteration state does not match its controller state schema", null);
        }
        ObjectNode encoded = mapper.createObjectNode();
        for (ProcessStateField field : schema.fields()) {
            Object value = current.get(field.name());
            if (!Objects.equals(value, parent.get(field.name()))) {
                if (value == null && !field.nullable()) {
                    throw invalid("Process variable is not nullable: " + field.name(), null);
                }
                encoded.set(field.name(),
                        toTree(value, variableTypes.get(field.name()), "parallel iteration variable " + field.name()));
            }
        }
        return encoded;
    }

    /**
     * Encodes caller-supplied Start values without inventing values for omitted fields.
     *
     * <p>Absence and an explicit {@code null} have different Process semantics: an absent
     * value receives the definition-owned default when the first Machine Turn starts, while
     * an explicit {@code null} overrides that default. Later continuations are always complete.</p>
     */
    private ObjectNode encodeInitialProcessVariables(Map<String, Object> source, String location) {
        Map<String, Object> variables = schema.validateStartInput(source);
        ObjectNode encoded = mapper.createObjectNode();
        for (ProcessStateField field : schema.fields()) {
            if (!variables.containsKey(field.name())) {
                continue;
            }
            Object value = variables.get(field.name());
            if (value == null && !field.nullable()) {
                throw invalid("Process variable is not nullable: " + field.name(), null);
            }
            encoded.set(field.name(), toTree(value, variableTypes.get(field.name()), location + " " + field.name()));
        }
        return encoded;
    }

    /**
     * Converts a called-Process input map according to the called Process's parameter contract.
     */
    public Map<String, Object> normalizeProcessCallInput(ProcessCallPlan processCall, Map<String, Object> source) {
        ProcessCallPlan call = Objects.requireNonNull(processCall, "processCall");
        Map<String, Object> input = schema.validateStartInput(Objects.requireNonNull(source, "source"));
        Set<String> expectedSources = call
            .inputs()
            .stream()
            .filter(binding -> binding.sourceExpression() != null)
            .map(ProcessCallPlan.Input::target)
            .collect(Collectors.toSet());
        if (!input.keySet().equals(expectedSources)) {
            throw invalid("Process call source input does not match the admitted mapping", null);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        input.forEach((name, value) -> {
            JavaType type = variableTypes.get(name);
            Object converted = value == null ? null : DataTypes.transfer(value, boxed(type.getRawClass()));
            normalized.put(name, converted);
        });
        call
            .inputs()
            .stream()
            .filter(binding -> binding.defaultValue() != null)
            .forEach(binding -> {
                JavaType type = variableTypes.get(binding.target());
                normalized.put(binding.target(), DataTypes.parseDefaultValue(type.getRawClass(), binding.defaultValue()));
            });
        return Collections.unmodifiableMap(normalized);
    }

    private Map<String, Object> decodeProcessVariables(JsonNode source, String location) {
        if (source == null || !source.isObject()) {
            throw invalid(location + " must be an object", null);
        }
        ObjectNode encoded = source.asObject();
        requireExactProperties(encoded,
                schema.fields().stream().map(ProcessStateField::name).collect(Collectors.toSet()), location);
        Map<String, Object> variables = new LinkedHashMap<>();
        for (ProcessStateField field : schema.fields()) {
            Object decoded =
                    fromTree(encoded.get(field.name()), variableTypes.get(field.name()), location + " " + field.name());
            if (decoded == null && !field.nullable()) {
                throw invalid("Process variable is not nullable: " + field.name(), null);
            }
            variables.put(field.name(), decoded);
        }
        return variables;
    }

    private Map<String, Object> decodeProcessVariableDelta(JsonNode source, Map<String, Object> baseline) {
        if (source == null || !source.isObject()) {
            throw invalid("Parallel iteration variables must be an object", null);
        }
        Map<String, Object> variables = new LinkedHashMap<>(schema.validate(baseline));
        for (Map.Entry<String, JsonNode> property : source.asObject().properties()) {
            String name = property.getKey();
            ProcessStateField field = schema.requireField(name);
            Object decoded =
                    fromTree(property.getValue(), variableTypes.get(name), "parallel iteration variable " + name);
            if (decoded == null && !field.nullable()) {
                throw invalid("Process variable is not nullable: " + name, null);
            }
            variables.put(name, decoded);
        }
        return schema.validate(variables);
    }

    private Map<String, Object> iterationBaseline(FrontierSnapshot frontier,
            Map<FrontierId, FrontierSnapshot> frontiers) {
        if (frontier.branchFrames().isEmpty()) {
            return null;
        }
        BranchFrame top = frontier.branchFrames().get(frontier.branchFrames().size() - 1);
        if (!(top instanceof MultiInstanceBranchFrame iteration)) {
            return null;
        }
        return requireIterationController(iteration, frontiers).variables();
    }

    private Map<String, Object> iterationBaseline(List<BranchFrame> branches,
            Map<FrontierId, FrontierSnapshot> frontiers) {
        if (branches.isEmpty()) {
            return null;
        }
        BranchFrame top = branches.get(branches.size() - 1);
        if (!(top instanceof MultiInstanceBranchFrame iteration)) {
            return null;
        }
        return requireIterationController(iteration, frontiers).variables();
    }

    private FrontierSnapshot requireIterationController(MultiInstanceBranchFrame iteration,
            Map<FrontierId, FrontierSnapshot> frontiers) {
        FrontierSnapshot parent = frontiers.get(iteration.parentFrontierId());
        MultiInstanceState controller = parent == null ? null : parent.multiInstanceController();
        if (controller == null || !controller.loopId().equals(iteration.loopId())
                || !controller.activeIndices().contains(iteration.index())) {
            throw invalid("Parallel iteration does not reference an active controller in this continuation", null);
        }
        return parent;
    }

    private Map<String, Object> decodeInitialProcessVariables(JsonNode source, String location) {
        if (source == null || !source.isObject()) {
            throw invalid(location + " must be an object", null);
        }
        ObjectNode encoded = source.asObject();
        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> property : encoded.properties()) {
            String name = property.getKey();
            ProcessStateField field = schema.requireField(name);
            if (!field.startInput()) {
                throw invalid("Process variable is not a Start input: " + name, null);
            }
            Object decoded = fromTree(property.getValue(), variableTypes.get(name), location + " " + name);
            if (decoded == null && !field.nullable()) {
                throw invalid("Process variable is not nullable: " + name, null);
            }
            variables.put(name, decoded);
        }
        return variables;
    }

    private ObjectNode encodeFrame(ScopeFrame frame, FrameDescriptor descriptor, JavaType itemType) {
        ObjectNode encoded = mapper.createObjectNode();
        encoded.put("kind", descriptor.kind().name());
        encoded.put("loopId", descriptor.loopId());
        encoded.put("position", frame.position());
        if (frame instanceof ForEachFrame forEach) {
            DurableMachinePlan.Iteration.ForEach loop = requireSequentialIteration(descriptor.loopId());
            List<Object> completedResults = forEach.completedResults();
            int expectedResults = loop.outputSourceVariable() == null ? 0 : forEach.position();
            if (completedResults.size() != expectedResults) {
                throw invalid("Foreach completed results do not match its position", null);
            }
            ArrayNode snapshot = encoded.putArray("snapshot");
            for (Object item : forEach.snapshotForEncoding()) {
                snapshot.add(toTree(item, itemType, "foreach item " + descriptor.loopId()));
            }
            if (loop.outputSourceVariable() != null) {
                JavaType resultType = variableTypes.get(loop.outputSourceVariable());
                ArrayNode results = encoded.putArray("results");
                for (Object result : completedResults) {
                    results.add(toTree(result, resultType, "foreach result " + descriptor.loopId()));
                }
            }
        } else if (frame instanceof ParallelForEachFrame parallel) {
            encoded.set("item",
                    toTree(parallel.currentValue(), itemType, "parallel foreach item " + descriptor.loopId()));
        }
        return encoded;
    }

    private ScopeFrame decodeFrame(JsonNode source, FrameDescriptor descriptor, JavaType itemType) {
        if (source == null || !source.isObject()) {
            throw invalid("Scope frame must be an object", null);
        }
        ObjectNode frame = source.asObject();
        DurableMachinePlan.Iteration.ForEach sequential =
                descriptor.kind() == FrameKind.FOR_EACH ? requireSequentialIteration(descriptor.loopId()) : null;
        Set<String> properties = switch (descriptor.kind()) {
            case FOR_EACH -> sequential.outputSourceVariable() == null
                    ? Set.of("kind", "loopId", "position", "snapshot")
                    : Set.of("kind", "loopId", "position", "snapshot", "results");
            case PARALLEL_FOR_EACH -> Set.of("kind", "loopId", "position", "item");
            case WHILE -> Set.of("kind", "loopId", "position");
        };
        requireExactProperties(frame, properties, "scope frame");
        if (!descriptor.kind().name().equals(text(frame, "kind")) || !descriptor
            .loopId()
            .equals(text(frame, "loopId"))) {
            throw invalid("Scope frame does not match Process Definition", null);
        }
        int position = integer(frame, "position");
        if (descriptor.kind() == FrameKind.WHILE) {
            if (position < 0 || descriptor.maxIterations() != null && position >= descriptor.maxIterations()) {
                throw invalid("While scope position exceeds the Process Definition bound", null);
            }
            return new WhileFrame(descriptor.loopId(), position);
        }
        if (descriptor.kind() == FrameKind.PARALLEL_FOR_EACH) {
            if (position < 0) {
                throw invalid("Parallel foreach position must be non-negative", null);
            }
            return new ParallelForEachFrame(descriptor.loopId(), position,
                    fromTree(frame.get("item"), itemType, "parallel foreach item " + descriptor.loopId()));
        }
        JsonNode snapshot = frame.get("snapshot");
        if (!snapshot.isArray() || snapshot.size() > limits.maxCollectionEntries()) {
            throw invalid("Foreach snapshot is invalid", null);
        }
        List<Object> items = new ArrayList<>(snapshot.size());
        for (JsonNode item : snapshot) {
            items.add(fromTree(item, itemType, "foreach item " + descriptor.loopId()));
        }
        if (sequential.outputSourceVariable() == null) {
            return new ForEachFrame(descriptor.loopId(), position, items);
        }
        JsonNode results = frame.get("results");
        if (!results.isArray() || results.size() != position || results.size() > limits.maxCollectionEntries()) {
            throw invalid("Foreach completed results do not match its position", null);
        }
        JavaType resultType = variableTypes.get(sequential.outputSourceVariable());
        List<Object> completedResults = new ArrayList<>(results.size());
        for (JsonNode result : results) {
            completedResults.add(fromTree(result, resultType, "foreach result " + descriptor.loopId()));
        }
        return new ForEachFrame(descriptor.loopId(), position, items, completedResults);
    }

    private JsonNode toTree(Object value, JavaType type, String location) {
        if (value != null && !boxed(type.getRawClass()).isInstance(value)) {
            throw invalid(location + " does not match declared type " + type, null);
        }
        try {
            JsonNode tree = mapper.valueToTree(value);
            rejectUnsafeTree(tree);
            // A typed read proves the value can be reconstructed without type metadata.
            mapper.treeToValue(tree, type);
            return tree;
        } catch (Exception failure) {
            throw invalid(location + " is not a supported typed JSON value", failure);
        }
    }

    private Object fromTree(JsonNode value, JavaType type, String location) {
        try {
            return mapper.treeToValue(value, type);
        } catch (Exception failure) {
            throw invalid(location + " does not match declared type " + type, failure);
        }
    }

    private Map<String, JavaType> resolveVariableTypes(TypeFactory types) {
        Map<String, JavaType> resolved = new LinkedHashMap<>();
        for (ProcessStateField field : schema.fields()) {
            resolved.put(field.name(), requirePortableType(resolveDeclaredType(types, field.declaredType())));
        }
        return Collections.unmodifiableMap(resolved);
    }

    private void validateParallelLoopTypes(TypeFactory types) {
        for (Map.Entry<String, DurableMachinePlan.Iteration.ForEach> entry : parallelIterations.entrySet()) {
            String iterationId = entry.getKey();
            DurableMachinePlan.Iteration.ForEach loop = entry.getValue();
            JavaType inputCollection = loopCollectionTypes.get(iterationId);
            JavaType outputTarget =
                    loop.outputTargetVariable() == null ? null : variableTypes.get(loop.outputTargetVariable());
            JavaType outputSource =
                    loop.outputSourceVariable() == null ? null : variableTypes.get(loop.outputSourceVariable());
            JavaType declaredItem = resolveDeclaredType(types, loop.itemType());
            JavaType iterationItem = loopElementTypes.get(iterationId);
            boolean compatibleItemType = compatibleElementType(declaredItem, iterationItem);
            if (inputCollection == null || inputCollection.getContentType() == null
                    || !List.class.isAssignableFrom(inputCollection.getRawClass()) || iterationItem == null
                    || !compatibleItemType) {
                throw invalid("Parallel foreach input List item type must equal itemType: " + iterationId, null);
            }
            if (loop.outputTargetVariable() != null
                    && (outputTarget == null || outputTarget.getContentType() == null || outputSource == null
                    || !outputTarget.getContentType().equals(outputSource))) {
                throw invalid("Parallel foreach output target item type must equal output source type: " + iterationId,
                        null);
            }
        }
    }

    private static boolean compatibleElementType(JavaType declared, JavaType resolved) {
        return resolved != null
                && (declared.getRawClass() == Object.class
                || declared.getRawClass().equals(resolved.getRawClass())
                && (!declared.hasGenericTypes() || declared.equals(resolved)));
    }

    private Map<String, List<JavaType>> resolveFrameTypes(TypeFactory types) {
        Map<String, List<JavaType>> resolved = new LinkedHashMap<>();
        for (ResumeDescriptor resume : resumes.values()) {
            List<JavaType> fields = new ArrayList<>();
            for (FrameDescriptor frame : resume.expectedFramePath()) {
                JavaType inferred = loopElementTypes.get(frame.loopId());
                fields.add(
                        frame.kind() != FrameKind.WHILE
                        ? inferred != null
                        ? inferred
                        : requirePortableType(resolveDeclaredType(types, frame.itemType()))
                        : null);
            }
            resolved.put(resume.resumePoint().key(), Collections.unmodifiableList(fields));
        }
        return Collections.unmodifiableMap(resolved);
    }

    private LoopTypes resolveLoopTypes(TypeFactory types) {
        Map<String, JavaType> collections = new LinkedHashMap<>();
        Map<String, JavaType> elements = new LinkedHashMap<>();
        for (Map.Entry<String, DurableMachinePlan.Iteration> iteration : iterations.entrySet()) {
            if (!(iteration.getValue() instanceof DurableMachinePlan.Iteration.ForEach loop)) {
                continue;
            }
            String iterationId = iteration.getKey();
            Map<String, JavaType> visible = new LinkedHashMap<>(variableTypes);
            ResumeDescriptor entry = resumes.get(ResumePoint.beforeElement(iterationId).key());
            if (entry != null) {
                for (FrameDescriptor frame : entry.expectedFramePath()) {
                    DurableMachinePlan.Iteration ancestorIteration = iterations.get(frame.loopId());
                    if (!(ancestorIteration instanceof DurableMachinePlan.Iteration.ForEach ancestor)) {
                        continue;
                    }
                    JavaType ancestorElement = resolveLoopElementType(ancestor, visible, types);
                    visible.put(ancestor.itemVariable(), ancestorElement);
                    if (ancestor.indexVariable() != null) {
                        visible.put(ancestor.indexVariable(), types.constructType(Integer.class));
                    }
                }
            }
            JavaType collection = visible.get(loop.collectionVariable());
            collections.put(iterationId, collection);
            elements.put(iterationId, resolveLoopElementType(loop, visible, types));
        }
        return new LoopTypes(Collections.unmodifiableMap(collections), Collections.unmodifiableMap(elements));
    }

    private JavaType resolveLoopElementType(DurableMachinePlan.Iteration.ForEach loop, Map<String, JavaType> visible,
            TypeFactory types) {
        JavaType declared = resolveDeclaredType(types, loop.itemType());
        JavaType collection = visible.get(loop.collectionVariable());
        if (collection == null || collection.getContentType() == null) {
            return requirePortableType(declared);
        }
        JavaType inferred = collection.getContentType();
        return inferred.getRawClass() == Object.class && declared.getRawClass() != Object.class
                ? requirePortableType(declared)
                : requirePortableType(inferred);
    }

    private record LoopTypes(Map<String, JavaType> collections, Map<String, JavaType> elements) {}

    private Map<String, Map<String, JavaType>> resolveActionInputTypes(TypeFactory types) {
        Map<String, Map<String, JavaType>> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, ActionPlan> entry : actions.entrySet()) {
            ActionPlan action = entry.getValue();
            LinkedHashMap<String, JavaType> fields = new LinkedHashMap<>();
            for (ActionPlan.Input input : action.inputs()) {
                if (!effectMetaInput(input)) {
                    fields.put(input.target(), requirePortableType(resolveDeclaredType(types, input.declaredType())));
                }
            }
            resolved.put(entry.getKey(), Collections.unmodifiableMap(fields));
        }
        return Collections.unmodifiableMap(resolved);
    }

    private ActionPlan requireEffectAction(String elementId) {
        ActionPlan action = actions.get(elementId);
        if (action == null || action.execution() != ActionExecution.EFFECT) {
            throw invalid("Effect Action is absent from the exact Process Definition: " + elementId, null);
        }
        return action;
    }

    private static boolean effectMetaInput(ActionPlan.Input input) {
        return input.source() instanceof ActionPlan.InputSource.EffectId;
    }

    private void requireNullableProcessValue(String name, Object value) {
        ProcessStateField field = schema.requireField(name);
        if (value == null && !field.nullable()) {
            throw invalid("Process variable is not nullable: " + name, null);
        }
    }

    private Map<String, Object> decodeTypedObject(byte[] source, Map<String, JavaType> contract, String location) {
        byte[] bytes = Objects.requireNonNull(source, "source").clone();
        if (bytes.length == 0 || bytes.length > limits.maxBytes()) {
            throw invalid(location + " byte length is invalid", null);
        }
        try {
            JsonNode parsed = mapper.readTree(bytes);
            if (parsed == null || !parsed.isObject()) {
                throw invalid(location + " root must be an object", null);
            }
            ObjectNode root = parsed.asObject();
            requireExactProperties(root, contract.keySet(), location);
            rejectUnsafeTree(root);
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, JavaType> field : contract.entrySet()) {
                result.put(field.getKey(),
                        fromTree(root.get(field.getKey()), field.getValue(), location + ' ' + field.getKey()));
            }
            return Collections.unmodifiableMap(result);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw invalid(location + " decoding failed", failure);
        } catch (Exception failure) {
            throw invalid(location + " decoding failed", failure);
        }
    }

    private byte[] writeBounded(JsonNode value, String location) {
        try {
            rejectUnsafeTree(value);
            byte[] encoded = mapper.writeValueAsBytes(value);
            if (encoded.length == 0 || encoded.length > limits.maxBytes()) {
                throw invalid(location + " exceeds the byte limit", null);
            }
            return encoded;
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw invalid(location + " encoding failed", failure);
        } catch (Exception failure) {
            throw invalid(location + " encoding failed", failure);
        }
    }

    private JavaType resolveDeclaredType(TypeFactory types, String declaration) {
        Class<?> raw = DataTypes.getJavaClass(declaration, classLoader);
        int dimensions = 0;
        while (raw.isArray()) {
            dimensions++;
            raw = raw.getComponentType();
        }
        JavaType[] arguments = DataTypes
            .getTypeArguments(declaration)
            .stream()
            .map(argument -> resolveDeclaredType(types, argument))
            .toArray(JavaType[]::new);
        JavaType resolved =
                arguments.length == 0 ? types.constructType(raw) : types.constructParametricType(raw, arguments);
        for (int dimension = 0; dimension < dimensions; dimension++) {
            resolved = types.constructArrayType(resolved);
        }
        return resolved;
    }

    private JavaType requirePortableType(JavaType type) {
        if (type == null || type.isJavaLangObject()) {
            throw invalid("Object is not a Durable Process variable type", null);
        }
        Class<?> raw = type.getRawClass();
        if (raw.isPrimitive()) {
            return type;
        }
        if (type.isMapLikeType()) {
            if (type.getKeyType() == null || type.getKeyType().getRawClass() != String.class
                    || type.getContentType() == null || type.getContentType().isJavaLangObject()) {
                throw invalid("Durable Map types require String keys and a concrete value type", null);
            }
            requirePortableType(type.getContentType());
            return type;
        }
        if (type.isCollectionLikeType()) {
            if (!List.class.isAssignableFrom(raw) && raw != Collection.class) {
                throw invalid("Durable collections must use ordered List/Collection semantics", null);
            }
            if (type.getContentType() == null || type.getContentType().isJavaLangObject()) {
                throw invalid("Durable collections require a concrete element type", null);
            }
            requirePortableType(type.getContentType());
            return type;
        }
        if (type.isArrayType()) {
            requirePortableType(type.getContentType());
            return type;
        }
        if ((type.isAbstract() || type.isInterface()) && raw != Map.class && raw != List.class
                && raw != Collection.class) {
            throw invalid("Abstract or polymorphic Durable types are not supported: " + type, null);
        }
        if (raw.getTypeParameters().length > 0 && !type.hasGenericTypes()) {
            throw invalid("Raw generic Durable types are not supported: " + type, null);
        }
        if (raw.isAnonymousClass() || raw.isLocalClass() || Modifier.isAbstract(raw.getModifiers())) {
            throw invalid("Durable type must have stable concrete structure: " + type, null);
        }
        return type;
    }

    private ResumeDescriptor requireResume(ResumePoint point) {
        if (point.isStart()) {
            return null;
        }
        ResumeDescriptor descriptor = resumes.get(point.key());
        if (descriptor == null) {
            throw invalid("ResumePoint is not present in the exact Process Definition", null);
        }
        return descriptor;
    }

    private void requireFrames(List<ScopeFrame> frames, ResumeDescriptor resume) {
        int expected = resume == null ? 0 : resume.expectedFramePath().size();
        if (frames.size() != expected) {
            throw invalid("Scope-frame depth does not match ResumePoint", null);
        }
        for (int index = 0; index < expected; index++) {
            ScopeFrame frame = Objects.requireNonNull(frames.get(index), "scope frame");
            FrameDescriptor descriptor = resume.expectedFramePath().get(index);
            if (!descriptor.loopId().equals(frame.loopId())
                    || descriptor.kind() == FrameKind.FOR_EACH && !(frame instanceof ForEachFrame)
                    || descriptor.kind() == FrameKind.PARALLEL_FOR_EACH && !(frame instanceof ParallelForEachFrame)
                    || descriptor.kind() == FrameKind.WHILE && !(frame instanceof WhileFrame)) {
                throw invalid("Scope frame does not match Process Definition", null);
            }
            if (frame instanceof WhileFrame whileFrame && descriptor.maxIterations() != null
                    && whileFrame.position() >= descriptor.maxIterations()) {
                throw invalid("While scope position exceeds the Process Definition bound", null);
            }
        }
    }

    private void rejectUnsafeTree(JsonNode node) {
        if (node == null) {
            throw invalid("JSON tree must not be null", null);
        }
        if (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) {
            throw invalid("Non-finite numbers are not supported", null);
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                if (TYPE_ID_NAMES.contains(property.getKey())) {
                    throw invalid("Persisted type identifiers are not supported", null);
                }
                rejectUnsafeTree(property.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rejectUnsafeTree(child);
            }
        }
    }

    private static void requireExactProperties(ObjectNode node, Set<String> expected, String location) {
        if (node.size() != expected.size() || !new HashSet<>(node.propertyNames()).equals(expected)) {
            throw invalid(location + " properties do not match the fixed contract", null);
        }
    }

    private static String text(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw invalid(name + " must be non-blank text", null);
        }
        return value.stringValue();
    }

    private static int integer(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isInt()) {
            throw invalid(name + " must be a 32-bit integer", null);
        }
        return value.intValue();
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? DurableValueSerializer.class.getClassLoader() : loader;
    }

    /**
     * Resolves a declared Durable type with this definition's class loader.
     *
     * <p>This is used by the paired runtime program when mapping typed Process-call I/O.</p>
     */
    public Class<?> resolveJavaClass(String dataType) {
        return DataTypes.getJavaClass(dataType, classLoader);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }

    public record Limits(int maxBytes, int maxDepth, long maxTokens, int maxCollectionEntries, int maxStringBytes,
            int maxNameBytes, int maxNumberCharacters) {
        public Limits {
            if (maxBytes <= 0 || maxBytes > ABSOLUTE_MAX_BYTES) {
                throw new IllegalArgumentException("maxBytes must be in (0, 4 MiB]");
            }
            if (maxDepth <= 0 || maxTokens <= 0 || maxCollectionEntries <= 0 || maxStringBytes <= 0 || maxNameBytes <= 0
                    || maxNumberCharacters <= 0) {
                throw new IllegalArgumentException("Snapshot limits must be positive");
            }
        }

        public static Limits defaults() {
            return new Limits(256 * 1024, 64, 200_000, 100_000, 256 * 1024, 512, 1_000);
        }
    }
}
