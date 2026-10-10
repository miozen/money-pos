const pad = (value, size) => String(value).padStart(size, '0')

/**
 * Generates a human-readable idempotency key from the cashier workstation's local clock.
 * The prefix identifies the business action; the 17-digit suffix is yyyyMMddHHmmssSSS.
 */
export const localRequestId = (prefix, date = new Date()) => (
  `${prefix}${date.getFullYear()}${pad(date.getMonth() + 1, 2)}${pad(date.getDate(), 2)}` +
  `${pad(date.getHours(), 2)}${pad(date.getMinutes(), 2)}${pad(date.getSeconds(), 2)}${pad(date.getMilliseconds(), 3)}`
)
