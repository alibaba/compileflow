import { MoonOutlined, SunOutlined } from '@ant-design/icons'
import { Button, Tooltip } from 'antd'
import { useTranslation } from 'react-i18next'

import { useTheme } from '../contexts/ThemeContext'

interface ThemeToggleProps {
  style?: React.CSSProperties
}

const ThemeToggle: React.FC<ThemeToggleProps> = ({ style }) => {
  const { t } = useTranslation()
  const { theme, toggleTheme } = useTheme()
  const label = t(theme === 'light' ? 'theme.dark' : 'theme.light')

  return (
    <Tooltip title={label}>
      <Button
        type="text"
        icon={theme === 'light' ? <MoonOutlined /> : <SunOutlined />}
        onClick={toggleTheme}
        aria-label={label}
        style={{
          color: 'inherit',
          ...style,
        }}
      />
    </Tooltip>
  )
}

export default ThemeToggle
