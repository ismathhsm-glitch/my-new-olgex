import { z } from 'zod'

export const passwordRules = z.string()
  .min(12, 'Use at least 12 characters.')
  .max(72, 'Use no more than 72 characters.')
  .regex(/[A-Z]/, 'Include an uppercase letter.')
  .regex(/[a-z]/, 'Include a lowercase letter.')
  .regex(/[0-9]/, 'Include a number.')
  .regex(/[^A-Za-z0-9\s]/, 'Include a symbol.')
  .regex(/^\S+$/, 'Do not use whitespace.')
