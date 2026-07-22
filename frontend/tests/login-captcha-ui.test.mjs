import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const loginPagePath = fileURLToPath(
  new URL('../src/pages/login/LoginPage.vue', import.meta.url)
)
const loginPage = readFileSync(loginPagePath, 'utf8')

test('captcha refresh control is a keyboard-accessible button', () => {
  assert.match(
    loginPage,
    /<button[\s\S]*?type="button"[\s\S]*?class="captcha-image"[\s\S]*?aria-label="刷新验证码"/
  )
})

test('captcha refresh control keeps loading feedback without hover text', () => {
  assert.match(loginPage, /class="captcha-loading-icon"/)
  assert.doesNotMatch(loginPage, /captcha-refresh-hint/)
  assert.doesNotMatch(loginPage, /RefreshRight/)
  assert.doesNotMatch(loginPage, /title="看不清？换一张"/)
})

test('captcha card aligns with the current input height', () => {
  assert.match(loginPage, /\.captcha-image\s*{[\s\S]*?height:\s*62px;/)
})

test('captcha image remains fully visible inside the card', () => {
  assert.match(loginPage, /\.captcha-image img\s*{[\s\S]*?object-fit:\s*contain;/)
})

test('captcha button uses transparent chrome', () => {
  const block = loginPage.match(
    /\.captcha-image\s*\{([\s\S]*?)\n\}/
  )?.[1]

  assert.ok(block, 'missing .captcha-image styles')
  assert.match(block, /padding:\s*0;/)
  assert.match(block, /border:\s*0;/)
  assert.match(block, /background:\s*transparent;/)
  assert.match(block, /box-shadow:\s*none;/)
  assert.doesNotMatch(loginPage, /\.captcha-image:hover\s*\{/)
})
