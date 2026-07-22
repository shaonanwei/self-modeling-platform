import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const dialogPath = fileURLToPath(
  new URL('../src/components/model/StepEditDialog.vue', import.meta.url)
)
const dialogSource = readFileSync(dialogPath, 'utf8')

function getEditUpdateBranch(handlerStart, handlerEnd) {
  const start = dialogSource.indexOf(handlerStart)
  const end = dialogSource.indexOf(handlerEnd, start)
  assert.notEqual(start, -1, `missing handler: ${handlerStart}`)
  assert.notEqual(end, -1, `missing handler end: ${handlerEnd}`)

  const handler = dialogSource.slice(start, end)
  const match = handler.match(/else if \(props\.editStep\) \{([\s\S]*?)\n\s*\} else \{/)
  assert.ok(match, `missing edit branch in ${handlerStart}`)
  return match[1]
}

test('basic info save only submits basic info fields in edit mode', () => {
  const branch = getEditUpdateBranch('const handleSaveAndClose', 'const handlePrev')

  assert.match(branch, /stepName:\s*form\.stepName/)
  assert.match(branch, /stepDesc:\s*form\.stepDesc/)
  assert.doesNotMatch(branch, /stepConfig:/)
  assert.doesNotMatch(branch, /stepType:/)
})

test('SQL save only submits step config in edit mode', () => {
  const branch = getEditUpdateBranch('const handleSubmit', '</script>')

  assert.match(branch, /stepConfig:\s*stepConfigStr/)
  assert.doesNotMatch(branch, /stepName:/)
  assert.doesNotMatch(branch, /stepDesc:/)
  assert.doesNotMatch(branch, /stepType:/)
})
