import { App } from 'antd'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { addNode, selectCurrentProcess } from '../store/editorSlice'
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

function isDesignerNode(node: BaseNode): boolean {
  return isTbbpmNode(node) || isBpmnNode(node)
}

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
      if (!currentProcess?.nodes) {
        message.warning(t('designer.clipboard.nothingToCopy'))
        return
      }

      const nodesToCopy = currentProcess.nodes
        .filter(isDesignerNode)
        .filter((node) => nodeIds.includes(node.id))

      if (nodesToCopy.length === 0) {
        message.warning(t('designer.clipboard.selectNodeFirst'))
        return
      }

      const validNodes = nodesToCopy.filter((node) => isDesignerNodeCopyable(node))

      if (validNodes.length === 0) {
        message.warning(t('designer.clipboard.cannotCopyStartEnd'))
        return
      }

      const clipboardData: ClipboardData = {
        nodes: validNodes,
        timestamp: Date.now(),
        source: currentProcess.code || 'unknown',
      }

      try {
        writeDesignerClipboard(clipboardData)
        message.success(t('designer.clipboard.copied', { count: validNodes.length }))
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

      if (!clipboardData.nodes || clipboardData.nodes.length === 0) {
        message.warning(t('designer.clipboard.noNodes'))
        return
      }

      const pasteSuffix = t('designer.clipboard.pasteSuffix')
      let pastedCount = 0

      for (const node of clipboardData.nodes) {
        if (!isDesignerNode(node)) {
          continue
        }

        const cloned = {
          ...node,
          id: generateId(),
          name: `${node.name}${pasteSuffix}`,
          position: {
            x: node.position.x + OFFSET_X,
            y: node.position.y + OFFSET_Y,
          },
          properties: node.properties ? JSON.parse(JSON.stringify(node.properties)) : {},
        }

        if (isTbbpmNode(cloned)) {
          dispatch(addNode(cloned))
          pastedCount += 1
        } else if (isBpmnNode(cloned)) {
          dispatch(addNode(cloned))
          pastedCount += 1
        }
      }

      if (pastedCount === 0) {
        message.warning(t('designer.clipboard.noNodes'))
        return
      }

      message.success(t('designer.clipboard.pasted', { count: pastedCount }))
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

    return isClipboardDataFresh(clipboardData) && clipboardData.nodes.length > 0
  }, [])

  return {
    copyNodes,
    pasteNodes,
    clearClipboard,
    hasClipboardData,
  }
}
