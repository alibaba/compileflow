import styles from './ProductShot.module.css'

/** Decorative product screenshot used in page headers. */
export function ProductShot({
  className,
  src = '/images/product-designer-hero.svg',
  alt = '',
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
