/** @type {import('tailwindcss').Config} */
import forms from '@tailwindcss/forms'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        navy: '#142B4D',
        sidebar: '#0D1D35',
        link: '#2458B8',
        canvas: '#F4F6F9',
        surface: '#FFFFFF',
        border: '#DCE3ED',
        ink: '#172B45',
        muted: '#627187',
        success: '#147653',
        warning: '#905B0B',
        danger: '#B53D3D',
        disabled: '#E7EBF1',
      },
      borderRadius: { card: '12px', field: '8px' },
      spacing: { 18: '4.5rem' },
      boxShadow: { card: '0 1px 2px rgb(20 43 77 / 4%)' },
      fontFamily: { sans: ['Inter', 'Segoe UI', 'sans-serif'] },
    },
  },
  plugins: [forms],
}
