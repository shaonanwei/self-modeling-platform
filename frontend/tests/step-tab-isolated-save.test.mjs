import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const dialogPath = fileURLToPath(
  new URL('../src/components/model/StepEditDialog.vue', import.meta.url)
)
const dialogSource = readFileSync(dialogPath, 'utf8')

function getSourceBlock(startMarker, endMarker) {
  const start = dialogSource.indexOf(startMarker)
  const end = dialogSource.indexOf(endMarker, start)
  assert.notEqual(start, -1, `missing source block: ${startMarker}`)
  assert.notEqual(end, -1, `missing source block end: ${endMarker}`)
  return dialogSource.slice(start, end)
}

test('next saves basic info before opening SQL configuration', () => {
  const handler = getSourceBlock('const handleNext', 'const handleSaveAndClose')
  const saveIndex = handler.indexOf('await saveBasicInfo()')
  const navigationIndex = handler.indexOf('currentStep.value = 1')

  assert.notEqual(saveIndex, -1, 'next must persist basic info')
  assert.notEqual(navigationIndex, -1, 'next must open SQL configuration')
  assert.ok(saveIndex < navigationIndex, 'save must finish before navigation')
})

test('SQL step navigator uses the same persisted next action', () => {
  const navigator = getSourceBlock('<div class="steps-wrapper">', '<div class="wizard-content">')
  const sqlStep = navigator.slice(navigator.indexOf('SQL配置') - 400)

  assert.match(sqlStep, /@click="handleNext"/)
  assert.doesNotMatch(sqlStep, /@click="currentStep = 1"/)
})

test('basic info save creates append and inserted steps and stores returned id', () => {
  const helper = getSourceBlock('const saveBasicInfo', 'const handleNext')

  assert.match(helper, /await modelApi\.addStep\(props\.modelId, addData\)/)
  assert.match(helper, /await modelApi\.insertStep\(props\.modelId, insertData\)/)
  assert.match(helper, /persistedStepId\.value\s*=\s*response\.data\.id/g)
})

test('basic info save updates the persisted step without SQL fields', () => {
  const helper = getSourceBlock('const saveBasicInfo', 'const handleNext')
  const updateDataMatch = helper.match(
    /const updateData: Partial<ModelStep> = \{([\s\S]*?)\}\s*await modelApi\.updateStep/
  )

  assert.ok(updateDataMatch, 'missing basic info update payload')
  assert.match(updateDataMatch[1], /stepName:\s*form\.stepName/)
  assert.match(updateDataMatch[1], /stepDesc:\s*form\.stepDesc/)
  assert.doesNotMatch(updateDataMatch[1], /stepConfig:/)
  assert.doesNotMatch(updateDataMatch[1], /stepType:/)
  assert.match(helper, /modelApi\.updateStep\(props\.modelId, persistedStepId\.value, updateData\)/)
})

test('save and close reuses the same basic info persistence action', () => {
  const handler = getSourceBlock('const handleSaveAndClose', 'const handlePrev')

  assert.match(handler, /await saveBasicInfo\(\)/)
  assert.doesNotMatch(handler, /modelApi\.(addStep|insertStep|updateStep)/)
})

test('SQL save only updates the persisted step config', () => {
  const handler = getSourceBlock('const handleSubmit', '</script>')
  const updateDataMatch = handler.match(
    /const updateData: Partial<ModelStep> = \{([\s\S]*?)\}\s*await modelApi\.updateStep/
  )

  assert.ok(updateDataMatch, 'missing SQL update payload')
  assert.match(updateDataMatch[1], /stepConfig:\s*stepConfigStr/)
  assert.doesNotMatch(updateDataMatch[1], /stepName:/)
  assert.doesNotMatch(updateDataMatch[1], /stepDesc:/)
  assert.doesNotMatch(updateDataMatch[1], /stepType:/)
  assert.match(handler, /modelApi\.updateStep\(props\.modelId, persistedStepId\.value, updateData\)/)
  assert.doesNotMatch(handler, /modelApi\.(addStep|insertStep)/)
})

test('handleSubmit remains the only SQL tab path that updates a persisted step', () => {
  const script = getSourceBlock('<script setup', '</script>')
  const basicSave = getSourceBlock('const saveBasicInfo', 'const handleNext')
  const sqlSave = getSourceBlock('const handleSubmit', '</script>')
  const updateCalls = [...script.matchAll(/modelApi\.updateStep/g)]

  assert.equal(updateCalls.length, 2, '仅基础信息保存和 SQL 提交可以更新步骤')
  assert.equal([...basicSave.matchAll(/modelApi\.updateStep/g)].length, 1)
  assert.equal([...sqlSave.matchAll(/modelApi\.updateStep/g)].length, 1)
})

test('AI SQL messages survive tab navigation and clear only with the full dialog session', () => {
  const sqlTab = getSourceBlock('<div key="step-1"', '</transition>')
  const clearForm = getSourceBlock('function clearFormData', 'function fillForm')
  const previousStep = getSourceBlock('const handlePrev', 'const handleSubmit')

  assert.match(sqlTab, /:ai-messages="aiSqlMessages"/)
  assert.match(sqlTab, /@update:ai-messages="handleAiSqlMessagesUpdate"/)
  assert.match(clearForm, /aiSqlMessages\.value\s*=\s*\[\]/)
  assert.doesNotMatch(previousStep, /aiSqlMessages/)
})
