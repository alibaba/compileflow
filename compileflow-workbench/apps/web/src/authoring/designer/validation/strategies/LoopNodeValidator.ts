import {
  isJavaClassName,
  isJavaIdentifier,
  isReservedJavaIdentifier,
} from '../../types/javaIdentifiers'
import { isValidLoopIterationLimit } from '../../types/loopLimits'
import type { TbbpmNode } from '../../types/tbbpm'
import { findDirectJavaMutation } from '../javaConditionExpression'
import {
  BaseNodeValidator,
  type ValidationContext,
  type ValidationIssue,
} from '../NodeValidationStrategy'

/** Validates the variant-specific loop contract. */
export class LoopNodeValidator extends BaseNodeValidator {
  validate(node: TbbpmNode, ctx: ValidationContext): ValidationIssue[] {
    const issues: ValidationIssue[] = []
    const children = ctx.nodes.filter((candidate) => candidate.parentId === node.id)
    if (children.length === 0) {
      issues.push(this.issue(node.id, 'loop.missingBody', 'error', 'property'))
    }
    const ends = children.filter((child) => child.type === 'end')
    if (
      ends.length === 1 &&
      ctx.connections.some((connection) => connection.sourceId === ends[0].id)
    ) {
      issues.push(this.issue(node.id, 'loop.endHasOutgoing', 'error', 'structure'))
    }
    if (node.type === 'while') {
      this.validateWhile(node, ctx, issues)
    } else {
      this.validateForEach(node, ctx, issues)
    }
    return issues
  }

  private validateWhile(node: TbbpmNode, ctx: ValidationContext, issues: ValidationIssue[]): void {
    const condition = text(node.properties.condition)
    if (!condition) {
      issues.push(this.issue(node.id, 'loop.missingCondition', 'error', 'property'))
    } else {
      const mutation = findDirectJavaMutation(condition)
      if (mutation) {
        issues.push(
          this.issue(node.id, 'condition.directMutation', 'error', 'property', {
            operator: mutation,
          })
        )
      }
    }
    if (!isValidLoopIterationLimit(node.properties.maxIterations)) {
      issues.push(this.issue(node.id, 'loop.invalidMaxIterations', 'error', 'property'))
    }
    this.validateLocal(node, 'index', node.properties.index, ctx, issues, false)
  }

  private validateForEach(
    node: TbbpmNode,
    ctx: ValidationContext,
    issues: ValidationIssue[]
  ): void {
    const visible = enclosingLoopVariables(node, ctx)
    const collection = text(node.properties.collection)
    if (!collection) {
      issues.push(this.issue(node.id, 'loop.missingCollection', 'error', 'property'))
    } else if (!ctx.processVariableNames.has(collection) && !visible.has(collection)) {
      issues.push(
        this.issue(node.id, 'loop.unknownCollection', 'error', 'property', { name: collection })
      )
    }
    this.validateLocal(node, 'item', node.properties.item, ctx, issues, true)
    this.validateLocal(node, 'index', node.properties.index, ctx, issues, false)
    if (text(node.properties.item) === text(node.properties.index)) {
      issues.push(this.issue(node.id, 'loop.itemIndexCollision', 'error', 'property'))
    }
    const itemType = text(node.properties.itemType)
    if (!itemType || !isJavaClassName(itemType)) {
      issues.push(this.issue(node.id, 'loop.invalidItemType', 'error', 'property'))
    }
    this.validateOutput(node, ctx, issues)
    if (
      node.properties.execution === 'parallel' &&
      ctx.nodes.some(
        (candidate) => candidate.type === 'break' && nearestLoopId(candidate, ctx) === node.id
      )
    ) {
      issues.push(this.issue(node.id, 'loop.parallelBreakUnsupported', 'error', 'structure'))
    }
  }

  private validateOutput(node: TbbpmNode, ctx: ValidationContext, issues: ValidationIssue[]): void {
    const output = node.properties.output
    if (!output) return
    for (const name of [text(output.target), text(output.source)]) {
      if (!name || !ctx.processVariableNames.has(name)) {
        issues.push(
          this.issue(node.id, 'loop.unknownOutputReference', 'error', 'property', { name })
        )
      }
    }
    if (output.target === output.source) {
      issues.push(this.issue(node.id, 'loop.outputReferenceCollision', 'error', 'property'))
    }
    const outputSource = ctx.processVariables.get(text(output.source))
    if (outputSource?.inOutType && outputSource.inOutType !== 'inner') {
      issues.push(this.issue(node.id, 'loop.outputSourceNotInner', 'error', 'property'))
    }
  }

  private validateLocal(
    node: TbbpmNode,
    property: string,
    raw: unknown,
    ctx: ValidationContext,
    issues: ValidationIssue[],
    required: boolean
  ): void {
    const value = text(raw)
    if (!value) {
      if (required) issues.push(this.issue(node.id, 'loop.missingItem', 'error', 'property'))
      return
    }
    if (!isJavaIdentifier(value) || isReservedJavaIdentifier(value)) {
      issues.push(
        this.issue(node.id, 'loop.invalidLocalVariable', 'error', 'property', { property, value })
      )
    }
    if (ctx.processVariableNames.has(value) || enclosingLoopVariables(node, ctx).has(value)) {
      issues.push(
        this.issue(node.id, 'loop.variableShadowing', 'error', 'property', { name: value })
      )
    }
  }
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function enclosingLoopVariables(node: TbbpmNode, ctx: ValidationContext): Set<string> {
  const variables = new Set<string>()
  let parentId = node.parentId
  while (parentId) {
    const parent = ctx.nodes.find((candidate) => candidate.id === parentId)
    if (!parent) break
    if (parent.type === 'foreach') {
      if (text(parent.properties.item)) variables.add(text(parent.properties.item))
      if (text(parent.properties.index)) variables.add(text(parent.properties.index))
    } else if (parent.type === 'while' && text(parent.properties.index)) {
      variables.add(text(parent.properties.index))
    }
    parentId = parent.parentId
  }
  return variables
}

function nearestLoopId(node: TbbpmNode, ctx: ValidationContext): string | undefined {
  let parentId = node.parentId
  while (parentId) {
    const parent = ctx.nodes.find((candidate) => candidate.id === parentId)
    if (!parent) return undefined
    if (parent.type === 'while' || parent.type === 'foreach') return parent.id
    parentId = parent.parentId
  }
  return undefined
}
