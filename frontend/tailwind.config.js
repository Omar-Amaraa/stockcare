/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#eef4fe", 100: "#d9e6fd", 200: "#b3ccfb", 300: "#82a9f5",
          400: "#5385ee", 500: "#2f66e4", 600: "#1D5FD6", 700: "#1a4bb0",
          800: "#1a3f8c", 900: "#1a376f", 950: "#0e2344"
        },
        accent: {
          50: "#ecfdf5", 100: "#d1fae5", 200: "#a7f3d0", 300: "#6ee7b7",
          400: "#34d399", 500: "#10b981", 600: "#059669", 700: "#047857",
          800: "#065f46", 900: "#064e3b"
        }
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"]
      },
      boxShadow: {
        soft: "0 1px 2px rgba(16,24,40,.06), 0 1px 3px rgba(16,24,40,.1)",
        card: "0 1px 3px rgba(16,24,40,.08), 0 8px 24px -12px rgba(16,24,40,.18)"
      }
    }
  },
  plugins: []
};
