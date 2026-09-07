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
package com.alibaba.compileflow.engine.tbbpm.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import com.alibaba.compileflow.engine.tbbpm.writer.TbbpmXmlWriter;
import com.alibaba.compileflow.engine.tbbpm.validation.TbbpmModelValidator;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.TimerTaskNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimerTaskContractTest {
    private static List<String> validationMessages(String xml) {
        return new TbbpmModelValidator().validate(parse(xml)).stream().map(ValidationFailure::message).toList();
    }

    private static TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("timer.contract", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> validationMessagesWithoutSchema(String xml) {
        TbbpmModel model = TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("timer.contract", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED);
        return new TbbpmModelValidator().validate(model).stream().map(ValidationFailure::message).toList();
    }

    private static String flow(String scheduleAttributes) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="timer.contract" name="Timer Contract">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="coolingOff"/>
                </start>
                <timerTask id="coolingOff" name="Cooling off"
                           g="60,0,100,40" %s>
                    <transition to="end"/>
                </timerTask>
                <end id="end" name="End" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(scheduleAttributes);
    }

    @Test
    void timerTaskRoundTripsThroughCanonicalXml() {
        TbbpmModel first = parse(flow("duration=\"PT5M\""));
        TimerTaskNode timer = (TimerTaskNode) first.getNode("coolingOff");

        assertThat(timer.getDuration()).isEqualTo("PT5M");
        assertThat(timer.getDurationExpression()).isNull();
        assertThat(first.getNode("coolingOff").getOutgoingTransitions().get(0).getTarget()).isEqualTo("end");

        ByteArrayOutputStream output = (ByteArrayOutputStream) TbbpmXmlWriter.getInstance().write(first);
        TbbpmModel second = parse(output.toString(StandardCharsets.UTF_8));
        assertThat(((TimerTaskNode) second.getNode("coolingOff")).getDuration()).isEqualTo("PT5M");
    }

    @Test
    void validatorRequiresExactlyOneNonNegativeSchedule() {
        assertThat(validationMessages(flow("duration=\"PT1S\" " + "wakeAtExpression=\"publishAt\""))
            .stream()
            .anyMatch(message -> message.contains("exactly one")))
            .isTrue();
        assertThat(validationMessagesWithoutSchema(flow("duration=\"-PT1S\""))
            .stream()
            .anyMatch(message -> message.contains("must not be negative")))
            .isTrue();
        assertThat(validationMessages(flow(""))
            .stream()
            .anyMatch(message -> message.contains("exactly one"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT1S", "PT60S", "PT1.001S", "PT1.000S", "P0DT1S", "P1D"})
    void schemaAcceptsTheProtocolDurationLanguage(String duration) {
        assertThat(parse(flow("duration=\"" + duration + "\""))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"duration=\"PT1S\" durationExpression=\"\"",
            "duration=\"PT1S\" wakeAtExpression=\"   \"", "duration=\"\" durationExpression=\"delay\""})
    void rejectsBlankSchedulesAlongsideAnotherDeclaredSchedule(String attributes) {
        assertThat(validationMessagesWithoutSchema(flow(attributes))).anyMatch(message -> message.contains(
                "exactly one"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duration=\"\"", "durationExpression=\"   \"", "wakeAtExpression=\"\""})
    void rejectsBlankSingleSchedule(String attributes) {
        assertThat(validationMessagesWithoutSchema(flow(attributes))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"P", "PT", "PT+1S", "PT-1S", "-PT1S", "P1Y", "P1M", "P1W", "pt1s",
            "PT1.0000000000S", "PT0.000000001S"})
    void schemaRejectsValuesOutsideTheProtocolDurationLanguage(String duration) {
        assertThatThrownBy(() -> parse(flow("duration=\"" + duration + "\"")))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Flow schema validation failed");
    }

    @Test
    void validatorAcceptsTimerInsideLoop() {
        String xml =
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="timer.loop" name="Timer Loop">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <while id="loop" name="Loop" g="60,0,200,120"
                           condition="false" maxIterations="100">
                    <transition to="end"/>
                    <start id="bodyStart"><transition to="delay"/></start>
                    <timerTask id="delay" name="Delay" g="100,20,100,40" duration="PT1S">
                        <transition to="bodyEnd"/>
                    </timerTask>
                    <end id="bodyEnd"/>
                </while>
                <end id="end" name="End" g="300,0,32,32"/>
            </bpm>
            """;

        assertThat(validationMessages(xml)).isEmpty();
    }
}
