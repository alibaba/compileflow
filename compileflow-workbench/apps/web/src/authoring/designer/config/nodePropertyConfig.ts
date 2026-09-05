import { lazy } from 'react'

import type { NodePropertyTabConfig } from '../types/propertyTabs'
import type { TbbpmNodeType } from '../types/tbbpm'

const AutoTaskPropertiesTab = lazy(() =>
  import('../components/properties/AutoTaskPropertiesTab').then((m) => ({
    default: m.AutoTaskPropertiesTab,
  }))
)
const ScriptTaskTbbpmPropertiesTab = lazy(() =>
  import('../components/properties/ScriptTaskTbbpmPropertiesTab').then((m) => ({
    default: m.ScriptTaskTbbpmPropertiesTab,
  }))
)
const ExclusivePropertiesTab = lazy(() =>
  import('../components/properties/ExclusivePropertiesTab').then((m) => ({
    default: m.ExclusivePropertiesTab,
  }))
)
const ParallelPropertiesTab = lazy(() =>
  import('../components/properties/ParallelPropertiesTab').then((m) => ({
    default: m.ParallelPropertiesTab,
  }))
)
const InclusivePropertiesTab = lazy(() =>
  import('../components/properties/InclusivePropertiesTab').then((m) => ({
    default: m.InclusivePropertiesTab,
  }))
)
const BpmCallPropertiesTab = lazy(() =>
  import('../components/properties/BpmCallPropertiesTab').then((m) => ({
    default: m.BpmCallPropertiesTab,
  }))
)
const SubBpmPropertiesTab = lazy(() =>
  import('../components/properties/SubBpmPropertiesTab').then((m) => ({
    default: m.SubBpmPropertiesTab,
  }))
)
const WaitTaskPropertiesTab = lazy(() =>
  import('../components/properties/WaitTaskPropertiesTab').then((m) => ({
    default: m.WaitTaskPropertiesTab,
  }))
)
const TimerTaskPropertiesTab = lazy(() =>
  import('../components/properties/TimerTaskPropertiesTab').then((m) => ({
    default: m.TimerTaskPropertiesTab,
  }))
)
const LoopPropertiesTab = lazy(() =>
  import('../components/properties/LoopPropertiesTab').then((m) => ({
    default: m.LoopPropertiesTab,
  }))
)
const BreakPropertiesTab = lazy(() =>
  import('../components/properties/BreakPropertiesTab').then((m) => ({
    default: m.BreakPropertiesTab,
  }))
)
const ContinuePropertiesTab = lazy(() =>
  import('../components/properties/ContinuePropertiesTab').then((m) => ({
    default: m.ContinuePropertiesTab,
  }))
)
const NotePropertiesTab = lazy(() =>
  import('../components/properties/NotePropertiesTab').then((m) => ({
    default: m.NotePropertiesTab,
  }))
)

const NODE_PROPERTY_CONFIGS: Record<TbbpmNodeType, NodePropertyTabConfig | null> = {
  start: null,
  end: null,
  note: {
    key: 'specific',
    labelKey: 'designer.properties.tab.note',
    component: NotePropertiesTab,
  },
  autoTask: {
    key: 'specific',
    labelKey: 'designer.properties.tab.task',
    component: AutoTaskPropertiesTab,
  },
  scriptTask: {
    key: 'specific',
    labelKey: 'designer.properties.tab.script',
    component: ScriptTaskTbbpmPropertiesTab,
  },
  exclusive: {
    key: 'specific',
    labelKey: 'designer.properties.tab.exclusive',
    component: ExclusivePropertiesTab,
  },
  parallel: {
    key: 'specific',
    labelKey: 'designer.properties.tab.parallel',
    component: ParallelPropertiesTab,
  },
  inclusive: {
    key: 'specific',
    labelKey: 'designer.properties.tab.inclusive',
    component: InclusivePropertiesTab,
  },
  subBpm: {
    key: 'specific',
    labelKey: 'designer.properties.tab.subBpm',
    component: SubBpmPropertiesTab,
  },
  bpmCall: {
    key: 'specific',
    labelKey: 'designer.properties.tab.bpmCall',
    component: BpmCallPropertiesTab,
  },
  waitTask: {
    key: 'specific',
    labelKey: 'designer.properties.tab.wait',
    component: WaitTaskPropertiesTab,
  },
  waitEventTask: {
    key: 'specific',
    labelKey: 'designer.properties.tab.wait',
    component: WaitTaskPropertiesTab,
  },
  timerTask: {
    key: 'specific',
    labelKey: 'designer.properties.tab.timer',
    component: TimerTaskPropertiesTab,
  },
  while: {
    key: 'specific',
    labelKey: 'designer.properties.tab.loop',
    component: LoopPropertiesTab,
  },
  foreach: {
    key: 'specific',
    labelKey: 'designer.properties.tab.loop',
    component: LoopPropertiesTab,
  },
  break: {
    key: 'specific',
    labelKey: 'designer.properties.tab.break',
    component: BreakPropertiesTab,
  },
  continue: {
    key: 'specific',
    labelKey: 'designer.properties.tab.continue',
    component: ContinuePropertiesTab,
  },
}

export function getNodePropertyConfig(nodeType: TbbpmNodeType): NodePropertyTabConfig | null {
  return NODE_PROPERTY_CONFIGS[nodeType] ?? null
}
