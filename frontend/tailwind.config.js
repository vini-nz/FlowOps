/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        flowops: {
          50: '#f0f7ff',
          600: '#1d6fe0',
          700: '#1758b3',
          900: '#0f2f5c'
        }
      }
    }
  },
  plugins: []
}
