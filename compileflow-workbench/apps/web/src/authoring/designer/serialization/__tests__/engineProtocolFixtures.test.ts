import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, test } from 'vitest'

import type { BpmnNode } from '../../types/flowDefinition'
import type { TbbpmNode } from '../../types/tbbpm'
import { generateBpmnXml, parseBpmnXml } from '../bpmnXmlCodec'
import { generateTbbpmXml, parseTbbpmXml } from '../tbbpmXmlCodec'

const REPOSITORY_ROOT = resolve(process.cwd(), '../../..')

function fixture(path: string): string {
  return readFileSync(resolve(REPOSITORY_ROOT, path), 'utf8')
}

interface ProtocolFixture {
  fixture: string
  callSiteId: string
  callMappings: string[]
  designerNodeTypes?: string[]
}

interface ProtocolGolden {
  formatVersion: number
  tbbpm: ProtocolFixture
  bpmn: ProtocolFixture
}

const golden = JSON.parse(
  fixture('docs/specs/fixtures/engine-workbench-protocol-golden-v1.json')
) as ProtocolGolden

function mappings(
  values: Array<{ direction: string; source?: string; target?: string }>
): string[] {
  return values.map(({ direction, source, target }) => `${direction}:${source}:${target}`)
}

describe('Engine protocol fixtures', () => {
  test('round-trips the Durable TBBPM protocol fixture', () => {
    expect(golden.formatVersion).toBe(1)
    const source = fixture(golden.tbbpm.fixture)
    const parsed = parseTbbpmXml(source)

    expect(parsed.success, parsed.error?.message).toBe(true)
    expect(parsed.data?.nodes.map((node) => node.type)).toEqual(
      expect.arrayContaining(golden.tbbpm.designerNodeTypes ?? [])
    )
    const call = parsed.data?.nodes.find((node) => node.id === golden.tbbpm.callSiteId) as TbbpmNode
    expect(mappings(call.properties.callMappings ?? [])).toEqual(golden.tbbpm.callMappings)

    const written = generateTbbpmXml(parsed.data!)
    const writtenCall = written.match(/<bpmCall[\s\S]*?<\/bpmCall>/)?.[0]
    expect(writtenCall).toBeDefined()
    expect(writtenCall).not.toMatch(/<input[^>]*\bdataType=/)
    expect(parseTbbpmXml(written).success).toBe(true)
  })

  test.each([
    'compileflow-integration-tests/src/test/resources/bpmn20/compat/call_activity.bpmn',
    'compileflow-integration-tests/src/test/resources/bpmn20/compat/multi_instance_loop.bpmn',
    'compileflow-integration-tests/src/test/resources/bpmn20/compat/service_task_with_invocation_policy.bpmn',
    'compileflow-integration-tests/src/test/resources/bpmn20/stateful/stateful_receive_task.bpmn',
  ])('round-trips Engine BPMN fixture %s', (path) => {
    const parsed = parseBpmnXml(fixture(path))

    expect(parsed.success, parsed.error?.message).toBe(true)
    const written = generateBpmnXml(parsed.data!)
    expect(parseBpmnXml(written).success).toBe(true)
  })

  test('preserves the Engine callActivity contract without a duplicate input type', () => {
    const parsed = parseBpmnXml(fixture(golden.bpmn.fixture))

    expect(parsed.success, parsed.error?.message).toBe(true)
    const call = parsed.data?.nodes.find((node) => node.id === golden.bpmn.callSiteId) as BpmnNode

    expect(mappings(call.properties.mappings ?? [])).toEqual(golden.bpmn.callMappings)
    const written = generateBpmnXml(parsed.data!)
    const writtenCall = written.match(/<bpmn:callActivity[\s\S]*?<\/bpmn:callActivity>/)?.[0]
    expect(writtenCall).toBeDefined()
    expect(writtenCall).not.toMatch(/<cf:input[^>]*\bdataType=/)
  })
})
