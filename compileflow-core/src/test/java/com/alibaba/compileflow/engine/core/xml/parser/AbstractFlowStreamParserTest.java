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
package com.alibaba.compileflow.engine.core.xml.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.model.Element;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.Test;

class AbstractFlowStreamParserTest {
    @Test
    void unqualifiedAttributeLookupDoesNotFallBackToAnotherNamespace() throws Exception {
        String xml = "<flow xmlns=\"urn:flow\" xmlns:ext=\"urn:extension\" ext:name=\"extension\"/>";
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        try {
            reader.nextTag();
            XmlStreamReaderSource source = XmlStreamReaderSource.of(reader);
            assertThat(source.getString("name")).isNull();
            assertThat(source.getString("urn:extension", "name")).isEqualTo("extension");
        } finally {
            reader.close();
        }
    }

    @Test
    void sameLocalAttributeNamesRemainDistinctRegardlessOfPrefixOrOrder() throws Exception {
        String xml =
                "<flow xmlns=\"urn:flow\" xmlns:other=\"http://www.compileflow.org\""
                + " other:name=\"extension\" name=\"standard\"/>";
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        try {
            reader.nextTag();
            XmlStreamReaderSource source = XmlStreamReaderSource.of(reader);
            assertThat(source.getString("name")).isEqualTo("standard");
            assertThat(source.getString("", "name")).isEqualTo("standard");
            assertThat(source.getCfString("name")).isEqualTo("extension");
        } finally {
            reader.close();
        }
    }

    @Test
    void schemaValidationEnforcesTheSameNestingBudget() {
        TraversingParser parser = new TraversingParser() {
            @Override
            protected String getXSD() {
                return "xsd/nesting-test.xsd";
            }
        };
        String xml = "<flow>".repeat(5000) + "</flow>".repeat(5000);
        assertThatExceptionOfType(CompileFlowException.class)
            .isThrownBy(() -> parser.parse(FlowSource.of("deep", xml.getBytes(StandardCharsets.UTF_8))))
            .satisfies(failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_VALIDATION_002))
            .withMessageContaining("128");
        String accepted = "<flow>".repeat(128) + "</flow>".repeat(128);
        assertThat(parser.parse(FlowSource.of("bounded", accepted.getBytes(StandardCharsets.UTF_8)))).isEqualTo(
                "parsed");
    }

    @Test
    void rejectsDeepXmlBeforeRecursiveModelParsingEvenWithoutSchemaValidation() {
        String xml = "<flow>".repeat(5000) + "</flow>".repeat(5000);
        assertThatExceptionOfType(CompileFlowException.class)
            .isThrownBy(() -> new TraversingParser()
                .parse(FlowSource.of("deep", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED))
            .satisfies(failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_VALIDATION_002))
            .withMessageContaining("128");
    }

    @Test
    void permitsWideDocumentsWithinTheNestingBudget() {
        String xml = "<flow>" + "<task/>".repeat(5000) + "</flow>";
        TraversingParser parser = new TraversingParser() {
            @Override
            protected String parseFlowModel(XmlSource source) throws Exception {
                source.hasNext();
                source.skipCurrentElement();
                return "parsed";
            }
        };
        assertThat(parser.parse(FlowSource.of("wide", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED))
            .isEqualTo("parsed");
    }

    @Test
    void flowSourceRequiresACanonicalProcessCode() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> FlowSource.of("order code", new byte[0]))
            .withMessageContaining("code must start with an ASCII letter or digit");
    }

    @Test
    void classifiesMalformedXmlAsDefinitionValidationFailureWhenSchemaValidationIsDisabled() {
        FlowSource source = FlowSource.of("order", "<flow><task></flow>".getBytes(StandardCharsets.UTF_8));

        assertThatExceptionOfType(CompileFlowException.class)
            .isThrownBy(() -> new TraversingParser().parse(source, SchemaValidation.DISABLED))
            .satisfies(failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_VALIDATION_002));
    }

    @Test
    void classifiesNestedMarkupInTextOnlyElementAsDefinitionValidationFailure() throws Exception {
        XMLStreamReader reader =
                XMLInputFactory.newFactory().createXMLStreamReader(new StringReader("<value>text<child/></value>"));
        reader.nextTag();
        XmlStreamReaderSource source = XmlStreamReaderSource.of(reader);

        try {
            assertThatExceptionOfType(CompileFlowException.class)
                .isThrownBy(source::getElementText)
                .satisfies(failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_VALIDATION_002));
        } finally {
            reader.close();
        }
    }

    @Test
    void classifiesParserContractViolationsAsDefinitionValidationFailures() {
        FlowSource source = FlowSource.of("order", "<flow/>".getBytes(StandardCharsets.UTF_8));
        TraversingParser parser = new TraversingParser() {
            @Override
            protected String parseFlowModel(XmlSource xmlSource) {
                throw new IllegalArgumentException("invalid element contract");
            }
        };

        assertThatExceptionOfType(CompileFlowException.class)
            .isThrownBy(() -> parser.parse(source, SchemaValidation.DISABLED))
            .satisfies(failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_VALIDATION_002));
    }

    private static class TraversingParser extends AbstractFlowStreamParser<String> {
        @Override
        protected String parseFlowModel(XmlSource xmlSource) throws Exception {
            while (xmlSource.hasNext()) {
                xmlSource.nextElementName();
            }
            return "parsed";
        }

        @Override
        protected AbstractFlowElementParserRegistry getFlowElementParserRegistry() {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        protected String convertToFlowModel(Element top) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        protected String getXSD() {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }
}
