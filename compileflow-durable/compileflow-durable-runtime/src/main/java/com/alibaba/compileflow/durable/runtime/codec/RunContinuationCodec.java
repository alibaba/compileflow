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

import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation.ProcessInvocation;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation.ProcessCallSite;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation.ReturnAddress;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Opaque persisted encoding of a same-Run Process-call continuation.
 *
 * @author yusu
 */
public final class RunContinuationCodec {
    // CFRC
    private static final int MAGIC = 0x43465243;
    private static final int FORMAT_VERSION = 1;

    public byte[] encode(RunContinuation continuation, Function<UUID, DurableProcessRuntime> programResolver) {
        RunContinuation value = Objects.requireNonNull(continuation, "continuation");
        Function<UUID, DurableProcessRuntime> loaded = Objects.requireNonNull(programResolver, "programResolver");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            List<Map.Entry<ProcessCallSite, UUID>> processCallTargets = value
                .processCallTargets()
                .entrySet()
                .stream()
                .sorted(Comparator
                    .comparing((Map.Entry<ProcessCallSite, UUID> entry) -> entry.getKey().callerProcessId())
                    .thenComparing(entry -> entry.getKey().callSiteId()))
                .toList();
            output.writeInt(processCallTargets.size());
            for (Map.Entry<ProcessCallSite, UUID> call : processCallTargets) {
                writeUuid(output, call.getKey().callerProcessId());
                writeText(output, call.getKey().callSiteId());
                UUID calledProcessId = Objects.requireNonNull(call.getValue(), "calledProcessId");
                writeUuid(output, calledProcessId);
            }
            output.writeLong(value.nextInvocationId());
            output.writeInt(value.invocations().size());
            for (ProcessInvocation invocation : value.invocations()) {
                output.writeLong(invocation.invocationId());
                writeUuid(output, invocation.processId());
                ReturnAddress returnAddress = invocation.returnAddress();
                output.writeBoolean(returnAddress != null);
                if (returnAddress != null) {
                    output.writeLong(returnAddress.invocationId());
                    writeText(output, returnAddress.frontierId().value());
                    writeText(output, returnAddress.elementId());
                }
                byte[] memberContinuation =
                        requireProgram(loaded, invocation.processId())
                    .valueSerializer()
                    .encode(invocation.continuation());
                writeBytes(output, memberContinuation);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > DurableStore.MAX_ENVELOPE_BYTES) {
                throw new IllegalArgumentException("Run continuation exceeds the Durable envelope limit");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory Run continuation encoding failed", impossible);
        }
    }

    public RunContinuation decode(byte[] source, DurableProcessRuntimeCache programs) {
        Objects.requireNonNull(programs, "programs");
        return decode(source, processId -> programs.get(processId).orElse(null));
    }

    public RunContinuation decode(byte[] source, Function<UUID, DurableProcessRuntime> programResolver) {
        byte[] bytes = Objects.requireNonNull(source, "source").clone();
        if (bytes.length == 0 || bytes.length > DurableStore.MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Run continuation byte length is invalid");
        }
        Function<UUID, DurableProcessRuntime> loaded = Objects.requireNonNull(programResolver, "programResolver");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Run continuation format is unsupported");
            }
            if (input.readInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Run continuation format is unsupported");
            }
            Map<ProcessCallSite, UUID> processCallTargets = new LinkedHashMap<>();
            int callCount = readNonNegativeCount(input, RunContinuation.MAX_PROCESS_CALL_TARGETS);
            for (int index = 0; index < callCount; index++) {
                ProcessCallSite site = new ProcessCallSite(readUuid(input), readText(input, 128));
                UUID calledProcessId = readUuid(input);
                requireProgram(loaded, calledProcessId);
                if (processCallTargets.putIfAbsent(site, calledProcessId) != null) {
                    throw new IllegalArgumentException("Run continuation contains duplicate Process call bindings");
                }
            }
            long nextInvocationId = input.readLong();
            int invocationCount = readCount(input, RunContinuation.MAX_INVOCATIONS);
            List<ProcessInvocation> invocations = new ArrayList<>(invocationCount);
            for (int index = 0; index < invocationCount; index++) {
                long invocationId = input.readLong();
                UUID processId = readUuid(input);
                ReturnAddress returnAddress = null;
                if (input.readBoolean()) {
                    returnAddress = new ReturnAddress(input.readLong(), new FrontierId(readText(input, 128)),
                            readText(input, 128));
                }
                byte[] memberContinuation = readBytes(input, DurableStore.MAX_ENVELOPE_BYTES);
                invocations.add(
                        new ProcessInvocation(invocationId, processId,
                                requireProgram(loaded, processId).valueSerializer().decode(memberContinuation),
                                returnAddress));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Run continuation contains trailing bytes");
            }
            return new RunContinuation(nextInvocationId, invocations, processCallTargets);
        } catch (EOFException truncated) {
            throw new IllegalArgumentException("Run continuation is truncated", truncated);
        } catch (IllegalStateException unavailable) {
            throw unavailable;
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Run continuation cannot be decoded", failure);
        }
    }

    private static DurableProcessRuntime requireProgram(Function<UUID, DurableProcessRuntime> programs, UUID processId) {
        DurableProcessRuntime loaded = programs.apply(processId);
        if (loaded == null) {
            throw new IllegalStateException("Stored Process is not loaded: " + processId);
        }
        if (!processId.equals(loaded.processId())) {
            throw new IllegalStateException("Loaded Process ID does not match the requested Process: " + processId);
        }
        return loaded;
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        writeBytes(output, ProcessText.encodeUtf8(value, "Run continuation text"));
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static String readText(DataInputStream input, int maxCharacters) throws IOException {
        String value = ProcessText.decodeUtf8(readBytes(input, maxCharacters * 4), "Run continuation text");
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maxCharacters) {
            throw new IllegalArgumentException("Run continuation text length is invalid");
        }
        return value;
    }

    private static byte[] readBytes(DataInputStream input, int maxBytes) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maxBytes) {
            throw new IllegalArgumentException("Run continuation field length is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Run continuation field is truncated");
        }
        return value;
    }

    private static int readCount(DataInputStream input, int max) throws IOException {
        return readCount(input, 1, max);
    }

    private static int readNonNegativeCount(DataInputStream input, int max) throws IOException {
        return readCount(input, 0, max);
    }

    private static int readCount(DataInputStream input, int minimum, int max) throws IOException {
        int count = input.readInt();
        if (count < minimum || count > max) {
            throw new IllegalArgumentException("Run continuation item count is invalid");
        }
        return count;
    }
}
