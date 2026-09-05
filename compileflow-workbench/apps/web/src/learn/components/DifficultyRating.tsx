import styles from './DifficultyRating.module.css'

interface DifficultyRatingProps {
  className?: string
  label: string
  value: number
}

export default function DifficultyRating({ className, label, value }: DifficultyRatingProps) {
  return (
    <span className={`${styles.rating} ${className ?? ''}`} role="img" aria-label={label}>
      {Array.from({ length: 5 }, (_, index) => (
        <span
          key={index}
          className={index < value ? undefined : styles.inactive}
          aria-hidden="true"
        >
          ★
        </span>
      ))}
    </span>
  )
}
