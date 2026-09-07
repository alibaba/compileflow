import type { ProcessModelType } from '@/shared/contracts'

export function withProcessXmlIdentity(
  xml: string,
  type: ProcessModelType,
  code: string,
  name: string
): string {
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  const process =
    type === 'BPMN'
      ? document.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'process')[0]
      : document.documentElement
  if (!process || document.getElementsByTagName('parsererror').length)
    throw new Error('Invalid process XML')
  const oldId = process.getAttribute(type === 'BPMN' ? 'id' : 'code')
  process.setAttribute(type === 'BPMN' ? 'id' : 'code', code)
  process.setAttribute('name', name)
  if (type === 'BPMN') {
    for (const participant of document.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/MODEL',
      'participant'
    )) {
      if (participant.getAttribute('processRef') === oldId)
        participant.setAttribute('processRef', code)
    }
    for (const plane of document.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/DI',
      'BPMNPlane'
    )) {
      if (plane.getAttribute('bpmnElement') === oldId) plane.setAttribute('bpmnElement', code)
    }
  }
  return new XMLSerializer().serializeToString(document)
}
