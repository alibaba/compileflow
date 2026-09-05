import styles from './ProductShot.module.css'

/**
 * Product stage — studio-lit designer visual.
 */
export function ProductShot({
  className,
  src = '/images/product-designer-hero.png',
  alt = 'CompileFlow designer',
}: {
  className?: string
  src?: string
  alt?: string
}) {
  return (
    <div className={[styles.stage, className].filter(Boolean).join(' ')} data-testid="product-shot">
      <div className={styles.bloom} aria-hidden="true" />
      <figure className={styles.figure}>
        <img className={styles.shot} src={src} alt={alt} draggable={false} />
      </figure>
    </div>
  )
}
