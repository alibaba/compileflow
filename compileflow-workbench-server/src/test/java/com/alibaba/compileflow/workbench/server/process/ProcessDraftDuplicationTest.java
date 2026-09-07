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
package com.alibaba.compileflow.workbench.server.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Timeout(30)
class ProcessDraftDuplicationTest {
    private static final String SOURCE_CODE = "source.flow";
    private static final String COPY_CODE = "copy.flow";
    private static final String COPY_NAME = "Copy & \"quoted\"";
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI";

    @ParameterizedTest
    @EnumSource(ProcessModelType.class)
    void duplicatePassesTheSameRealPublicationPreflightAsItsSource(ProcessModelType type) {
        String xml = type == ProcessModelType.TBBPM
                ? "<bpm code=\"source.flow\" name=\"Original\"><start id=\"start\">"
                + "<transition to=\"end\"/></start><end id=\"end\"/></bpm>"
                : "<definitions xmlns=\"" + BPMN + "\" targetNamespace=\"urn:draft\">"
                + "<process id=\"source.flow\" name=\"Original\" isExecutable=\"true\">"
                + "<startEvent id=\"start\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"flow\" sourceRef=\"start\" targetRef=\"end\"/>" + "</process></definitions>";
        ProcessDraftService.ProcessRecord copy = duplicate(type, xml);
        try (ProcessEngine engine = ProcessEngineFactory.create()) {
            ProcessDefinitionPreflightService preflight = new ProcessDefinitionPreflightService(engine);
            assertThat(preflight.preflight(SOURCE_CODE, type, xml).getOverallStatus())
                .as("The source fixture must already be publishable")
                .isEqualTo(ProcessPreflightReport.OverallStatus.PASS);
            ProcessPreflightReport report = preflight.preflight(copy.code(), copy.type(), copy.xml());
            assertThat(report.getOverallStatus())
                .as("Duplicated definition must retain publication admission: %s", report.getItems())
                .isEqualTo(ProcessPreflightReport.OverallStatus.PASS);
        }
        ProcessImportParser.ImportedProcess imported = new ProcessImportParser().parse(copy.xml());
        assertThat(imported.code()).isEqualTo(COPY_CODE);
        assertThat(imported.name()).isEqualTo(COPY_NAME);
    }

    @Test
    void tbbpmRewritesOnlyRootIdentityWithoutRequiringAnExecutableDraft() throws Exception {
        String xml =
                """
            <?draft keep-this?>
            <bpm xmlns:ext="urn:extension" code="source.flow" name="Original" ext:flag="keep">
              <!-- Keep the unfinished action and its literal source.flow reference. -->
              <autoTask id="unfinished"><ext:item code="source.flow" name="Original"/>
                <script><![CDATA[var code = "source.flow"; a < b;]]></script>
              </autoTask>
            </bpm>
            """;
        Document original = document(xml);
        Document copied = document(duplicate(ProcessModelType.TBBPM, xml).xml());
        Element root = copied.getDocumentElement();
        assertThat(root.getAttribute("code")).isEqualTo(COPY_CODE);
        assertThat(root.getAttribute("name")).isEqualTo(COPY_NAME);
        root.setAttribute("code", SOURCE_CODE);
        root.setAttribute("name", "Original");
        assertThat(copied.isEqualNode(original)).as("All non-identity XML nodes must survive").isTrue();
    }

    @Test
    void bpmnUpdatesLocalProcessReferencesButPreservesExtensionsAndCallTargets() throws Exception {
        String xml =
                """
            <b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:di="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:ext="urn:extension"
                xmlns:local="urn:draft" xmlns:other="urn:other" targetNamespace="urn:draft">
              <b:process id="source.flow" name="Original">
                <b:extensionElements><ext:process id="source.flow" name="Original"/></b:extensionElements>
                <b:callActivity id="call" calledElement="source.flow"/>
                <b:documentation><![CDATA[Keep source.flow <documentation> unchanged.]]></b:documentation>
              </b:process>
              <b:collaboration id="collaboration">
                <b:participant id="local" processRef="source.flow"/>
                <b:participant id="qualified" processRef="local:source.flow"/>
                <b:participant id="external" processRef="other:source.flow"/>
                <b:participant id="different" processRef="different.flow"/>
              </b:collaboration>
              <di:BPMNDiagram id="diagram"><di:BPMNPlane id="plane" bpmnElement="source.flow"/></di:BPMNDiagram>
              <di:BPMNDiagram id="qualifiedDiagram">
                <di:BPMNPlane id="qualifiedPlane" bpmnElement="local:source.flow"/>
              </di:BPMNDiagram>
            </b:definitions>
            """;
        Document original = document(xml);
        Document copied = document(duplicate(ProcessModelType.BPMN, xml).xml());
        Element process = (Element) copied.getElementsByTagNameNS(BPMN, "process").item(0);
        assertThat(process.getAttribute("id")).isEqualTo(COPY_CODE);
        assertThat(process.getAttribute("name")).isEqualTo(COPY_NAME);
        process.setAttribute("id", SOURCE_CODE);
        process.setAttribute("name", "Original");
        Element local = (Element) copied.getElementsByTagNameNS(BPMN, "participant").item(0);
        Element qualified = (Element) copied.getElementsByTagNameNS(BPMN, "participant").item(1);
        assertThat(local.getAttribute("processRef")).isEqualTo(COPY_CODE);
        assertThat(qualified.getAttribute("processRef")).isEqualTo("local:" + COPY_CODE);
        local.setAttribute("processRef", SOURCE_CODE);
        qualified.setAttribute("processRef", "local:" + SOURCE_CODE);
        Element plane = (Element) copied.getElementsByTagNameNS(BPMN_DI, "BPMNPlane").item(0);
        Element qualifiedPlane = (Element) copied.getElementsByTagNameNS(BPMN_DI, "BPMNPlane").item(1);
        assertThat(plane.getAttribute("bpmnElement")).isEqualTo(COPY_CODE);
        assertThat(qualifiedPlane.getAttribute("bpmnElement")).isEqualTo("local:" + COPY_CODE);
        plane.setAttribute("bpmnElement", SOURCE_CODE);
        qualifiedPlane.setAttribute("bpmnElement", "local:" + SOURCE_CODE);
        assertThat(copied.isEqualNode(original)).as("Unrelated references and all extension nodes must survive").isTrue();
    }

    @ParameterizedTest
    @MethodSource("unfinishedDrafts")
    void preservesDraftTextWhenItsIdentityCannotBeSafelyRewritten(ProcessModelType type, String xml) {
        assertThat(duplicate(type, xml).xml()).isEqualTo(xml);
    }

    @Test
    void duplicationNeverRequestsExternalDtdOrEntityFromAReachableLoopbackServer() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                byte[] body = "loopback-probe".getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                } finally {
                    exchange.close();
                }
            });
            server.start();
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpURLConnection control = (HttpURLConnection) URI.create(origin + "/control").toURL().openConnection();
            try {
                control.setConnectTimeout(2000);
                control.setReadTimeout(2000);
                assertThat(control.getResponseCode()).isEqualTo(200);
                try (InputStream response = control.getInputStream()) {
                    assertThat(new String(response.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("loopback-probe");
                }
            } finally {
                control.disconnect();
            }
            assertThat(requests.get()).as("The external resource probe must actually be reachable").isEqualTo(1);
            requests.set(0);
            String dtd = "<!DOCTYPE bpm SYSTEM '" + origin + "/draft.dtd'><bpm code=\"source.flow\"/>";
            String entity = "<!DOCTYPE bpm [<!ENTITY xxe SYSTEM '" + origin + "/entity'>]>"
                    + "<bpm code=\"source.flow\"><description>&xxe;</description></bpm>";
            assertThat(duplicate(ProcessModelType.TBBPM, dtd).xml()).isEqualTo(dtd);
            assertThat(duplicate(ProcessModelType.TBBPM, entity).xml()).isEqualTo(entity);
            assertThat(requests.get()).as("Draft duplication must not fetch either external XML resource").isZero();
        } finally {
            server.stop(0);
        }
    }

    private static Stream<Arguments> unfinishedDrafts() {
        return Stream.of(Arguments.of(ProcessModelType.TBBPM, ""), Arguments.of(ProcessModelType.BPMN, "  \n  "),
                Arguments.of(ProcessModelType.TBBPM, "<bpm code=\"source.flow\"><unfinished>"),
                Arguments.of(ProcessModelType.TBBPM, "<bpm code=\"source.flow\"/>trailing"),
                Arguments.of(ProcessModelType.TBBPM, "<bpm/>"), Arguments.of(ProcessModelType.BPMN, "<definitions/>"),
                Arguments.of(ProcessModelType.BPMN, "<bpm code=\"source.flow\"/>"),
                Arguments.of(ProcessModelType.TBBPM, "<bpm xmlns=\"urn:other\" code=\"source.flow\"/>"),
                Arguments.of(ProcessModelType.BPMN,
                        "<definitions xmlns=\"" + BPMN + "\"><process id=\"source.flow\"/><process id=\"other\"/></definitions>"),
                Arguments.of(ProcessModelType.TBBPM,
                        "<!DOCTYPE bpm [<!ENTITY value 'draft'>]><bpm code=\"source.flow\" name=\"&value;\"/>"),
                Arguments.of(ProcessModelType.TBBPM,
                        "<!DOCTYPE bpm SYSTEM 'file:///nonexistent-draft.dtd'><bpm code=\"source.flow\"/>"),
                Arguments.of(ProcessModelType.TBBPM,
                        "<!DOCTYPE bpm [<!ENTITY xxe SYSTEM 'file:///nonexistent-draft-secret'>]>"
                        + "<bpm code=\"source.flow\"><description>&xxe;</description></bpm>"));
    }

    private static ProcessDraftService.ProcessRecord duplicate(ProcessModelType type, String xml) {
        ProcessDraftRepository repository = mock(ProcessDraftRepository.class);
        ServerIdentity identity = mock(ServerIdentity.class);
        ProcessDraftEntity source = new ProcessDraftEntity();
        source.setCode(SOURCE_CODE);
        source.setName("Original");
        source.setType(type);
        source.setXml(xml);
        source.setDescription("Keep description");
        source.setTagsJson("[\"tag\"]");
        source.setRevision(7L);
        Instant now = Instant.parse("2026-09-07T09:00:00Z");
        when(repository.findById(SOURCE_CODE)).thenReturn(Optional.of(source));
        when(repository.currentTimestamp()).thenReturn(now);
        when(identity.principal()).thenReturn("copy-author");
        when(repository.saveAndFlush(any(ProcessDraftEntity.class))).thenAnswer(invocation -> {
            ProcessDraftEntity stored = invocation.getArgument(0);
            stored.setRevision(0L);
            return stored;
        });
        ProcessDraftService.ProcessRecord result = new ProcessDraftService(repository, identity)
            .duplicateProcess(SOURCE_CODE, COPY_CODE, COPY_NAME)
            .orElseThrow();
        ArgumentCaptor<ProcessDraftEntity> saved = ArgumentCaptor.forClass(ProcessDraftEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        assertThat(saved.getValue().getXml()).isEqualTo(result.xml());
        assertThat(result.code()).isEqualTo(COPY_CODE);
        assertThat(result.name()).isEqualTo(COPY_NAME);
        assertThat(result.type()).isEqualTo(type);
        assertThat(result.createdBy()).isEqualTo("copy-author");
        assertThat(result.createdAt()).isEqualTo(now.toString());
        assertThat(result.updatedAt()).isEqualTo(now.toString());
        assertThat(result.tags()).containsExactly("tag");
        assertThat(result.description()).isEqualTo("Keep description");
        assertThat(result.revision()).isZero();
        assertThat(source.getXml()).isEqualTo(xml);
        assertThat(source.getCode()).isEqualTo(SOURCE_CODE);
        assertThat(source.getName()).isEqualTo("Original");
        assertThat(source.getRevision()).isEqualTo(7L);
        return result;
    }

    private static Document document(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
