import { describe, expect, it } from 'vitest'

import { withProcessXmlIdentity } from '../processXmlIdentity'

describe('stored draft XML identity', () => {
  it('rewrites matching participant references without changing unrelated process references', () => {
    const source = `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL">
      <b:process id="old"/>
      <b:collaboration id="collaboration">
        <b:participant id="owner" processRef="old"/>
        <b:participant id="other" processRef="external"/>
      </b:collaboration>
    </b:definitions>`
    const output = withProcessXmlIdentity(source, 'BPMN', 'new', 'New')
    const document = new DOMParser().parseFromString(output, 'application/xml')
    const participants = document.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/MODEL',
      'participant'
    )
    expect(participants[0].getAttribute('processRef')).toBe('new')
    expect(participants[1].getAttribute('processRef')).toBe('external')
  })
})
