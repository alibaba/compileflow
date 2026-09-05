import type { Page } from '@playwright/test'

const SAMPLE_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:cf="http://www.compileflow.org"
  id="Definitions_1"
  targetNamespace="http://www.compileflow.org">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_1" name="结束">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="432" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="216" y="118" />
        <di:waypoint x="432" y="118" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

const ORDER_APPROVAL_FLOW = {
  code: 'order-approval-bpmn',
  name: '订单审批流程',
  type: 'BPMN',
  description: '订单审批流程（E2E mock）',
  xml: SAMPLE_BPMN_XML,
  version: '1.2.0',
  status: 'published',
  createdAt: '2026-01-15T10:00:00Z',
  updatedAt: '2026-02-08T14:30:00Z',
  createdBy: 'admin',
}

export async function installOperateApiMocks(page: Page): Promise<void> {
  const listResponse = {
    data: [ORDER_APPROVAL_FLOW],
    total: 1,
    page: 1,
    pageSize: 10,
  }

  await page.route(/\/api\/flows\/order-approval-bpmn\/?$/, async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(ORDER_APPROVAL_FLOW),
      })
      return
    }
    if (method === 'PUT') {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          ...ORDER_APPROVAL_FLOW,
          updatedAt: new Date().toISOString(),
        }),
      })
      return
    }
    await route.continue()
  })

  await page.route(/\/api\/flows(\?.*)?$/, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(listResponse),
      })
      return
    }
    await route.continue()
  })
}
