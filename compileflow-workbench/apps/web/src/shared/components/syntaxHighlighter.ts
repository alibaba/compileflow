import { PrismLight } from 'react-syntax-highlighter'
import bash from 'react-syntax-highlighter/dist/esm/languages/prism/bash'
import java from 'react-syntax-highlighter/dist/esm/languages/prism/java'
import javascript from 'react-syntax-highlighter/dist/esm/languages/prism/javascript'
import json from 'react-syntax-highlighter/dist/esm/languages/prism/json'
import markup from 'react-syntax-highlighter/dist/esm/languages/prism/markup'
import sql from 'react-syntax-highlighter/dist/esm/languages/prism/sql'
import typescript from 'react-syntax-highlighter/dist/esm/languages/prism/typescript'
import yaml from 'react-syntax-highlighter/dist/esm/languages/prism/yaml'

PrismLight.registerLanguage('bash', bash)
PrismLight.registerLanguage('bpmn', markup)
PrismLight.registerLanguage('java', java)
PrismLight.registerLanguage('javascript', javascript)
PrismLight.registerLanguage('json', json)
PrismLight.registerLanguage('sql', sql)
PrismLight.registerLanguage('typescript', typescript)
PrismLight.registerLanguage('xml', markup)
PrismLight.registerLanguage('yaml', yaml)

export default PrismLight
