import { App } from 'antd'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { addGraph, selectCurrentProcess } from '../store/editorSlice'
import type { BaseNode, UnifiedProcessDefinition } from '../types/flowDefinition'
import { isBpmnNode, isNodeCopyable, isTbbpmNode } from '../types/typeGuards'

import { type ClipboardData, isClipboardDataFresh } from './clipboardData'
import {
  clearDesignerClipboard,
  readDesignerClipboard,
  writeDesignerClipboard,
} from './clipboardStorage'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { generateId } from '@/authoring/designer/identifiers'
import { toError } from '@/shared/errors'
import { logger } from '@/shared/logging/logger'

const OFFSET_X = 50
const OFFSET_Y = 50

type ClipboardSelection =
  | { status: 'empty' }
  | { status: 'protected' }
  | { status: 'ready'; data: ClipboardData }

function isDesignerNodeCopyable(node: BaseNode): boolean {
  if (isTbbpmNode(node)) return isNodeCopyable(node)
  if (isBpmnNode(node)) {
    return node.type !== 'bpmn:StartEvent' && node.type !== 'bpmn:EndEvent'
  }
  return false
}

function collectClipboardSelection(
  currentProcess: UnifiedProcessDefinition,
  nodeIds: string[]
): ClipboardSelection {
  const nodesToCopy = currentProcess.nodes.filter((node) => nodeIds.includes(node.id))
  if (nodesToCopy.length === 0) return { status: 'empty' }

  const validNodes = nodesToCopy.filter(isDesignerNodeCopyable)
  if (validNodes.length === 0) return { status: 'protected' }

  const copiedIds = new Set(validNodes.map((node) => node.id))
  let expanded = true
  while (expanded) {
    expanded = false
    for (const node of currentProcess.nodes) {
      if (node.parentId && copiedIds.has(node.parentId) && !copiedIds.has(node.id)) {
        copiedIds.add(node.id)
        expanded = true
      }
    }
  }
  const nodes = currentProcess.nodes
    .filter((node) => copiedIds.has(node.id))
    .map((node) => ({
      ...node,
      parentId: node.parentId && copiedIds.has(node.parentId) ? node.parentId : undefined,
    }))
  const messageIds = new Set(
    nodes.map((node) => (isBpmnNode(node) ? node.properties.messageRef : undefined))
  )
  return {
    status: 'ready',
    data: {
      modelType: currentProcess.type,
      nodes,
      connections: currentProcess.connections.filter(
        (edge) => copiedIds.has(edge.sourceId) && copiedIds.has(edge.targetId)
      ),
      messages: (currentProcess.messages ?? []).filter((message) => messageIds.has(message.id)),
      timestamp: Date.now(),
    },
  }
}

function cloneClipboardGraph(clipboardData: ClipboardData, pasteSuffix: string) {
  const nodeIds = new Map(clipboardData.nodes.map((node) => [node.id, generateId()]))
  const connectionIds = new Map(clipboardData.connections.map((edge) => [edge.id, generateId()]))
  const messageIds = new Map(clipboardData.messages.map((message) => [message.id, generateId()]))
  const nodes = clipboardData.nodes.map((node) => {
    const cloned: BaseNode = {
      ...node,
      id: nodeIds.get(node.id)!,
      parentId: node.parentId ? nodeIds.get(node.parentId) : undefined,
      name: node.name ? `${node.name}${pasteSuffix}` : node.name,
      position: {
        x: node.position.x + OFFSET_X,
        y: node.position.y + OFFSET_Y,
      },
      properties: structuredClone(node.properties),
    }
    if (isBpmnNode(cloned)) {
      if (cloned.properties.default)
        cloned.properties.default = connectionIds.get(cloned.properties.default)
      if (cloned.properties.messageRef) {
        cloned.properties.messageRef =
          messageIds.get(cloned.properties.messageRef) ?? cloned.properties.messageRef
      }
    }
    return cloned
  })
  return {
    nodes,
    connections: clipboardData.connections.map((edge) => ({
      ...edge,
      id: connectionIds.get(edge.id)!,
      sourceId: nodeIds.get(edge.sourceId)!,
      targetId: nodeIds.get(edge.targetId)!,
      waypoints: edge.waypoints?.map((point) => ({
        x: point.x + OFFSET_X,
        y: point.y + OFFSET_Y,
      })),
    })),
    messages: clipboardData.messages.map((message) => ({
      ...message,
      id: messageIds.get(message.id)!,
    })),
  }
}

export function useClipboard() {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const currentProcess = useAppSelector(selectCurrentProcess)

  const copyNodes = useCallback(
    (nodeIds: string[]) => {
      if (!currentProcess) {
        message.warning(t('designer.clipboard.nothingToCopy'))
        return
      }

      const selection = collectClipboardSelection(currentProcess, nodeIds)
      if (selection.status === 'empty') {
        message.warning(t('designer.clipboard.selectNodeFirst'))
        return
      }
      if (selection.status === 'protected') {
        message.warning(t('designer.clipboard.cannotCopyStartEnd'))
        return
      }

      try {
        writeDesignerClipboard(selection.data)
        message.success(t('designer.clipboard.copied', { count: selection.data.nodes.length }))
      } catch (error) {
        message.error(t('designer.clipboard.copyFailed'))
        logger.error('Copy nodes failed:', {}, toError(error))
      }
    },
    [currentProcess, message, t]
  )

  const pasteNodes = useCallback(() => {
    if (!currentProcess) {
      message.warning(t('designer.clipboard.openProcessFirst'))
      return
    }

    const clipboardData = readDesignerClipboard()
    if (!clipboardData) {
      message.warning(t('designer.clipboard.empty'))
      return
    }

    try {
      if (!isClipboardDataFresh(clipboardData)) {
        message.warning(t('designer.clipboard.expired'))
        clearDesignerClipboard()
        return
      }

      const pasteSuffix = t('designer.clipboard.pasteSuffix')
      if (clipboardData.modelType !== currentProcess.type) {
        message.warning(t('designer.clipboard.pasteFailed'))
        return
      }
      const graph = cloneClipboardGraph(clipboardData, pasteSuffix)
      dispatch(addGraph(graph))
      message.success(t('designer.clipboard.pasted', { count: graph.nodes.length }))
    } catch (error) {
      message.error(t('designer.clipboard.pasteFailed'))
      logger.error('Paste nodes failed:', {}, toError(error))
    }
  }, [currentProcess, dispatch, message, t])

  const duplicateNodes = useCallback(
    (nodeIds: string[]) => {
      if (!currentProcess) return
      const selection = collectClipboardSelection(currentProcess, nodeIds)
      if (selection.status === 'empty') {
        message.warning(t('designer.clipboard.selectNodeFirst'))
        return
      }
      if (selection.status === 'protected') {
        message.warning(t('designer.clipboard.cannotCopyStartEnd'))
        return
      }
      const graph = cloneClipboardGraph(selection.data, t('designer.clipboard.pasteSuffix'))
      dispatch(addGraph(graph))
      message.success(t('designer.clipboard.pasted', { count: graph.nodes.length }))
    },
    [currentProcess, dispatch, message, t]
  )

  const clearClipboard = useCallback(() => {
    clearDesignerClipboard()
    message.info(t('designer.clipboard.cleared'))
  }, [message, t])

  const hasClipboardData = useCallback((): boolean => {
    const clipboardData = readDesignerClipboard()
    if (!clipboardData) return false

    return clipboardData.modelType === currentProcess?.type && isClipboardDataFresh(clipboardData)
  }, [currentProcess?.type])

  return {
    copyNodes,
    duplicateNodes,
    pasteNodes,
    clearClipboard,
    hasClipboardData,
  }
}
