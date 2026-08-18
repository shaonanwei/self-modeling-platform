import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TestGreeting from './fixtures/TestGreeting.vue'

describe('Vue SFC 测试链路', () => {
  it('mounts a real SFC through plugin-vue', () => {
    const wrapper = mount(TestGreeting, { props: { message: '测试成功' } })

    expect(wrapper.find('.greeting').text()).toBe('测试成功')
  })
})
