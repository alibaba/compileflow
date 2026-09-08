export const DEFAULT_TBBPM_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="sample" name="New TBBPM Process">
</bpm>`

export const TBBPM_STARTER_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="sample" name="TBBPM Starter">
  <start id="start" name="Start" g="80,100,80,80">
    <transition to="end"/>
  </start>
  <end id="end" name="End" g="400,100,80,80"/>
</bpm>`

export const DEFAULT_TBBPM_WITH_NODES_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="greeting_process" name="Greeting Process">
  <var name="name" description="Name to greet" dataType="java.lang.String" inOutType="param" defaultValue="World"/>
  <var name="message" description="Generated greeting" dataType="java.lang.String" inOutType="return"/>
  <start id="start" name="Start" g="80,160,80,80">
    <transition to="buildGreeting"/>
  </start>
  <scriptTask id="buildGreeting" name="Build Greeting" g="220,150,200,100">
    <action type="script" language="qlexpress">
      <input target="name" dataType="java.lang.String" source="name"/>
      <output dataType="java.lang.String" target="message"/>
      <code><![CDATA["Hello, " + name]]></code>
    </action>
    <transition to="end"/>
  </scriptTask>
  <end id="end" name="End" g="500,160,80,80"/>
</bpm>`
