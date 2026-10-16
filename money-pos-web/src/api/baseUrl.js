export const getApiBaseUrl = () => window.location.protocol === 'file:'
    ? 'http://127.0.0.1:9101/money-pos'
    : import.meta.env.VITE_BASE_URL;
