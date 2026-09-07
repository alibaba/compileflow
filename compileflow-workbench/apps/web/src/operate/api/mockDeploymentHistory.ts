import { mockDeployments } from './mockDeploymentData'

import type { Deployment, DeploymentEvent } from '@/shared/contracts'

const histories = new Map<string, DeploymentEvent[]>()
let nextEventId = 1

export function appendMockDeploymentEvent(
  deployment: Deployment,
  type: DeploymentEvent['type'],
  fromPhase: DeploymentEvent['fromPhase'],
  reason: string | null = null,
  timestamp = new Date().toISOString()
) {
  const history = histories.get(deployment.id) ?? []
  const toPhase = deployment.status
  if (toPhase !== 'in_progress' && toPhase !== 'completed' && toPhase !== 'aborted') return
  history.push({
    id: nextEventId++,
    sequence: history.length + 1,
    type,
    fromPhase,
    toPhase,
    actor: deployment.createdBy,
    reason,
    timestamp,
  })
  histories.set(deployment.id, history)
}

export function appendMockDeploymentCreation(
  deployment: Deployment,
  timestamp = new Date().toISOString()
) {
  const type =
    deployment.status === 'in_progress'
      ? 'CANARY_STARTED'
      : deployment.status === 'aborted'
        ? 'ABORTED'
        : 'COMPLETED'
  appendMockDeploymentEvent(deployment, type, null, null, timestamp)
}

// Seed only the fixture's known phase; do not invent its earlier transitions.
for (const deployment of mockDeployments) {
  appendMockDeploymentCreation(deployment, deployment.deployedAt ?? deployment.createdAt)
}

export function getMockDeploymentEvents(id: string): DeploymentEvent[] {
  return (histories.get(id) ?? []).map((event) => ({ ...event }))
}
