/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  darkMode: ["selector", '[data-theme="dark"]'],
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
        },
        /* Semantic tokens — values defined in styles.scss (:root / [data-theme="dark"]) */
        canvas: "rgb(var(--sc-canvas) / <alpha-value>)",
        surface: "rgb(var(--sc-surface) / <alpha-value>)",
        raised: "rgb(var(--sc-raised) / <alpha-value>)",
        line: "rgb(var(--sc-line) / <alpha-value>)",
        "line-strong": "rgb(var(--sc-line-strong) / <alpha-value>)",
        ink: {
          DEFAULT: "rgb(var(--sc-ink) / <alpha-value>)",
          soft: "rgb(var(--sc-ink-soft) / <alpha-value>)",
          mute: "rgb(var(--sc-ink-mute) / <alpha-value>)",
          faint: "rgb(var(--sc-ink-faint) / <alpha-value>)"
        }
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
        display: ["Plus Jakarta Sans", "Inter", "ui-sans-serif", "system-ui", "sans-serif"]
      },
      boxShadow: {
        soft: "0 1px 2px rgba(16,24,40,.06), 0 1px 3px rgba(16,24,40,.1)",
        card: "0 1px 3px rgba(16,24,40,.08), 0 8px 24px -12px rgba(16,24,40,.18)",
        lift: "0 4px 8px rgba(16,24,40,.08), 0 16px 32px -12px rgba(16,24,40,.22)",
        "glow-brand": "0 0 0 1px rgba(29,95,214,.15), 0 8px 24px -8px rgba(29,95,214,.35)"
      },
      keyframes: {
        "fade-up": {
          "0%": { opacity: "0", transform: "translateY(12px)" },
          "100%": { opacity: "1", transform: "translateY(0)" }
        },
        "fade-in": { "0%": { opacity: "0" }, "100%": { opacity: "1" } },
        "scale-in": {
          "0%": { opacity: "0", transform: "scale(.96)" },
          "100%": { opacity: "1", transform: "scale(1)" }
        },
        shimmer: {
          "0%": { backgroundPosition: "-400px 0" },
          "100%": { backgroundPosition: "400px 0" }
        },
        "pulse-dot": {
          "0%, 100%": { opacity: "1", transform: "scale(1)" },
          "50%": { opacity: ".55", transform: "scale(.85)" }
        }
      },
      animation: {
        "fade-up": "fade-up .45s cubic-bezier(0,0,.2,1) both",
        "fade-in": "fade-in .3s ease-out both",
        "scale-in": "scale-in .25s cubic-bezier(0,0,.2,1) both",
        shimmer: "shimmer 1.4s linear infinite",
        "pulse-dot": "pulse-dot 1.6s ease-in-out infinite"
      }
    }
  },
  plugins: []
};
