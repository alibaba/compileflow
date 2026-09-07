import { describe, expect, it } from 'vitest'

import { duplicateMockProcess, getMockProcessByCode } from '../mockProcessData'

describe('mock process XML identity', () => {
  it.each(['order-approval-bpmn', 'payment-process-tbbpm'])(
    'exports the current process identity for %s',
    (code) => {
      const process = getMockProcessByCode(code)
      const doc = new DOMParser().parseFromString(process.xml, 'application/xml')
      const root =
        process.type === 'BPMN'
          ? doc.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'process')[0]
          : doc.documentElement
      expect(root.getAttribute(process.type === 'BPMN' ? 'id' : 'code')).toBe(code)
      expect(root.getAttribute('name')).toBe(process.name)
    }
  )

  it('rewrites the duplicated BPMN process and diagram identity together', () => {
    const process = duplicateMockProcess('order-approval-bpmn', 'copy-flow', 'Copy')
    const doc = new DOMParser().parseFromString(process.xml, 'application/xml')
    expect(
      doc
        .getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'process')[0]
        .getAttribute('id')
    ).toBe('copy-flow')
    expect(
      doc
        .getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNPlane')[0]
        .getAttribute('bpmnElement')
    ).toBe('copy-flow')
  })
})
