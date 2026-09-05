import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'

import enAuthoring from './en/authoring'
import enCommon from './en/common'
import enLearn from './en/learn'
import enOperate from './en/operate'
import zhAuthoring from './zh/authoring'
import zhCommon from './zh/common'
import zhLearn from './zh/learn'
import zhOperate from './zh/operate'

const resources = {
  en: {
    translation: { ...enCommon, ...enAuthoring, ...enOperate, ...enLearn },
  },
  zh: {
    translation: { ...zhCommon, ...zhAuthoring, ...zhOperate, ...zhLearn },
  },
}

export const i18nReady = i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'zh',
    supportedLngs: ['en', 'zh'],
    load: 'languageOnly',

    interpolation: {
      escapeValue: false,
    },

    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'compileflow:language',
    },
  })
  .then(() => undefined)

export default i18n
