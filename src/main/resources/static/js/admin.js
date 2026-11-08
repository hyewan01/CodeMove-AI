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

	/* ---------- 견적서 뷰어: 확대/축소(60~180%) · 100% 초과 시 끌어서 이동 · 두 손가락 확대 ---------- */
	$$('[data-doc-viewer]').forEach((viewer) => {
		const viewport = $('[data-doc-viewport]', viewer);
		const pageEl = $('[data-doc-page]', viewer);
		const label = $('[data-zoom-reset]', viewer);
		let zoom = 100, pan = { x: 0, y: 0 }, drag = null, pinch = null;
		const clamp = (v) => Math.min(180, Math.max(60, v));
		/* 종이 크기: 영역(여백 12px씩) 안에 견적서 비율(1488:2103) 그대로 맞춤 (CSS aspect + max-width 는 container-type 과 함께 쓰면 무시됨) */
		const fit = () => {
			const w = viewport.clientWidth - 24;
			const h = viewport.clientHeight - 24;
			pageEl.style.width = `${Math.max(0, Math.min(w, h * 1488 / 2103))}px`;
			pageEl.style.height = 'auto';
		};
		fit();
		window.addEventListener('resize', fit);
		const apply = () => {
			if (zoom <= 100) pan = { x: 0, y: 0 };
			pageEl.style.transform = `translate(${pan.x}px, ${pan.y}px) scale(${zoom / 100})`;
			label.textContent = `${zoom}%`;
		};
		$$('[data-zoom-step]', viewer).forEach((b) => b.addEventListener('click', () => { zoom = clamp(zoom + Number(b.dataset.zoomStep)); apply(); }));
		label.addEventListener('click', () => { zoom = 100; apply(); });
		const isField = (t) => t.closest('input, textarea, select, button');
		viewport.addEventListener('pointerdown', (e) => {
			if (e.pointerType !== 'mouse' || isField(e.target)) return;
			drag = { x: e.clientX, y: e.clientY, px: pan.x, py: pan.y };
			viewport.setPointerCapture(e.pointerId);
			viewport.classList.replace('cursor-grab', 'cursor-grabbing');
		});
		viewport.addEventListener('pointermove', (e) => {
			if (e.pointerType !== 'mouse' || !drag || zoom <= 100) return;
			pan = { x: drag.px + e.clientX - drag.x, y: drag.py + e.clientY - drag.y };
			apply();
		});
		const endDrag = (e) => {
			if (e.pointerType !== 'mouse') return;
			drag = null;
			if (viewport.hasPointerCapture(e.pointerId)) viewport.releasePointerCapture(e.pointerId);
			viewport.classList.replace('cursor-grabbing', 'cursor-grab');
		};
		viewport.addEventListener('pointerup', endDrag);
		viewport.addEventListener('pointercancel', endDrag);
		viewport.addEventListener('touchstart', (e) => {
			if (e.touches.length === 2) {
				const [a, b] = e.touches;
				pinch = { d: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY), zoom, cx: (a.clientX + b.clientX) / 2, cy: (a.clientY + b.clientY) / 2, px: pan.x, py: pan.y };
				drag = null;
			} else if (e.touches.length === 1 && !isField(e.target)) {
				drag = { x: e.touches[0].clientX, y: e.touches[0].clientY, px: pan.x, py: pan.y };
			}
		}, { passive: true });
		viewport.addEventListener('touchmove', (e) => {
			if (e.touches.length === 2 && pinch) {
				const [a, b] = e.touches;
				zoom = clamp(Math.round(pinch.zoom * Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY) / pinch.d));
				if (zoom > 100) pan = { x: pinch.px + (a.clientX + b.clientX) / 2 - pinch.cx, y: pinch.py + (a.clientY + b.clientY) / 2 - pinch.cy };
				apply();
			} else if (e.touches.length === 1 && drag && zoom > 100) {
				pan = { x: drag.px + e.touches[0].clientX - drag.x, y: drag.py + e.touches[0].clientY - drag.y };
				apply();
			}
		}, { passive: true });
		viewport.addEventListener('touchend', (e) => {
			pinch = null;
			drag = e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY, px: pan.x, py: pan.y } : null;
		});
	});

	/* ---------- 수정 가능한 견적서: 옵션 ■□ 선택, 작업내용, 운반방법 전/후, 품목 행 추가·삭제 ---------- */
	const ON = ['font-black', 'text-black'];
	$$('[data-estimate-sheet]').forEach((sheet) => {
		if (!$('input', sheet)) return;
		const setBox = (btn, on, offClass) => {
			btn.classList.remove(...ON, offClass);
			btn.classList.add(...(on ? ON : [offClass]));
			const box = $('span', btn);
			if (box) box.textContent = on ? '■' : '□';
		};
		/* 옵션: 같은 걸 다시 누르면 해제 */
		$$('[data-option-row]', sheet).forEach((row) => {
			const input = $('[data-option-value]', row);
			$$('[data-choice]', row).forEach((btn) => btn.addEventListener('click', () => {
				input.value = input.value === btn.dataset.choice ? '' : btn.dataset.choice;
				$$('[data-choice]', row).forEach((b) => setBox(b, b.dataset.choice === input.value, 'text-[#69716b]'));
			}));
		});
		/* 작업내용: 하나만 선택 */
		$$('[data-single]', sheet).forEach((group) => {
			const input = $('[data-single-value]', group);
			$$('[data-choice]', group).forEach((btn) => btn.addEventListener('click', () => {
				input.value = btn.dataset.choice;
				$$('[data-choice]', group).forEach((b) => setBox(b, b === btn, 'text-[#69716b]'));
			}));
		});
		/* 운반방법: 전/후, 다시 누르면 해제 */
		const UNDER = ['font-black', 'text-black', 'underline', 'decoration-[.18cqw]', 'underline-offset-[.3cqw]'];
		$$('[data-transport]', sheet).forEach((group) => {
			const input = $('[data-transport-value]', group);
			$$('[data-choice]', group).forEach((btn) => btn.addEventListener('click', () => {
				input.value = input.value === btn.dataset.choice ? '' : btn.dataset.choice;
				$$('[data-choice]', group).forEach((b) => {
					b.className = b.dataset.choice === input.value ? UNDER.join(' ') : 'text-[#8a918c]';
				});
			}));
		});
		/* 품목 행 1~14 */
		const rows = $('[data-item-rows]', sheet);
		const count = $('[data-row-count]', sheet);
		const label = $('[data-row-label]', sheet);
		const steps = $$('[data-row-step]', sheet);
		const refreshRows = () => {
			const n = rows.children.length;
			count.value = n;
			label.textContent = `${n}행`;
			steps.forEach((b) => (b.disabled = Number(b.dataset.rowStep) < 0 ? n <= 1 : n >= 14));
		};
		steps.forEach((b) => b.addEventListener('click', () => {
			if (Number(b.dataset.rowStep) < 0) {
				rows.lastElementChild.remove();
			} else {
				const row = rows.lastElementChild.cloneNode(true);
				const base = rows.children.length * 4;
				$$('input', row).forEach((input, i) => {
					const col = Math.floor(i / 2);
					const isName = i % 2 === 0;
					input.value = '';
					input.name = `${isName ? 'itemNames' : 'itemQuantities'}[${base + col}]`;
					input.setAttribute('aria-label', `${rows.children.length + 1}행 ${col + 1}번째 ${isName ? '품목' : '수량'}`);
				});
				rows.appendChild(row);
			}
			refreshRows();
		}));
		refreshRows();
	});

	/* ---------- 확인 모달: data-confirm-open="이름" → data-confirm="이름" (취소: data-confirm-close) ---------- */
	$$('[data-confirm-open]').forEach((btn) => btn.addEventListener('click', () => {
		const modal = $(`[data-confirm="${btn.dataset.confirmOpen}"]`);
		if (modal) modal.hidden = false;
	}));
	$$('[data-confirm-close]').forEach((btn) => btn.addEventListener('click', () => (btn.closest('[data-confirm]').hidden = true)));

	/* ---------- YY.MM.DD 입력 서식 (프로토타입 formatShortDateInput) ---------- */
	$$('[data-short-date]').forEach((input) => input.addEventListener('input', (e) => {
		const digits = input.value.replace(/\D/g, '').slice(0, 6);
		const deleting = e.inputType && e.inputType.startsWith('delete');
		let v = digits;
		if (digits.length >= 5) v = `${digits.slice(0, 2)}.${digits.slice(2, 4)}.${digits.slice(4)}`;
		else if (digits.length >= 3) v = `${digits.slice(0, 2)}.${digits.slice(2)}${digits.length === 4 && !deleting ? '.' : ''}`;
		else if (digits.length === 2 && !deleting) v = `${digits}.`;
		input.value = v;
	}));

	window.AdminUI = { toast: showToast, openPanel };
})();
