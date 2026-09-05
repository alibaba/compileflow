import type { Graph } from '@antv/x6'

export interface ExportOptions {
  backgroundColor: string
  filename: string
}

async function exportToPng(graph: Graph, options: ExportOptions): Promise<void> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    graph.toPNG(
      (value: string) => (value ? resolve(value) : reject(new Error('PNG generation failed'))),
      {
        backgroundColor: options.backgroundColor,
        copyStyles: true,
        padding: 20,
        quality: 1,
      }
    )
  })

  const link = document.createElement('a')
  link.href = dataUrl
  link.download = `${options.filename}.png`
  link.click()
}

async function exportToSvg(graph: Graph, options: ExportOptions): Promise<void> {
  const svg = await new Promise<string>((resolve, reject) => {
    graph.toSVG(
      (value: string) => (value ? resolve(value) : reject(new Error('SVG generation failed'))),
      { copyStyles: true, preserveDimensions: true, serializeImages: true }
    )
  })

  let output = svg
  if (options.backgroundColor !== 'transparent') {
    const svgTag = svg.match(/<svg[^>]*>/)?.[0]
    const viewBox = svgTag?.match(/viewBox="([^"]+)"/)?.[1].split(' ')
    if (svgTag && viewBox) {
      const [, , width, height] = viewBox
      output = svg.replace(
        svgTag,
        `${svgTag}<rect width="${width}" height="${height}" fill="${options.backgroundColor}"/>`
      )
    }
  }

  const url = URL.createObjectURL(new Blob([output], { type: 'image/svg+xml' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `${options.filename}.svg`
  link.click()
  URL.revokeObjectURL(url)
}

export async function exportBoth(graph: Graph, options: ExportOptions): Promise<void> {
  await exportToPng(graph, options)
  await exportToSvg(graph, options)
}
