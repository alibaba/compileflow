import { AutoTaskPropertiesTab } from '../components/properties/AutoTaskPropertiesTab'
import { BpmCallPropertiesTab } from '../components/properties/BpmCallPropertiesTab'
import { BreakPropertiesTab } from '../components/properties/BreakPropertiesTab'
import { ContinuePropertiesTab } from '../components/properties/ContinuePropertiesTab'
import { ExclusivePropertiesTab } from '../components/properties/ExclusivePropertiesTab'
import { InclusivePropertiesTab } from '../components/properties/InclusivePropertiesTab'
import { LoopPropertiesTab } from '../components/properties/LoopPropertiesTab'
import { NotePropertiesTab } from '../components/properties/NotePropertiesTab'
import { ParallelPropertiesTab } from '../components/properties/ParallelPropertiesTab'
import { ScriptTaskTbbpmPropertiesTab } from '../components/properties/ScriptTaskTbbpmPropertiesTab'
import { SubBpmPropertiesTab } from '../components/properties/SubBpmPropertiesTab'
import { TimerTaskPropertiesTab } from '../components/properties/TimerTaskPropertiesTab'
import { WaitTaskPropertiesTab } from '../components/properties/WaitTaskPropertiesTab'
import type { NodePropertyTabConfig } from '../types/propertyTabs'
import type { TbbpmNodeType } from '../types/tbbpm'

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
