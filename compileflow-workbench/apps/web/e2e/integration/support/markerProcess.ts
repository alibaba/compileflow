export function markerProcessXml(code: string, marker: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="${code}" name="${code}">
  <var name="version_marker" dataType="java.lang.String" inOutType="return"/>
  <start id="start" name="Start" g="50,50,32,32">
    <transition to="marker"/>
  </start>
  <scriptTask id="marker" name="Marker" g="150,40,88,48">
    <action type="script" language="qlexpress">
      <output target="version_marker" dataType="java.lang.String"/>
      <code><![CDATA["${marker}"]]></code>
    </action>
    <transition to="end"/>
  </scriptTask>
  <end id="end" name="End" g="300,50,32,32"/>
</bpm>`
}

export function failingProcessXml(code: string, _failureMessage: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="${code}" name="${code}">
  <var name="fail" dataType="java.lang.Boolean" inOutType="param"/>
  <start id="start" name="Start" g="50,50,32,32">
    <transition to="failure"/>
  </start>
  <scriptTask id="failure" name="Failure" g="150,40,88,48">
    <action type="script" language="qlexpress">
      <input source="fail" target="fail" dataType="java.lang.Boolean"/>
      <code><![CDATA[fail ? 1 / 0 : 0]]></code>
    </action>
    <transition to="end"/>
  </scriptTask>
  <end id="end" name="End" g="300,50,32,32"/>
</bpm>`
}

/** Minimal executable BPMN with a QL Script Task. */
export function markerBpmnProcessXml(code: string, marker: string): string {
  // Match engine-facing fixtures: default BPMN NS, process id == code, no DI required.
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:cf="http://www.compileflow.org"
             targetNamespace="http://www.compileflow.org">
  <process id="${code}" name="${code}" isExecutable="true">
    <extensionElements>
      <cf:var name="version_marker" dataType="java.lang.String" inOutType="return"/>
    </extensionElements>
    <startEvent id="StartEvent_1" name="Start"/>
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="MarkerTask"/>
    <scriptTask id="MarkerTask" name="Marker" scriptFormat="qlexpress">
      <extensionElements>
        <cf:output target="version_marker" dataType="java.lang.String"/>
      </extensionElements>
      <script><![CDATA["${marker}"]]></script>
    </scriptTask>
    <sequenceFlow id="Flow_2" sourceRef="MarkerTask" targetRef="EndEvent_1"/>
    <endEvent id="EndEvent_1" name="End"/>
  </process>
</definitions>`
}
