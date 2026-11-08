/* 관리자 화면 공통 스크립트 (프로토타입 App 셸 동작) */
(function () {
	const $ = (sel, root = document) => root.querySelector(sel);
	const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

	/* ---------- 알림 패널 · 통합 메뉴 검색 : data-open="notifications|search" ---------- */
	const openPanel = (name) => {
		const panel = $(`[data-panel="${name}"]`);
		if (!panel) return;
		panel.hidden = false;
		if (name === 'notifications') {
			const dot = $('[data-unread-dot]');
			if (dot) {
				dot.hidden = true;
				fetch(dot.dataset.readUrl, { method: 'POST' }).catch(() => { /* 다음 화면에서 다시 표시될 뿐 */ });
			}
		}
		if (name === 'search') $('[data-search-input]')?.focus();
	};
	const closePanel = (panel) => {
		panel.hidden = true;
		if (panel.dataset.panel === 'search') {
			const input = $('[data-search-input]');
			input.value = '';
			filterMenus('');
		}
	};
	$$('[data-open]').forEach((btn) => {
		if (btn.dataset.open === 'drawer') return;
		btn.addEventListener('click', () => openPanel(btn.dataset.open));
	});
	$$('[data-panel] [data-close]').forEach((btn) =>
		btn.addEventListener('click', () => closePanel(btn.closest('[data-panel]'))));

	/* ---------- 초성 검색 (프로토타입 getKoreanInitials) ---------- */
	const INITIALS = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
	const toInitials = (value) => [...value].map((ch) => {
		const code = ch.charCodeAt(0) - 0xac00;
		return code >= 0 && code <= 11171 ? INITIALS[Math.floor(code / 588)] : ch;
	}).join('');
	const filterMenus = (query) => {
		const q = query.trim().replace(/\s/g, '');
		const isInitial = q.length > 0 && [...q].every((ch) => INITIALS.includes(ch));
		let count = 0;
		$$('[data-search-item]').forEach((item) => {
			const label = item.dataset.label.replace(/\s/g, '');
			const match = !q || (isInitial ? toInitials(label).startsWith(q) : label.toLowerCase().includes(q.toLowerCase()));
			item.hidden = !match;
			if (match) count++;
		});
		const countEl = $('[data-search-count]');
		if (countEl) {
			countEl.hidden = !q;
			countEl.textContent = `${count}개 결과`;
		}
		const empty = $('[data-search-empty]');
		if (empty) empty.hidden = count > 0;
		const hint = $('[data-search-hint]');
		if (hint) hint.hidden = !!q;
	};
	$('[data-search-input]')?.addEventListener('input', (e) => filterMenus(e.target.value));

	/* ---------- 전체 메뉴 (아래에서 올라오는 시트) ---------- */
	const drawer = $('[data-drawer]');
	const sheet = $('[data-drawer-sheet]');
	const setDrawer = (open) => {
		if (!drawer) return;
		drawer.classList.toggle('visible', open);
		drawer.classList.toggle('invisible', !open);
		drawer.setAttribute('aria-hidden', String(!open));
		sheet.classList.toggle('translate-y-0', open);
		sheet.classList.toggle('translate-y-full', !open);
	};
	$$('[data-open="drawer"]').forEach((btn) => btn.addEventListener('click', () => setDrawer(true)));
	$('[data-drawer-close]')?.addEventListener('click', () => setDrawer(false));

	document.addEventListener('keydown', (e) => {
		if (e.key !== 'Escape') return;
		$$('[data-panel]').filter((p) => !p.hidden).forEach(closePanel);
		setDrawer(false);
	});

	/* ---------- 자동 메시징 토스트 (10초 후 닫힘, 오른쪽으로 밀면 닫힘) ---------- */
	const toast = $('[data-toast]');
	let toastTimer = null;
	const hideToast = () => {
		if (toastTimer) clearTimeout(toastTimer);
		if (toast) toast.hidden = true;
	};
	const showToast = (title, message, href) => {
		if (!toast) return;
		$('[data-toast-title]', toast).textContent = title;
		$('[data-toast-message]', toast).textContent = message;
		$('[data-toast-time]', toast).textContent =
			new Date().toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true });
		$('[data-toast-link]', toast).href = href || '#';
		toast.hidden = false;
		if (toastTimer) clearTimeout(toastTimer);
		toastTimer = setTimeout(hideToast, 10000);
	};
	if (toast) {
		const link = $('[data-toast-link]', toast);
		let startX = null;
		link.addEventListener('pointerdown', (e) => { startX = e.clientX; });
		link.addEventListener('pointermove', (e) => {
			if (startX === null) return;
			const dx = Math.max(0, e.clientX - startX);
			link.style.transform = dx ? `translateX(${dx}px)` : '';
			link.style.opacity = dx ? String(Math.max(0, 1 - dx / 160)) : '';
		});
		const end = (e) => {
			if (startX === null) return;
			const dx = e.clientX - startX;
			startX = null;
			link.style.transform = '';
			link.style.opacity = '';
			if (dx > 80) {
				e.preventDefault();
				hideToast();
			}
		};
		link.addEventListener('pointerup', end);
		link.addEventListener('pointercancel', () => { startX = null; link.style.transform = ''; link.style.opacity = ''; });
	}

	/* 서버에서 넘긴 루틴 토스트 (조건 발생 직후 화면) */
	if (toast && toast.dataset.initialTitle) {
		showToast(toast.dataset.initialTitle, toast.dataset.initialMessage, toast.dataset.initialHref);
	}

	/* ---------- 작은 팝오버 열고 닫기 : data-toggle="이름" ↔ data-toggle-target="이름" ---------- */
	$$('[data-toggle]').forEach((btn) => btn.addEventListener('click', () => {
		const target = $(`[data-toggle-target="${btn.dataset.toggle}"]`);
		if (!target) return;
		target.hidden = !target.hidden;
		btn.setAttribute('aria-expanded', String(!target.hidden));
		btn.classList.toggle('is-open', !target.hidden);
		if (!target.hidden) $('input, textarea', target)?.focus();
	}));

	/* select 변경 시 바로 조회 : <select data-auto-submit> */
	$$('select[data-auto-submit]').forEach((sel) => sel.addEventListener('change', () => sel.form.submit()));

	window.AdminUI = { toast: showToast, openPanel };
})();
