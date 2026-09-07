import { App } from 'antd'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { addGraph, selectCurrentProcess } from '../store/editorSlice'
import type { BaseNode } from '../types/flowDefinition'
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

function isDesignerNodeCopyable(node: BaseNode): boolean {
  if (isTbbpmNode(node)) return isNodeCopyable(node)
  if (isBpmnNode(node)) {
    return node.type !== 'bpmn:StartEvent' && node.type !== 'bpmn:EndEvent'
  }
  return false
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

      const nodesToCopy = currentProcess.nodes.filter((node) => nodeIds.includes(node.id))

      if (nodesToCopy.length === 0) {
        message.warning(t('designer.clipboard.selectNodeFirst'))
        return
      }

      const validNodes = nodesToCopy.filter((node) => isDesignerNodeCopyable(node))

      if (validNodes.length === 0) {
        message.warning(t('designer.clipboard.cannotCopyStartEnd'))
        return
      }

      const copiedIds = new Set(validNodes.map((node) => node.id))
      // A container's graph includes all descendants, including nested entry/exit nodes.
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
      const clipboardData: ClipboardData = {
        modelType: currentProcess.type,
        nodes,
        connections: currentProcess.connections.filter(
          (edge) => copiedIds.has(edge.sourceId) && copiedIds.has(edge.targetId)
        ),
        messages: (currentProcess.messages ?? []).filter((message) => messageIds.has(message.id)),
        timestamp: Date.now(),
      }

      try {
        writeDesignerClipboard(clipboardData)
        message.success(t('designer.clipboard.copied', { count: nodes.length }))
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
      const nodeIds = new Map(clipboardData.nodes.map((node) => [node.id, generateId()]))
      const connectionIds = new Map(
        clipboardData.connections.map((edge) => [edge.id, generateId()])
      )
      const messageIds = new Map(
        clipboardData.messages.map((message) => [message.id, generateId()])
      )
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
      const connections = clipboardData.connections.map((edge) => ({
        ...edge,
        id: connectionIds.get(edge.id)!,
        sourceId: nodeIds.get(edge.sourceId)!,
        targetId: nodeIds.get(edge.targetId)!,
        waypoints: edge.waypoints?.map((point) => ({
          x: point.x + OFFSET_X,
          y: point.y + OFFSET_Y,
        })),
      }))
      const messages = clipboardData.messages.map((message) => ({
        ...message,
        id: messageIds.get(message.id)!,
      }))
      dispatch(addGraph({ nodes, connections, messages }))
      message.success(t('designer.clipboard.pasted', { count: nodes.length }))
    } catch (error) {
      message.error(t('designer.clipboard.pasteFailed'))
      logger.error('Paste nodes failed:', {}, toError(error))
    }
  }, [currentProcess, dispatch, message, t])

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
    pasteNodes,
    clearClipboard,
    hasClipboardData,
  }
}
