/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
  // Cho phép Tailwind và MUI cùng tồn tại không xung đột
  corePlugins: {
    preflight: false,
  },
}
