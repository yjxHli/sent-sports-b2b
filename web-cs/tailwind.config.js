/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      colors: {
        // 与 theme.css 的 CSS 变量同源，Tailwind 侧只做引用，不重复定义色值
        'sent-primary': 'var(--sent-primary)',
        'sent-primary-dark': 'var(--sent-primary-dark)',
        'sent-accent': 'var(--sent-accent)',
        'cs-shell': 'var(--cs-shell)',
        'cs-shell-2': 'var(--cs-shell-2)',
      },
      boxShadow: {
        'cs-card': 'var(--cs-shadow-card)',
        'cs-pop': 'var(--cs-shadow-pop)',
      },
    },
  },
  plugins: [],
};
