/* Minimal react-i18next mock: t() returns the key so assertions are deterministic. */
export const useTranslation = () => ({
    t: key => key,
    i18n: {language: 'en', changeLanguage: () => Promise.resolve()}
});
