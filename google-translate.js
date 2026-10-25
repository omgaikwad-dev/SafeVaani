/**
 * SafeVaani - Google Translate Integration
 * Seamless multilingual translation toggle for Dashboards & Website
 */

(function () {
  'use strict';

  // Supported languages config
  const LANGUAGES = [
    { code: 'en', name: 'English (EN)' },
    { code: 'hi', name: 'हिन्दी (Hindi)' },
    { code: 'mr', name: 'मराठी (Marathi)' },
    { code: 'gu', name: 'ગુજરાતી (Gujarati)' },
    { code: 'ta', name: 'தமிழ் (Tamil)' },
    { code: 'te', name: 'తెలుగు (Telugu)' },
    { code: 'kn', name: 'ಕನ್ನಡ (Kannada)' },
    { code: 'bn', name: 'বাংলা (Bengali)' },
    { code: 'pa', name: 'ਪੰਜਾਬੀ (Punjabi)' }
  ];

  // Inject required CSS for clean styling and removing Google banner layout shifts
  function injectStyles() {
    if (document.getElementById('sv-google-translate-styles')) return;

    const style = document.createElement('style');
    style.id = 'sv-google-translate-styles';
    style.textContent = `
      /* Hide Google Translate top banner, tooltip, and balloons */
      .goog-te-banner-frame.skiptranslate,
      .goog-te-banner-frame,
      iframe.goog-te-banner-frame,
      #goog-gt-tt,
      .goog-te-balloon-frame {
        display: none !important;
        visibility: hidden !important;
      }
      body {
        top: 0px !important;
        position: static !important;
      }
      .goog-text-highlight {
        background: none !important;
        box-shadow: none !important;
      }
      #google_translate_element {
        display: none !important;
      }

      /* SafeVaani Language Toggle UI */
      .lang-toggle-wrap {
        position: relative;
        display: inline-flex;
        align-items: center;
        vertical-align: middle;
      }
      .lang-toggle-box {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        background: var(--dash-card-bg, #FFFFFF);
        border: 1.5px solid var(--dash-border, #E3D9C6);
        border-radius: 999px;
        padding: 5px 10px;
        font-size: 0.82rem;
        font-weight: 600;
        color: var(--dash-ink, #1F2236);
        transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      }
      .lang-toggle-box:hover,
      .lang-toggle-box:focus-within {
        border-color: #FDDB10;
        box-shadow: 0 2px 8px rgba(253, 219, 16, 0.35);
      }
      .lang-globe-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: var(--dash-ink, #1F2236);
        flex-shrink: 0;
      }
      .sv-lang-select {
        border: none;
        background: transparent;
        color: inherit;
        font-family: inherit;
        font-size: 0.82rem;
        font-weight: 600;
        cursor: pointer;
        outline: none;
        padding-right: 4px;
        -webkit-appearance: none;
        -moz-appearance: none;
        appearance: none;
        background-image: url("data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='10'%20height='6'%20viewBox='0%200%2010%206'%3E%3Cpath%20d='M1%201l4%204%204-4'%20stroke='%231F2236'%20stroke-width='1.5'%20fill='none'%20stroke-linecap='round'%20stroke-linejoin='round'/%3E%3C/svg%3E");
        background-repeat: no-repeat;
        background-position: right 2px center;
        padding-right: 18px;
      }
      .sv-lang-select option {
        background: #FFFFFF;
        color: #1F2236;
        font-weight: 500;
      }

      /* Dark mode support */
      html[data-theme="dark"] .lang-toggle-box {
        background: #151D2C;
        border-color: #26334D;
        color: #ECEEF2;
      }
      html[data-theme="dark"] .lang-globe-icon {
        color: #ECEEF2;
      }
      html[data-theme="dark"] .sv-lang-select {
        color: #ECEEF2;
        background-image: url("data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='10'%20height='6'%20viewBox='0%200%2010%206'%3E%3Cpath%20d='M1%201l4%204%204-4'%20stroke='%23ECEEF2'%20stroke-width='1.5'%20fill='none'%20stroke-linecap='round'%20stroke-linejoin='round'/%3E%3C/svg%3E");
      }
      html[data-theme="dark"] .sv-lang-select option {
        background: #151D2C;
        color: #ECEEF2;
      }
    `;
    document.head.appendChild(style);
  }

  // Get cookie by name
  function getCookie(name) {
    const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]+)'));
    return match ? decodeURIComponent(match[2]) : null;
  }

  // Retrieve active language code
  function getCurrentLang() {
    const cookie = getCookie('googtrans');
    if (cookie) {
      const parts = cookie.split('/');
      if (parts.length >= 3 && parts[2]) {
        return parts[2].toLowerCase();
      }
    }
    const stored = localStorage.getItem('safevaani_preferred_lang');
    if (stored) return stored.toLowerCase();
    return 'en';
  }

  // Set Google Translate cookie and change language
  function setLanguage(langCode) {
    langCode = (langCode || 'en').toLowerCase();
    localStorage.setItem('safevaani_preferred_lang', langCode);

    const domain = window.location.hostname;
    const cookiePath = '/';

    if (langCode === 'en') {
      // Revert to original
      document.cookie = `googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=${cookiePath};`;
      document.cookie = `googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=${cookiePath}; domain=${domain};`;
      document.cookie = `googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=${cookiePath}; domain=.${domain};`;
      document.cookie = `googtrans=/en/en; path=${cookiePath};`;
    } else {
      document.cookie = `googtrans=/en/${langCode}; path=${cookiePath};`;
      document.cookie = `googtrans=/en/${langCode}; path=${cookiePath}; domain=${domain};`;
      document.cookie = `googtrans=/en/${langCode}; path=${cookiePath}; domain=.${domain};`;
    }

    // Update select elements across page
    document.querySelectorAll('.sv-lang-select').forEach((sel) => {
      sel.value = langCode;
    });

    // Check if Google Translate combo exists in the DOM
    const combo = document.querySelector('.goog-te-combo');
    if (combo) {
      combo.value = langCode;
      combo.dispatchEvent(new Event('change'));
    } else {
      // Reload so translation initiates with new cookie
      window.location.reload();
    }
  }

  // Sync all select elements with current language
  function syncSelectors() {
    const current = getCurrentLang();
    document.querySelectorAll('.sv-lang-select').forEach((sel) => {
      sel.value = current;
    });
  }

  // Bind change events to all .sv-lang-select elements
  function bindSelectors() {
    document.querySelectorAll('.sv-lang-select').forEach((sel) => {
      if (!sel.dataset.bound) {
        sel.dataset.bound = 'true';
        sel.addEventListener('change', function (e) {
          setLanguage(e.target.value);
        });
      }
    });
    syncSelectors();
  }

  // Ensure map and interactive critical elements are not broken by translation DOM shifts
  function protectCriticalElements() {
    const protectedIds = ['parent-safety-map', 'cad-map', 'audio-stream-player'];
    protectedIds.forEach((id) => {
      const el = document.getElementById(id);
      if (el && !el.classList.contains('notranslate')) {
        el.classList.add('notranslate');
      }
    });
  }

  // Ensure hidden translate element exists
  function ensureTranslateElement() {
    if (!document.getElementById('google_translate_element')) {
      const div = document.createElement('div');
      div.id = 'google_translate_element';
      div.style.display = 'none';
      document.body.appendChild(div);
    }
  }

  // Load Google Translate script
  function loadGoogleScript() {
    if (window.google && window.google.translate) {
      return;
    }
    window.googleTranslateElementInit = function () {
      new window.google.translate.TranslateElement(
        {
          pageLanguage: 'en',
          includedLanguages: 'en,hi,mr,gu,ta,te,kn,bn,pa',
          autoDisplay: false
        },
        'google_translate_element'
      );

      // Periodically check combo to sync current lang
      let checks = 0;
      const interval = setInterval(() => {
        checks++;
        const combo = document.querySelector('.goog-te-combo');
        if (combo || checks > 30) {
          clearInterval(interval);
          syncSelectors();
        }
      }, 200);
    };

    const existingScript = document.querySelector('script[src*="translate.google.com"]');
    if (!existingScript) {
      const script = document.createElement('script');
      script.type = 'text/javascript';
      script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
      script.async = true;
      document.head.appendChild(script);
    }
  }

  // Public API
  window.SafeVaaniTranslate = {
    setLanguage: setLanguage,
    getCurrentLang: getCurrentLang,
    languages: LANGUAGES
  };

  // Initialize on DOM ready
  function init() {
    injectStyles();
    ensureTranslateElement();
    protectCriticalElements();
    bindSelectors();
    loadGoogleScript();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
