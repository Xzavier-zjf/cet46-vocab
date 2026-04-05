export function createInFlightQueue(onQueued) {
  let inFlight = false
  let queued = false

  return {
    tryStart() {
      if (inFlight) {
        queued = true
        return false
      }
      inFlight = true
      return true
    },
    finish() {
      inFlight = false
      if (!queued) return
      queued = false
      if (typeof onQueued === 'function') {
        onQueued()
      }
    },
    isInFlight() {
      return inFlight
    }
  }
}
