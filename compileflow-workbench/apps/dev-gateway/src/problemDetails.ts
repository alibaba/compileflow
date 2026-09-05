import type { Request, Response } from 'express'

interface ProblemDetails {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  code: string
}

function createProblemDetails(
  instance: string,
  status: number,
  code: string,
  title: string,
  detail: string
): ProblemDetails {
  return {
    type: `urn:compileflow:problem:${code.toLowerCase().replaceAll('_', '-')}`,
    title,
    status,
    detail,
    instance,
    code,
  }
}

export function sendProblem(
  req: Request,
  res: Response,
  status: number,
  code: string,
  title: string,
  detail: string
): void {
  res.status(status)
  res.setHeader('Content-Type', 'application/problem+json')
  res.json(createProblemDetails(req.path, status, code, title, detail))
}
