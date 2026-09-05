export const DEFAULT_TBBPM_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="sample" name="New TBBPM Process">
  <var name="inputData" description="Input data" dataType="java.lang.String" inOutType="param"/>
  <var name="result" description="Result" dataType="java.lang.String" inOutType="return"/>
  <start id="start" name="Start" g="80,100,60,60">
    <transition to="end"/>
  </start>
  <end id="end" name="End" g="400,100,60,60"/>
</bpm>`

export const DEFAULT_TBBPM_WITH_NODES_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="greeting_process" name="Greeting Process">
  <var name="name" description="Name to greet" dataType="java.lang.String" inOutType="param" defaultValue="World"/>
  <var name="message" description="Generated greeting" dataType="java.lang.String" inOutType="return"/>
  <start id="start" name="Start" g="80,150,60,60">
    <transition to="buildGreeting"/>
  </start>
  <scriptTask id="buildGreeting" name="Build Greeting" g="200,150,120,60">
    <action type="script" language="qlexpress">
      <input target="name" dataType="java.lang.String" source="name"/>
      <output dataType="java.lang.String" target="message"/>
      <code><![CDATA["Hello, " + name]]></code>
    </action>
    <transition to="end"/>
  </scriptTask>
  <end id="end" name="End" g="400,150,60,60"/>
</bpm>`
