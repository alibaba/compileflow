import i18n from './index'

type DateValue = Date | number | string | null | undefined
type DateFormat = 'date' | 'dateTime' | 'time'
type Locale = 'en-US' | 'zh-CN'

const formatters: Record<Locale, Record<DateFormat, Intl.DateTimeFormat>> = {
  'en-US': {
    date: new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }),
    dateTime: new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'medium' }),
    time: new Intl.DateTimeFormat('en-US', { timeStyle: 'medium' }),
  },
  'zh-CN': {
    date: new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }),
    dateTime: new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }),
    time: new Intl.DateTimeFormat('zh-CN', { timeStyle: 'medium' }),
  },
}

function format(value: DateValue, kind: DateFormat): string {
  if (value == null || value === '') return '-'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const locale: Locale = i18n.resolvedLanguage === 'en' ? 'en-US' : 'zh-CN'
  return formatters[locale][kind].format(date)
}

export const formatDate = (value: DateValue) => format(value, 'date')
export const formatDateTime = (value: DateValue) => format(value, 'dateTime')
export const formatTime = (value: DateValue) => format(value, 'time')
