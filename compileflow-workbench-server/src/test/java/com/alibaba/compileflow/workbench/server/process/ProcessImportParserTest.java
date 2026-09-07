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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessModelType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProcessImportParserTest {
    private final ProcessImportParser parser = new ProcessImportParser();

    @Test
    void readsTbbpmIdentityFromTheRootElement() {
        ProcessImportParser.ImportedProcess imported =
                parser.parse("<?xml version=\"1.0\"?><bpm code=\"order.flow\" name=\"Order Process\"/>");

        assertThat(imported.code()).isEqualTo("order.flow");
        assertThat(imported.name()).isEqualTo("Order Process");
        assertThat(imported.type()).isEqualTo(ProcessModelType.TBBPM);
    }

    @Test
    void readsTheSingleBpmnProcessIdentity() {
        ProcessImportParser.ImportedProcess imported = parser.parse(
                "<?xml version=\"1.0\"?>" + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"order.flow\" name=\"Order Process\"/>" + "</definitions>");

        assertThat(imported.code()).isEqualTo("order.flow");
        assertThat(imported.name()).isEqualTo("Order Process");
        assertThat(imported.type()).isEqualTo(ProcessModelType.BPMN);
    }

    @Test
    void rejectsAmbiguousBpmnDocuments() {
        assertThatThrownBy(() -> parser.parse(
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"one\"/><process id=\"two\"/>" + "</definitions>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Imported BPMN must contain exactly one process");
    }

    @Test
    void rejectsNamespacedTbbpmRoot() {
        assertThatThrownBy(() -> parser.parse("<bpm xmlns=\"urn:example\" code=\"order.flow\"/>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Imported XML must be a TBBPM <bpm> document or a BPMN 2.0 <definitions> document");
    }

    @Test
    void rejectsMultipleDocumentElements() {
        assertThatThrownBy(() -> parser.parse("<bpm code=\"order.flow\"/><bpm code=\"other.flow\"/>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Imported file is not well-formed XML");
    }

    @Test
    void rejectsInvalidTrailingContent() {
        assertThatThrownBy(() -> parser.parse("<bpm code=\"order.flow\"/>trailing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Imported file is not well-formed XML");
    }

    @Test
    void rejectsExternalEntities() {
        assertThatThrownBy(() -> parser.parse(
                "<!DOCTYPE bpm [<!ENTITY secret SYSTEM \"file:///etc/passwd\">]>" + "<bpm code=\"order.flow\" name=\"&secret;\"/>"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveElementDepth() {
        String xml = "<bpm code=\"order.flow\">" + "<node>".repeat(128) + "</node>".repeat(128) + "</bpm>";

        assertThatThrownBy(() -> parser.parse(xml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Imported file is not well-formed XML");
    }

    @Test
    void rejectsMalformedUtf8BeforeParsingXml() {
        byte[] malformed = new byte[] {'<', 'b', 'p', 'm', ' ', 'c', 'o', 'd', 'e', '=', '"', 'x', '"', ' ', 'n', 'a',
                'm', 'e', '=', '"', (byte) 0xC3, (byte) 0x28, '"',
                '/', '>'};

        assertThatThrownBy(() -> parser.parse(malformed))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Imported XML must be valid UTF-8");
    }

    @Test
    void acceptsOneUtf8BomAndReturnsTheExactDecodedSnapshot() {
        String xml = "<bpm code=\"order.flow\"/>";
        byte[] content = ("\uFEFF" + xml).getBytes(StandardCharsets.UTF_8);

        ProcessImportParser.ImportedDocument document = parser.parse(content);

        assertThat(document.process().code()).isEqualTo("order.flow");
        assertThat(document.xml()).isEqualTo(xml);
    }
}
