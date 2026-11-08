/* 고객 화면 공통 동작 - 프로토타입(App.tsx)의 클라이언트 상호작용 이식 */
(function () {
	const $ = (sel, root = document) => root.querySelector(sel);
	const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

	/* 뒤로 가기: 같은 사이트에서 왔으면 history.back() */
	$$('a[data-back]').forEach((a) => a.addEventListener('click', (e) => {
		if (document.referrer.startsWith(location.origin) && history.length > 1) {
			e.preventDefault();
			history.back();
		}
	}));

	/* 모달: data-modal-open="이름" / data-modal="이름" / data-modal-close */
	document.addEventListener('click', (e) => {
		const opener = e.target.closest('[data-modal-open]');
		if (opener) {
			$$(`[data-modal="${opener.dataset.modalOpen}"]`).forEach((el) => (el.hidden = false));
			return;
		}
		const closer = e.target.closest('[data-modal-close]');
		if (closer) {
			const owner = closer.closest('[data-modal]');
			if (owner) $$(`[data-modal="${owner.dataset.modal}"]`).forEach((el) => (el.hidden = true));
			if (window.speechSynthesis) speechSynthesis.cancel();
		}
	});

	/* 탭: [data-tabs] 안의 [data-tab] 버튼과 [data-tab-panel] */
	$$('[data-tabs]').forEach((box) => {
		const tabs = $$('[data-tab]', box);
		const activeClass = box.dataset.activeClass || 'active';
		tabs.forEach((t) => t.addEventListener('click', () => {
			tabs.forEach((x) => {
				const on = x === t;
				x.classList.toggle(activeClass, on);
				x.setAttribute('aria-selected', on);
			});
			$$('[data-tab-panel]').forEach((p) => (p.hidden = p.dataset.tabPanel !== t.dataset.tab));
			if (box.dataset.tabSync) history.replaceState(null, '', `?${box.dataset.tabSync}=${t.dataset.tab}`);
		}));
	});

	/* 음성 안내 토글: data-speak="문장" */
	$$('[data-speak]').forEach((btn) => btn.addEventListener('click', () => {
		if (!window.speechSynthesis) return;
		const target = btn.dataset.speakTarget ? $(btn.dataset.speakTarget, btn) : btn;
		const setUi = (on) => {
			btn.classList.toggle('active', on);
			if (btn.dataset.labelOn) target.textContent = on ? btn.dataset.labelOn : btn.dataset.labelOff;
		};
		if (btn.classList.contains('active')) { speechSynthesis.cancel(); setUi(false); return; }
		speechSynthesis.cancel();
		const u = new SpeechSynthesisUtterance(btn.dataset.speak);
		u.lang = 'ko-KR';
		u.rate = 0.9;
		u.onend = u.onerror = () => setUi(false);
		setUi(true);
		speechSynthesis.speak(u);
	}));

	/* 입력 포맷: data-format="phone" (010-0000-0000) / "digits" */
	const formatPhone = (v) => {
		const n = v.replace(/\D/g, '').slice(0, 11);
		if (n.length <= 3) return n;
		if (n.length <= 7) return `${n.slice(0, 3)}-${n.slice(3)}`;
		return `${n.slice(0, 3)}-${n.slice(3, 7)}-${n.slice(7)}`;
	};
	$$('[data-format]').forEach((input) => input.addEventListener('input', () => {
		input.value = input.dataset.format === 'phone'
			? formatPhone(input.value)
			: input.value.replace(/\D/g, '').slice(0, Number(input.maxLength) > 0 ? input.maxLength : undefined);
	}));
	const digitsOf = (input) => (input ? input.value.replace(/\D/g, '') : '');
	const setEnabled = (btn, on) => btn && btn.classList.toggle('is-disabled', !on);

	/* 3분 타이머 */
	const startTimer = (el, seconds = 180) => {
		clearInterval(el._timer);
		let left = seconds;
		const paint = () => {
			el.textContent = left === 0 ? '시간 만료' : `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
			el.classList.toggle('expired', left === 0);
		};
		paint();
		el._timer = setInterval(() => { left = Math.max(0, left - 1); paint(); if (!left) clearInterval(el._timer); }, 1000);
	};

	/* 비활성 버튼은 제출 막기 */
	document.addEventListener('submit', (e) => {
		const submitter = e.submitter;
		if (submitter && submitter.classList.contains('is-disabled')) e.preventDefault();
	});

	/* ---------- 스플래시 (SCR-U-022) ---------- */
	const splash = $('[data-splash]');
	if (splash) {
		const title = $('.splash-title', splash);
		Promise.race([document.fonts.load('700 26px "Gmarket Sans TTF:Bold"'), new Promise((r) => setTimeout(r, 450))])
			.finally(() => {
				title.classList.add('font-ready');
				setTimeout(() => { splash.classList.add('splash-closing'); setTimeout(() => splash.remove(), 220); }, 650);
			});
	}

	/* ---------- 온보딩 슬라이드 ---------- */
	$$('[data-onboarding]').forEach((root) => {
		const slides = $$('[data-slide]', root);
		const dots = $$('[data-pager]', root);
		let current = 0;
		const show = (i) => {
			current = i;
			slides.forEach((s, idx) => (s.hidden = idx !== i));
			dots.forEach((d, idx) => d.classList.toggle('pager-active', idx === i));
		};
		dots.forEach((d) => d.addEventListener('click', () => show(Number(d.dataset.pager))));
		if (!root.closest('.login-scene')) setInterval(() => show((current + 1) % slides.length), 3500);
	});

	/* ---------- 로그인 바텀시트: 아래로 70px 이상 끌면 닫기 ---------- */
	const sheet = $('[data-login-sheet]');
	if (sheet) {
		const grabber = $('[data-sheet-grabber]', sheet);
		let startY = null;
		grabber.addEventListener('pointerdown', (e) => { startY = e.clientY; grabber.setPointerCapture(e.pointerId); });
		grabber.addEventListener('pointermove', (e) => {
			if (startY !== null) sheet.style.transform = `translateY(${Math.max(0, e.clientY - startY)}px)`;
		});
		const end = (e) => {
			if (startY === null) return;
			const dist = Math.max(0, e.clientY - startY);
			startY = null;
			if (dist > 70) location.href = sheet.dataset.dismiss;
			else sheet.style.transform = '';
		};
		grabber.addEventListener('pointerup', end);
		grabber.addEventListener('pointercancel', () => { startY = null; sheet.style.transform = ''; });
	}

	/* ---------- 휴대폰 인증: 휴대전화 로그인(SCR-U-024) · 휴대폰 번호 변경(SCR-U-032) ---------- */
	$$('[data-phone-verify]').forEach((phoneLogin) => {
		const phone = $('[name="phone"]', phoneLogin);
		const code = $('[name="code"]', phoneLogin);
		const send = $('[data-send-code]', phoneLogin);
		const submit = $('[data-submit]', phoneLogin);
		let sent = false;
		/* 번호를 바꾸면 인증 초기화 */
		phone.addEventListener('input', () => {
			if (!sent) return;
			sent = false;
			send.textContent = '인증하기';
			$('[data-code-group]', phoneLogin).hidden = true;
		});
		const refresh = () => {
			const ready = digitsOf(phone).length === 11;
			setEnabled(send, ready);
			send.classList.toggle('active', ready);
			setEnabled(submit, sent && digitsOf(code).length === 6);
		};
		send.addEventListener('click', () => {
			if (send.classList.contains('is-disabled')) return;
			sent = true;
			code.value = '';
			send.textContent = '재인증';
			$('[data-code-group]', phoneLogin).hidden = false;
			$('[data-phone-echo]', phoneLogin).textContent = phone.value;
			startTimer($('[data-timer]', phoneLogin));
			code.focus();
			refresh();
		});
		phone.addEventListener('input', refresh);
		code.addEventListener('input', refresh);
		refresh();
	});

	/* ---------- 목록 필터 시트 (견적내역 · 예약내역) ---------- */
	$$('[data-filter-box]').forEach((box) => {
		const menu = $('[data-filter-menu]', box);
		const toggle = $('[data-filter-toggle]', box);
		const dismiss = $('[data-filter-dismiss]', box);
		const items = $$(`[data-filter-item]`);
		const empty = $('[data-filter-empty]');
		const setOpen = (open) => {
			menu.hidden = dismiss.hidden = !open;
			toggle.classList.toggle('open', open);
			document.body.style.overflow = open ? 'hidden' : '';
		};
		toggle.addEventListener('click', () => setOpen(menu.hidden));
		dismiss.addEventListener('click', () => setOpen(false));
		$('[data-filter-handle]', box).addEventListener('click', () => setOpen(false));
		$$('[data-filter-option]', box).forEach((opt) => opt.addEventListener('click', () => {
			const v = opt.dataset.filterOption;
			$$('[data-filter-option]', box).forEach((o) => {
				const on = o === opt;
				o.classList.toggle('selected', on);
				const mark = $('span', o);
				if (mark) mark.hidden = !on;
			});
			$('[data-filter-label]', box).textContent = opt.dataset.label;
			let shown = 0;
			items.forEach((it) => {
				const show = v === 'all' || it.dataset.filterItem === v;
				it.hidden = !show;
				if (show) shown++;
			});
			if (empty) empty.hidden = shown > 0;
			/* 연도 묶음 등: 보이는 항목이 없으면 그룹 숨김 */
			$$('[data-filter-group]').forEach((g) => (g.hidden = !$$('[data-filter-item]', g).some((it) => !it.hidden)));
			setOpen(false);
		}));
	});

	/* ---------- 업체 견적서 원본: 배율 조절 · 끌어서 이동 ---------- */
	$$('[data-quote-doc]').forEach((doc) => {
		const target = $('[data-zoom-target]', doc);
		const label = $('[data-zoom="reset"]', doc);
		const minus = $('[data-zoom="-25"]', doc);
		const plus = $('[data-zoom="25"]', doc);
		let zoom = 75;
		const apply = () => {
			target.style.setProperty('--quote-zoom', zoom / 100);
			label.textContent = `${zoom}%`;
			minus.classList.toggle('is-disabled', zoom <= 50);
			plus.classList.toggle('is-disabled', zoom >= 150);
		};
		$$('[data-zoom]', doc).forEach((b) => b.addEventListener('click', () => {
			const d = b.dataset.zoom;
			zoom = d === 'reset' ? 100 : Math.min(150, Math.max(50, zoom + Number(d)));
			apply();
		}));
		apply();
	});
	$$('[data-drag-scroll]').forEach((box) => {
		let drag = null;
		box.addEventListener('pointerdown', (e) => {
			if (e.button !== 0) return;
			drag = { id: e.pointerId, x: e.clientX, y: e.clientY, left: box.scrollLeft, top: box.scrollTop };
			box.setPointerCapture(e.pointerId);
			box.classList.add('is-dragging');
		});
		box.addEventListener('pointermove', (e) => {
			if (!drag) return;
			e.preventDefault();
			box.scrollLeft = drag.left - (e.clientX - drag.x);
			box.scrollTop = drag.top - (e.clientY - drag.y);
		});
		const end = () => { drag = null; box.classList.remove('is-dragging'); };
		box.addEventListener('pointerup', end);
		box.addEventListener('pointercancel', end);
	});

	/* ---------- 회원가입 (SCR-U-025) ---------- */
	const signup = $('[data-signup]');
	if (signup) {
		const name = $('[name="name"]', signup);
		const phone = $('[name="phone"]', signup);
		const terms = $('[data-terms]', signup);
		const submit = $('[data-submit]', signup);
		const refresh = () => setEnabled(submit, name.value.trim().length > 0 && digitsOf(phone).length === 11 && terms.value === 'true');
		name.addEventListener('input', refresh);
		phone.addEventListener('input', refresh);
		/* 동의 전: 버튼 기본 동작으로 약관 화면 이동 / 동의 후: 체크 해제 */
		const check = $('[data-terms-check]', signup);
		check.addEventListener('click', (e) => {
			if (terms.value !== 'true') return;
			e.preventDefault();
			terms.value = 'false';
			const mark = $('span', check);
			mark.classList.remove('terms-checked');
			mark.textContent = '';
			refresh();
		});
		refresh();
	}

	/* ---------- 개인정보 수집 안내: 끝까지 읽어야 동의 가능 (SCR-U-026) ---------- */
	const termsPage = $('[data-terms-page]');
	if (termsPage) {
		const box = $('[data-terms-scroll]', termsPage);
		const submit = $('[data-submit]', termsPage);
		const done = () => { setEnabled(submit, true); submit.textContent = submit.dataset.labelReady; };
		if (box.scrollHeight <= box.clientHeight + 1) done();
		box.addEventListener('scroll', () => { if (box.scrollTop + box.clientHeight >= box.scrollHeight - 12) done(); });
	}

	/* ---------- 인증번호 6자리 입력 (SCR-U-027) ---------- */
	const verify = $('[data-verify]');
	if (verify) {
		const boxes = $$('.otp-box', verify);
		const hidden = $('[data-otp-value]', verify);
		const submit = $('[data-submit]', verify);
		const timer = $('[data-timer]', verify);
		const refresh = () => {
			const code = boxes.map((b) => b.value).join('');
			hidden.value = code;
			boxes.forEach((b) => b.classList.toggle('otp-filled', !!b.value));
			const ready = code.length === 6;
			setEnabled(submit, ready);
			submit.classList.toggle('primary', ready);
			submit.classList.toggle('next-button', !ready);
		};
		boxes.forEach((b, i) => {
			b.setAttribute('aria-label', `인증번호 ${i + 1}번째 자리`);
			b.addEventListener('focus', () => b.select());
			b.addEventListener('input', () => {
				b.value = b.value.replace(/\D/g, '').slice(-1);
				if (b.value && i < 5) boxes[i + 1].focus();
				refresh();
			});
			b.addEventListener('keydown', (e) => { if (e.key === 'Backspace' && !b.value && i > 0) boxes[i - 1].focus(); });
		});
		$('[data-otp]', verify).addEventListener('paste', (e) => {
			const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
			if (!pasted) return;
			e.preventDefault();
			boxes.forEach((b, i) => (b.value = pasted[i] || ''));
			boxes[Math.min(pasted.length, 5)].focus();
			refresh();
		});
		$('[data-resend]', verify).addEventListener('click', () => {
			boxes.forEach((b) => (b.value = ''));
			boxes[0].focus();
			startTimer(timer);
			refresh();
		});
		startTimer(timer);
		refresh();
	}
})();
