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

test('captcha refresh control provides clear loading and refresh feedback', () => {
  assert.match(loginPage, /class="captcha-loading-icon"/)
  assert.match(loginPage, /class="captcha-refresh-hint"/)
  assert.match(loginPage, />换一张</)
})

test('captcha card aligns with the current input height', () => {
  assert.match(loginPage, /\.captcha-image\s*{[\s\S]*?height:\s*62px;/)
})

test('captcha image remains fully visible inside the card', () => {
  assert.match(loginPage, /\.captcha-image img\s*{[\s\S]*?object-fit:\s*contain;/)
})
