import { describe, expect, it, vi } from 'vitest'
import { createInFlightQueue } from '@/utils/inFlightQueue'

describe('createInFlightQueue', () => {
  it('queues one follow-up run when a second trigger happens during in-flight', () => {
    const onQueued = vi.fn()
    const queue = createInFlightQueue(onQueued)

    expect(queue.tryStart()).toBe(true)
    expect(queue.isInFlight()).toBe(true)

    expect(queue.tryStart()).toBe(false)
    expect(queue.tryStart()).toBe(false)

    queue.finish()

    expect(onQueued).toHaveBeenCalledTimes(1)
    expect(queue.isInFlight()).toBe(false)
  })

  it('does not call onQueued when no queued trigger exists', () => {
    const onQueued = vi.fn()
    const queue = createInFlightQueue(onQueued)

    expect(queue.tryStart()).toBe(true)
    queue.finish()

    expect(onQueued).not.toHaveBeenCalled()
  })
})
