/* ==============================
   SafeVaani CLONE - MAIN JAVASCRIPT
   Clean-room replica of safevaani.in
============================== */

/* ----- Header scroll state ----- */
const header = document.getElementById('site-header');
const updateHeader = () => header?.classList.toggle('scrolled', window.scrollY > 10);
window.addEventListener('scroll', updateHeader, { passive: true });
updateHeader();

/* ----- Hamburger / Mobile Drawer ----- */
const burger = document.getElementById('hd-burger');
const sheet  = document.getElementById('hd-sheet');

const toggleDrawer = (open) => {
  if (!burger || !sheet) return;
  burger.setAttribute('aria-expanded', String(open));
  burger.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
  sheet.hidden = !open;
  document.documentElement.style.overflow = open ? 'hidden' : '';
};
burger?.addEventListener('click', () => {
  toggleDrawer(burger.getAttribute('aria-expanded') !== 'true');
});
sheet?.querySelectorAll('a').forEach(a => a.addEventListener('click', () => toggleDrawer(false)));
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && burger?.getAttribute('aria-expanded') === 'true') {
    toggleDrawer(false);
    burger.focus();
  }
});

/* ----- Scroll-triggered reveals ----- */
const revealEls = document.querySelectorAll('.scroll-reveal');
const revealObs = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('is-in');
      revealObs.unobserve(entry.target);
    }
  });
}, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });

revealEls.forEach(el => {
  if (el.getBoundingClientRect().top < window.innerHeight) {
    el.classList.add('is-in');
  } else {
    revealObs.observe(el);
  }
});

/* ----- Magnetic button effect ----- */
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
if (!reduceMotion) {
  document.querySelectorAll('.btn').forEach(btn => {
    if (btn.dataset.magnetic) return;
    btn.dataset.magnetic = '1';
    let currentX = 0, currentY = 0, targetX = 0, targetY = 0, rafId = null;
    const lerp = (a, b, t) => a + (b - a) * t;
    const animate = () => {
      currentX = lerp(currentX, targetX, 0.12);
      currentY = lerp(currentY, targetY, 0.12);
      btn.style.transform = `translate(${currentX}px, ${currentY}px)`;
      rafId = (Math.abs(currentX - targetX) > 0.01 || Math.abs(currentY - targetY) > 0.01)
        ? requestAnimationFrame(animate) : null;
    };
    btn.addEventListener('mousemove', e => {
      const rect = btn.getBoundingClientRect();
      targetX = (e.clientX - rect.left - rect.width / 2) * 0.25;
      targetY = (e.clientY - rect.top - rect.height / 2) * 0.28;
      if (!rafId) rafId = requestAnimationFrame(animate);
    });
    btn.addEventListener('mouseleave', () => {
      targetX = 0; targetY = 0;
      rafId = requestAnimationFrame(animate);
    });
  });
}

/* ----- Pricing cycle toggle ----- */
(() => {
  const wrap = document.querySelector('.pr-cycle');
  if (!wrap) return;
  wrap.addEventListener('click', e => {
    const btn = e.target.closest('.pc-opt');
    if (!btn) return;
    const cycle = btn.dataset.cycle;
    wrap.querySelectorAll('.pc-opt').forEach(b => b.classList.toggle('is-on', b === btn));
    document.querySelectorAll('[data-m][data-y]').forEach(el => {
      const val = el.getAttribute(cycle === 'y' ? 'data-y' : 'data-m');
      if (val !== null) el.textContent = val;
    });
  });
})();

/* ----- FAQ Accordion ----- */
const faqItems = document.querySelectorAll('.faq-item');
faqItems.forEach(item => {
  const btn = item.querySelector('.faq-question');
  if (!btn) return;
  btn.addEventListener('click', () => {
    const isActive = item.classList.contains('active');
    // Close all
    faqItems.forEach(i => {
      i.classList.remove('active');
      i.querySelector('.faq-question')?.setAttribute('aria-expanded', 'false');
    });
    // Toggle current
    if (!isActive) {
      item.classList.add('active');
      btn.setAttribute('aria-expanded', 'true');
    }
  });
});

/* ----- FAQ show more ----- */
const faqAllBtn = document.getElementById('faq-all');
faqAllBtn?.addEventListener('click', () => {
  document.querySelectorAll('.faq-more').forEach(el => { el.hidden = false; });
  faqAllBtn.textContent = 'Showing all questions';
  faqAllBtn.disabled = true;
});

/* ----- Privacy Pledge ----- */
const pledge = document.getElementById('privacy-pledge');
const ackBtn = document.getElementById('pp-ack');
const SEEN_KEY = 'safevaani:privacy-pledge-seen-v1';
const hasSeen = () => { try { return localStorage.getItem(SEEN_KEY) === '1'; } catch { return false; } };
const markSeen = () => { try { localStorage.setItem(SEEN_KEY, '1'); } catch {} };

if (pledge && ackBtn && !hasSeen()) {
  const dismiss = () => {
    markSeen();
    pledge.classList.remove('pp-show');
    document.documentElement.classList.remove('pp-open');
    setTimeout(() => pledge.setAttribute('hidden', ''), 360);
  };
  pledge.removeAttribute('hidden');
  document.documentElement.classList.add('pp-open');
  requestAnimationFrame(() => requestAnimationFrame(() => pledge.classList.add('pp-show')));
  ackBtn.addEventListener('click', dismiss);
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && pledge.classList.contains('pp-show')) dismiss();
  });
}

/* ----- Smooth scroll for anchor links ----- */
document.querySelectorAll('a[href^="#"]').forEach(link => {
  link.addEventListener('click', e => {
    const id = link.getAttribute('href');
    if (!id || id === '#') return;
    const target = document.querySelector(id);
    if (!target) return;
    e.preventDefault();
    const top = target.getBoundingClientRect().top + window.scrollY - 68;
    window.scrollTo({ top, behavior: 'smooth' });
  });
});

/* ----- Countdown number animation ----- */
const countdownEl = document.getElementById('countdown-num');
if (countdownEl) {
  let count = 3;
  setInterval(() => {
    count = count <= 1 ? 3 : count - 1;
    countdownEl.textContent = count;
  }, 1000);
}

/* ----- Trigger word cycling ----- */
const triggerEl = document.querySelector('.trigger-word');
if (triggerEl) {
  const words = ['"help"', '"bachao"', '"madad"'];
  let i = 0;
  setInterval(() => {
    i = (i + 1) % words.length;
    triggerEl.style.opacity = '0';
    triggerEl.style.transform = 'scale(.92)';
    setTimeout(() => {
      triggerEl.textContent = words[i];
      triggerEl.style.opacity = '1';
      triggerEl.style.transform = 'scale(1)';
    }, 300);
  }, 2200);
}

/* ----- Parallax on hero background dots ----- */
if (!reduceMotion) {
  document.addEventListener('mousemove', e => {
    const dots = document.querySelectorAll('.dot');
    const mx = (e.clientX / window.innerWidth - 0.5) * 2;
    const my = (e.clientY / window.innerHeight - 0.5) * 2;
    dots.forEach((dot, i) => {
      const depth = (i % 3 + 1) * 4;
      dot.style.transform = `translate(${mx * depth}px, ${my * depth}px)`;
    });
  });
}

console.log('%c🔴 SafeVaani Clone loaded ', 'background:#FDDB10;color:#1F2236;font-weight:bold;font-size:14px;padding:4px 8px;border-radius:4px;');
