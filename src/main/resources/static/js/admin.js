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
	/* 전체 서비스 접기/펼치기 (화면설계서 SCR-A-017) */
	$('[data-service-toggle]')?.addEventListener('click', (e) => {
		const btn = e.currentTarget;
		const open = btn.getAttribute('aria-expanded') !== 'true';
		btn.setAttribute('aria-expanded', String(open));
		$$('[data-service-item]').forEach((item) => (item.hidden = !open));
	});
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

	/*
	 * ---------- 견적서 뷰어: 확대/축소(60~300%) · 두 손가락 확대 ----------
	 * 확대하면 종이 크기 자체가 커지고(cqw 글자도 같이 커져 선명함) 영역 안 스크롤바로 이동.
	 * 휠은 견적서만 스크롤 (Shift = 좌우, Ctrl = 확대/축소). 글자는 끌어서 선택하되 한 칸 안에서만, 두 번 누르면 그 칸 복사
	 */
	$$('[data-doc-viewer]').forEach((viewer) => {
		const viewport = $('[data-doc-viewport]', viewer);
		const pageEl = $('[data-doc-page]', viewer);
		const label = $('[data-zoom-reset]', viewer);
		let zoom = 100, pinch = null, base = { w: 0, h: 0 };
		const clamp = (v) => Math.min(300, Math.max(60, v));
		const size = () => {
			pageEl.style.width = `${base.w * zoom / 100}px`;
			pageEl.style.height = `${base.h * zoom / 100}px`;
			label.textContent = `${zoom}%`;
		};
		/*
		 * 100% 종이: 높이는 영역 - 24px, 너비는 견적서 비율(1488:2103)과 영역 너비 중 작은 값
		 * (스크롤바가 생겨도 바뀌지 않게 영역의 바깥 크기 기준)
		 */
		const scroller = (() => {
			for (let p = viewer.parentElement; p; p = p.parentElement) if (/auto|scroll/.test(getComputedStyle(p).overflowY)) return p;
			return null;
		})();
		const fit = () => {
			/* 아래 스크롤바가 하단 메뉴에 가리지 않도록 영역 높이를 화면에 보이는 만큼으로 (최소 360px) */
			if (scroller) {
				const top = viewport.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop;
				viewport.style.height = `${Math.max(360, scroller.clientHeight - top - 16)}px`;
			}
			const w = viewport.offsetWidth - 24;
			const h = viewport.offsetHeight - 24;
			base = { w: Math.max(0, Math.min(w, h * 1488 / 2103)), h: Math.max(0, h) };
			size();
		};
		fit();
		window.addEventListener('resize', fit);
		/* 확대 기준점(fx, fy: 영역 안 좌표, 기본 가운데)이 그 자리에 머물도록 스크롤 보정 */
		const setZoom = (z, fx = viewport.clientWidth / 2, fy = viewport.clientHeight / 2) => {
			z = clamp(z);
			if (z === zoom) return;
			const rx = (viewport.scrollLeft + fx) / viewport.scrollWidth;
			const ry = (viewport.scrollTop + fy) / viewport.scrollHeight;
			zoom = z;
			size();
			viewport.scrollLeft = rx * viewport.scrollWidth - fx;
			viewport.scrollTop = ry * viewport.scrollHeight - fy;
		};
		$$('[data-zoom-step]', viewer).forEach((b) => b.addEventListener('click', () => setZoom(zoom + Number(b.dataset.zoomStep))));
		label.addEventListener('click', () => setZoom(100));
		/* 견적서 위에서 휠을 굴리면 화면 대신 견적서만 움직임 */
		viewport.addEventListener('wheel', (e) => {
			e.preventDefault();
			const unit = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? viewport.clientHeight : 1;
			if (e.ctrlKey) {
				const r = viewport.getBoundingClientRect();
				setZoom(zoom + (e.deltaY < 0 ? 10 : -10), e.clientX - r.left, e.clientY - r.top);
				return;
			}
			viewport.scrollLeft += (e.shiftKey ? e.deltaY : e.deltaX) * unit;
			viewport.scrollTop += (e.shiftKey ? 0 : e.deltaY) * unit;
		}, { passive: false });
		/* 글자 선택: 끌기를 시작한 칸 밖으로 선택이 넘어가면 그 칸 끝에서 멈춤 */
		let selCell = null;
		viewport.addEventListener('mousedown', (e) => { selCell = e.target.closest('[data-copy]'); });
		document.addEventListener('selectionchange', () => {
			const sel = window.getSelection();
			if (!selCell || !sel.rangeCount || !selCell.contains(sel.anchorNode) || selCell.contains(sel.focusNode)) return;
			const after = selCell.compareDocumentPosition(sel.focusNode) & Node.DOCUMENT_POSITION_FOLLOWING;
			sel.setBaseAndExtent(sel.anchorNode, sel.anchorOffset, selCell, after ? selCell.childNodes.length : 0);
		});
		/* 값 칸 두 번 누르기 → 그 칸 글자 전체를 선택하고 복사 */
		const hint = document.createElement('div');
		hint.className = 'pointer-events-none absolute bottom-6 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-full bg-[#263129]/85 px-3.5 py-2 text-[10px] font-bold text-white opacity-0 transition-opacity';
		viewer.append(hint);
		let hintTimer = null;
		viewport.addEventListener('dblclick', (e) => {
			const cell = e.target.closest('[data-copy]');
			if (!cell || !cell.textContent.trim()) return;
			const range = document.createRange();
			range.selectNodeContents(cell);
			const sel = window.getSelection();
			sel.removeAllRanges();
			sel.addRange(range);
			const text = cell.textContent.trim();
			const done = () => {
				hint.textContent = `복사했어요: ${text.length > 18 ? `${text.slice(0, 18)}…` : text}`;
				hint.classList.replace('opacity-0', 'opacity-100');
				clearTimeout(hintTimer);
				hintTimer = setTimeout(() => hint.classList.replace('opacity-100', 'opacity-0'), 1400);
			};
			(navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject()).then(done, () => { if (document.execCommand('copy')) done(); });
		});
		/* 두 손가락 확대 (한 손가락 이동은 기본 스크롤) */
		const spread = (t) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
		viewport.addEventListener('touchstart', (e) => {
			pinch = e.touches.length === 2 ? { d: spread(e.touches), zoom } : null;
		}, { passive: true });
		viewport.addEventListener('touchmove', (e) => {
			if (e.touches.length !== 2 || !pinch) return;
			const r = viewport.getBoundingClientRect();
			const [a, b] = e.touches;
			setZoom(Math.round(pinch.zoom * spread(e.touches) / pinch.d), (a.clientX + b.clientX) / 2 - r.left, (a.clientY + b.clientY) / 2 - r.top);
		}, { passive: true });
		viewport.addEventListener('touchend', () => (pinch = null));
	});

	/* ---------- 년·월·일 휠 선택 (fragments/wheel.html): 위로 쓸기·▲·휠 아래·↑ = 증가 ---------- */
	const ITEM = 48;
	const daysIn = (y, m) => new Date(y, m, 0).getDate();
	$$('[data-wheel]').forEach((wheel) => {
		const v = { y: Number(wheel.dataset.y), m: Number(wheel.dataset.m), d: Number(wheel.dataset.d) };
		const max = { y: 2100, m: 12, d: () => daysIn(v.y, v.m) };
		const top = (k) => (k === 'd' ? max.d() : max[k]);
		/* 연도는 1900~2100 에서 멈추고, 월·일은 끝에서 처음으로 돌아감 */
		const at = (k, n) => (k === 'y' ? (n < 1900 || n > 2100 ? null : n) : ((n - 1) % top(k) + top(k)) % top(k) + 1);
		const text = (k, n) => (k === 'y' ? `${n}년` : `${String(n).padStart(2, '0')}${k === 'm' ? '월' : '일'}`);
		const label = $('[data-wheel-label]', wheel);
		const cols = {};
		const draw = (k, offset = 0, animate = false) => {
			const { strip, viewport, input } = cols[k];
			let html = '';
			for (let i = -3; i <= 3; i++) {
				const n = at(k, v[k] + i);
				html += `<div class="grid h-12 place-items-center ${i === 0 ? 'text-[20px] font-extrabold text-[#222a24]' : 'text-[16px] font-bold text-[#b7beb9]'}">${n == null ? '' : text(k, n)}</div>`;
			}
			strip.innerHTML = html;
			strip.style.transition = 'none';
			strip.style.transform = `translateY(${offset - 2 * ITEM}px)`;
			if (animate) {
				void strip.offsetHeight;
				strip.style.transition = 'transform .22s cubic-bezier(.2,.8,.3,1)';
				strip.style.transform = `translateY(${-2 * ITEM}px)`;
			}
			input.value = v[k];
			viewport.setAttribute('aria-valuenow', v[k]);
			viewport.setAttribute('aria-valuetext', text(k, v[k]));
		};
		const refresh = () => {
			label.textContent = `${v.y}년 ${v.m}월 ${v.d}일 (${'일월화수목금토'[new Date(v.y, v.m - 1, v.d).getDay()]})`;
			wheel.dispatchEvent(new CustomEvent('wheelchange', { detail: { ...v } }));
		};
		/* dy: 끌던 거리 — 새 값으로 다시 그린 뒤 손을 뗀 자리에서 가운데로 미끄러지게 */
		const step = (k, s, dy = 0) => {
			const before = v[k];
			v[k] = k === 'y' ? Math.min(2100, Math.max(1900, v.y + s)) : at(k, v[k] + s);
			draw(k, dy + (k === 'y' ? v.y - before : s) * ITEM, true);
			if (k !== 'd') {
				v.d = Math.min(v.d, max.d());
				draw('d');
			}
			refresh();
		};
		$$('[data-wheel-col]', wheel).forEach((col) => {
			const k = col.dataset.wheelCol;
			const viewport = $('[data-wheel-viewport]', col);
			const strip = $('[data-wheel-strip]', col);
			cols[k] = { strip, viewport, input: $('[data-wheel-input]', col) };
			$$('[data-wheel-step]', col).forEach((b) => b.addEventListener('click', () => step(k, Number(b.dataset.wheelStep))));
			let drag = null;
			viewport.addEventListener('pointerdown', (e) => {
				drag = { y: e.clientY, dy: 0 };
				viewport.setPointerCapture(e.pointerId);
				strip.style.transition = 'none';
			});
			viewport.addEventListener('pointermove', (e) => {
				if (!drag) return;
				drag.dy = Math.max(-2.5 * ITEM, Math.min(2.5 * ITEM, e.clientY - drag.y));
				strip.style.transform = `translateY(${drag.dy - 2 * ITEM}px)`;
			});
			const end = (e) => {
				if (!drag) return;
				const { dy } = drag;
				drag = null;
				const s = Math.round(-dy / ITEM);
				if (s) step(k, s, dy);
				else if (Math.abs(dy) < 4 && e.type === 'pointerup') {
					/* 흐린 위·아래 값을 누르면 그 값으로 */
					const y = e.clientY - viewport.getBoundingClientRect().top;
					if (y < ITEM) step(k, -1);
					else if (y > 2 * ITEM) step(k, 1);
				} else draw(k, dy, true);
			};
			viewport.addEventListener('pointerup', end);
			viewport.addEventListener('pointercancel', end);
			let acc = 0;
			viewport.addEventListener('wheel', (e) => {
				e.preventDefault();
				acc += e.deltaY;
				if (Math.abs(acc) >= 40) { step(k, acc > 0 ? 1 : -1); acc = 0; }
			}, { passive: false });
			viewport.addEventListener('keydown', (e) => {
				if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
				e.preventDefault();
				step(k, e.key === 'ArrowUp' ? 1 : -1);
			});
		});
		/* 화면 스크립트에서 값 넣기 · 읽기 */
		wheel.wheelSet = (val) => {
			Object.assign(v, val);
			v.d = Math.min(Math.max(1, v.d || 1), max.d());
			['y', 'm', 'd'].forEach((k) => draw(k));
			refresh();
		};
		wheel.wheelValue = () => ({ ...v });
		wheel.wheelSet({});
	});

	/* ---------- 견적서 PDF 다운로드: 화면의 견적서를 원본 크기(1488px)로 복제해 이미지로 그린 뒤 A4 한 장에 담음 ---------- */
	const loadScript = (src) => new Promise((resolve, reject) => {
		if (document.querySelector(`script[src="${src}"]`)) return resolve();
		const s = document.createElement('script');
		s.src = src;
		s.onload = resolve;
		s.onerror = () => reject(new Error(`${src} 로드 실패`));
		document.head.append(s);
	});
	$$('[data-doc-download]').forEach((btn) => btn.addEventListener('click', async () => {
		const sheet = $('[data-estimate-sheet]', btn.closest('[data-doc-viewer]'));
		const label = $('[data-doc-download-label]', btn);
		const holder = document.createElement('div');
		holder.style.cssText = 'position:fixed;left:-20000px;top:0;width:1488px;container-type:inline-size;background:#fff';
		const clone = sheet.cloneNode(true);
		/* 입력 중인 값은 속성이 아니라 DOM 값이라 복제되지 않음 → 값 옮기고(금액은 보기 화면처럼 ₩ 표시) 안내 문구·행 추가 버튼은 지움 */
		const inputs = $$('input', sheet);
		$$('input', clone).forEach((el, i) => {
			const v = inputs[i].value;
			el.setAttribute('value', el.hasAttribute('data-money') && v.trim() ? `₩ ${v}` : v);
			el.removeAttribute('placeholder');
		});
		$$('[data-pdf-hide]', clone).forEach((el) => el.remove());
		holder.append(clone);
		btn.disabled = true;
		label.textContent = '생성 중';
		try {
			await Promise.all([loadScript(`${btn.dataset.vendor}html-to-image.js`), loadScript(`${btn.dataset.vendor}jspdf.umd.min.js`)]);
			document.body.append(holder);
			await document.fonts.ready;
			const image = await window.htmlToImage.toJpeg(clone, { quality: 0.92, pixelRatio: 2, backgroundColor: '#ffffff' });
			const pdf = new window.jspdf.jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' });
			pdf.addImage(image, 'JPEG', 0, 0, 210, 297);
			pdf.save(btn.dataset.fileName);
		} catch (err) {
			console.error(err);
			alert('견적서 PDF를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.');
		} finally {
			holder.remove();
			btn.disabled = false;
			label.textContent = '다운';
		}
	}));

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
