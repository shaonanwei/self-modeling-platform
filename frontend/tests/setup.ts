import { vi } from 'vitest'

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.stubGlobal('ResizeObserver', ResizeObserverStub)
Object.defineProperty(Element.prototype, 'scrollIntoView', {
  configurable: true,
  value: vi.fn()
})
