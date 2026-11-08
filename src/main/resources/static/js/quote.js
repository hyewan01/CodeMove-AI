/* 영상 견적 단계 진행 (SCR-U-003~010) - 프로토타입 Quote 컴포넌트 이식 */
(function () {
	const form = document.querySelector('[data-quote]');
	if (!form) return;

	const DATA = window.QUOTE_DATA || {};
	const A = DATA.assets || '/assets/';
	const easy = form.dataset.easy === 'true';
	const $ = (sel, root = form) => root.querySelector(sel);
	const $$ = (sel, root = form) => Array.from(root.querySelectorAll(sel));

	const STEPS = [
		{ type: 'upload', title: '집 안을 촬영한 영상을 올려주세요', subtitle: 'AI가 가구와 짐을 찾아 부피를 계산해요.' },
		{ type: 'analysis', title: 'AI가 영상을 분석하고 있어요', subtitle: '촬영한 영상을 바탕으로 짐의 종류와 부피를 확인합니다.' },
		{ type: 'options', title: '이사 정보를 알려주세요', subtitle: '주거 형태와 이사 방식을 선택해주세요.',
			easyTitle: '이사 정보를 선택해주세요', easySubtitle: '집 종류와 포장 방법을 하나씩 선택해주세요.' },
		{ type: 'address', title: '출발지와 도착지는 어디인가요?', subtitle: '정확한 주소를 입력해주세요.' },
		{ type: 'calendar', title: '이사 날짜를 선택해주세요', subtitle: '정확한 견적을 위해 가능한 일정을 알려주세요.' },
		{ type: 'summary', title: '예상 견적을 확인해주세요', subtitle: '영상 분석과 입력 정보를 바탕으로 계산했어요.' },
	];
	const EASY_NEXT = ['영상 분석하기', '인식 품목 확인', '주소 입력하기', '날짜 선택하기', '견적 확인하기', '이사 요청하기'];
	const SIZE_FACTOR = { 소형: 0.75, 중형: 1, 대형: 1.25 };

	const state = {
		step: 0,
		videoReady: false,
		analysisDone: false,
		options: { houseType: null, packType: null },
		optionLabels: { houseType: '', packType: '' },
		address: { from: null, to: null },
		date: null,
		time: '10:00',
		dateSkipped: false,
		items: (DATA.items || []).map((it) => ({ ...it })),
	};

	/* ---------- 공통 ---------- */
	const openModal = (name) => $$(`[data-modal="${name}"]`).forEach((el) => (el.hidden = false));
	const closeModal = (name) => $$(`[data-modal="${name}"]`).forEach((el) => (el.hidden = true));
	const fmtWon = (n) => n.toLocaleString('ko-KR') + '원';
	const timeLabel = (t) => {
		const [h, m] = t.split(':').map(Number);
		const dh = h === 0 ? 12 : h > 12 ? h - 12 : h;
		return `${h < 12 ? '오전' : '오후'} ${dh}:${String(m).padStart(2, '0')}`;
	};
	const itemCbm = (it) => it.baseCbm * SIZE_FACTOR[it.size] * it.count;

	const canProceed = () => {
		switch (state.step) {
			case 0: return state.videoReady;
			case 1: return state.analysisDone;
			case 2: return !!(state.options.houseType && state.options.packType);
			case 3: return !!(state.address.from && state.address.to);
			case 4: return !!state.date;
			default: return true;
		}
	};

	const nextBtn = $('[data-step-next]');
	const refreshNext = () => {
		const ok = canProceed();
		nextBtn.classList.toggle('primary', ok);
		nextBtn.classList.toggle('flow-next-disabled', !ok);
		nextBtn.classList.toggle('is-disabled', !ok);
		nextBtn.textContent = easy ? EASY_NEXT[state.step] : state.step === STEPS.length - 1 ? '이사 요청' : '다음';
	};

	const render = () => {
		const s = STEPS[state.step];
		$('[data-flow-title]').textContent = easy && s.easyTitle ? s.easyTitle : s.title;
		$('[data-flow-subtitle]').textContent = easy && s.easySubtitle ? s.easySubtitle : s.subtitle;
		$('[data-progress]').style.width = `${((state.step + 1) / STEPS.length) * 100}%`;
		$('[data-step-count]').textContent = `${state.step + 1}/${STEPS.length}`;
		$$('[data-step]').forEach((sec) => (sec.hidden = sec.dataset.step !== s.type));
		if (s.type === 'analysis' && !state.analysisDone) startAnalysis();
		if (s.type === 'summary') renderSummary();
		refreshNext();
		speakStep();
		window.scrollTo(0, 0);
	};

	const goStep = (n) => { state.step = n; render(); };

	$('[data-step-back]').addEventListener('click', () => {
		if (state.step > 0) goStep(state.step - 1);
		else if (history.length > 1 && document.referrer.startsWith(location.origin)) history.back();
		else location.href = form.dataset.back;
	});
	nextBtn.addEventListener('click', () => {
		if (!canProceed()) return;
		if (state.step === STEPS.length - 1) { writeItemInputs(); form.submit(); return; }
		goStep(state.step + 1);
	});
	$$('[data-go-step]').forEach((b) => b.addEventListener('click', () => goStep(Number(b.dataset.goStep))));

	/* ---------- 1. 영상 업로드 ---------- */
	const videoInput = $('[data-video-input]');
	$$('[data-video-pick]').forEach((b) => b.addEventListener('click', () => videoInput.click()));
	videoInput.addEventListener('change', () => {
		const file = videoInput.files && videoInput.files[0];
		if (!file) return;
		state.videoReady = true;
		state.analysisDone = false;
		$('[data-upload-card]').classList.add('uploaded');
		$('[data-upload-empty]').hidden = true;
		$('[data-upload-done]').hidden = false;
		$('[data-video-name]').textContent = file.name;
		refreshNext();
	});

	/* ---------- 2. AI 분석 (화면 시연: 1.8초 후 완료) ---------- */
	let analysisTimer = null;
	function startAnalysis() {
		$('[data-analysis-loading]').hidden = false;
		$('[data-analysis-result]').hidden = true;
		clearTimeout(analysisTimer);
		analysisTimer = setTimeout(() => {
			state.analysisDone = true;
			$('[data-analysis-loading]').hidden = true;
			$('[data-analysis-result]').hidden = false;
			renderItems();
			refreshNext();
			speakStep();
		}, 1800);
	}

	function renderItems() {
		const list = $('[data-item-list]');
		list.innerHTML = '';
		state.items.forEach((it) => {
			const btn = document.createElement('button');
			btn.type = 'button';
			btn.className = 'action recognized-item' + (it.needsReview ? ' needs-review' : '');
			btn.setAttribute('aria-label', `${it.name} 수정`);
			btn.innerHTML = `
				<div class="recognized-item-icon"><img alt="" src="${A}${it.icon}"></div>
				<div class="recognized-item-copy">
					<strong>${it.name}${it.needsReview ? '<em>확인 필요</em>' : ''}</strong>
					<span>${it.size} · ${it.count}개</span>
				</div>
				<b>${itemCbm(it).toFixed(1)} CBM</b>`;
			btn.addEventListener('click', () => openEditor(it, false));
			list.appendChild(btn);
		});
		$('[data-item-total]').textContent = state.items.reduce((s, it) => s + it.count, 0);
		$('[data-cbm-total]').textContent = state.items.reduce((s, it) => s + itemCbm(it), 0).toFixed(1);
	}

	function writeItemInputs() {
		const box = $('[data-item-inputs]');
		box.innerHTML = '';
		state.items.forEach((it, i) => {
			[['name', it.name], ['size', it.size], ['count', it.count], ['cbm', itemCbm(it).toFixed(2)]].forEach(([k, v]) => {
				const input = document.createElement('input');
				input.type = 'hidden';
				input.name = `items[${i}].${k}`;
				input.value = v;
				box.appendChild(input);
			});
		});
	}

	/* 품목 수정 시트 */
	let draft = null;
	let adding = false;
	function openEditor(item, isNew) {
		adding = isNew;
		draft = { ...item };
		$('[data-edit-kicker]').textContent = isNew ? '누락 품목 추가' : '인식 품목 수정';
		$('[data-edit-delete]').hidden = isNew;
		$('[data-edit-save]').textContent = isNew ? '품목 추가' : '수정 완료';
		renderDraft();
		openModal('item-edit');
	}
	function renderDraft() {
		$('[data-edit-name]').textContent = draft.name;
		$$('[data-edit-type]').forEach((b) => b.classList.toggle('active', b.dataset.name === draft.name));
		$$('[data-edit-size]').forEach((b) => b.classList.toggle('active', b.dataset.editSize === draft.size));
		$('[data-edit-count-value]').textContent = draft.count;
		$('[data-edit-cbm]').textContent = itemCbm(draft).toFixed(1);
	}
	$$('[data-edit-type]').forEach((b) => b.addEventListener('click', () => {
		Object.assign(draft, { name: b.dataset.name, icon: b.dataset.icon, baseCbm: Number(b.dataset.cbm) });
		renderDraft();
	}));
	$$('[data-edit-size]').forEach((b) => b.addEventListener('click', () => { draft.size = b.dataset.editSize; renderDraft(); }));
	$$('[data-edit-count]').forEach((b) => b.addEventListener('click', () => {
		draft.count = Math.max(1, draft.count + Number(b.dataset.editCount));
		renderDraft();
	}));
	$('[data-edit-delete]').addEventListener('click', () => {
		state.items = state.items.filter((it) => it.id !== draft.id);
		closeModal('item-edit');
		renderItems();
	});
	$('[data-edit-save]').addEventListener('click', () => {
		const saved = { ...draft, needsReview: false };
		state.items = adding ? [...state.items, saved] : state.items.map((it) => (it.id === saved.id ? saved : it));
		closeModal('item-edit');
		renderItems();
	});
	$('[data-item-add]').addEventListener('click', () => openEditor(
		{ id: `item-${Date.now()}`, name: '테이블', count: 1, baseCbm: 0.85, size: '중형', icon: 'item-table.svg' }, true));

	/* ---------- 3. 주거형태·포장방식 ---------- */
	$$('[data-option]').forEach((btn) => btn.addEventListener('click', () => {
		const group = btn.dataset.option;
		state.options[group] = btn.dataset.value;
		state.optionLabels[group] = btn.dataset.label;
		$(`[data-option-input="${group}"]`).value = btn.dataset.value;
		$$(`[data-option="${group}"]`).forEach((b) => {
			const on = b === btn;
			b.classList.toggle('active', on);
			const mark = b.querySelector('b');
			if (mark && mark.hasAttribute('data-keep')) mark.textContent = on ? '✓' : '';
			else if (mark) mark.hidden = !on;
		});
		refreshNext();
	}));

	/* ---------- 4. 주소 검색 (화면 시연: 목업 주소, 추후 주소 API 연동) ---------- */
	$$('[data-address]').forEach((wrap) => {
		const key = wrap.dataset.address;
		const q = $('[data-addr-query]', wrap);
		const results = $('[data-addr-results]', wrap);
		const search = () => {
			const term = q.value.trim();
			if (!term) return;
			const found = (DATA.addresses || []).filter((a) => a.road.includes(term) || a.jibun.includes(term));
			results.innerHTML = found.length
				? ''
				: '<div class="addr-no-result">검색 결과가 없어요. 다른 주소로 검색해보세요.</div>';
			found.forEach((addr) => {
				const b = document.createElement('button');
				b.type = 'button';
				b.className = 'action addr-result-item';
				b.innerHTML = `<div class="addr-result-road">${addr.road}</div><div class="addr-result-jibun">${addr.jibun}</div>`;
				b.addEventListener('click', () => pick(addr));
				results.appendChild(b);
			});
			results.hidden = false;
		};
		const pick = (addr) => {
			state.address[key] = addr.road;
			$('[data-addr-value]', wrap).value = addr.road;
			$('[data-addr-road]', wrap).textContent = addr.road;
			$('[data-addr-jibun]', wrap).textContent = addr.jibun;
			$('[data-addr-search]', wrap).hidden = true;
			$('[data-addr-selected]', wrap).hidden = false;
			$('[data-addr-detail-wrap]', wrap).hidden = false;
			results.hidden = true;
			refreshNext();
		};
		$('[data-addr-search-btn]', wrap).addEventListener('click', search);
		q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); search(); } });
		q.addEventListener('input', () => (results.hidden = true));
		$('[data-addr-detail]', wrap).addEventListener('input', (e) => {
			$('[data-addr-hint]', wrap).hidden = e.target.value.trim().length > 0;
		});
		$('[data-addr-reset]', wrap).addEventListener('click', () => {
			state.address[key] = null;
			q.value = '';
			$('[data-addr-value]', wrap).value = '';
			$('[data-addr-detail]', wrap).value = '';
			$('[data-addr-search]', wrap).hidden = false;
			$('[data-addr-selected]', wrap).hidden = true;
			$('[data-addr-detail-wrap]', wrap).hidden = true;
			refreshNext();
		});
	});

	/* ---------- 5. 달력 ---------- */
	const today = new Date();
	let viewY = today.getFullYear();
	let viewM = today.getMonth();
	const daysBox = $('[data-cal-days]');
	const isCurrentMonth = () => viewY === today.getFullYear() && viewM === today.getMonth();
	const dateLabel = () => {
		if (!state.date) return '';
		const [y, m, d] = state.date.split('-');
		return `${y}. ${m}. ${d} ${timeLabel(state.time)}`;
	};
	function renderCalendar() {
		$('[data-cal-title]').textContent = `${viewY}년 ${viewM + 1}월`;
		$('[data-cal-prev]').classList.toggle('nav-disabled', isCurrentMonth());
		const firstDow = new Date(viewY, viewM, 1).getDay();
		const inMonth = new Date(viewY, viewM + 1, 0).getDate();
		const inPrev = new Date(viewY, viewM, 0).getDate();
		const total = Math.ceil((firstDow + inMonth) / 7) * 7;
		daysBox.innerHTML = '';
		for (let i = 0; i < total; i++) {
			const span = document.createElement('span');
			if (i < firstDow) { span.className = 'muted'; span.textContent = inPrev - firstDow + 1 + i; }
			else if (i - firstDow + 1 > inMonth) { span.className = 'muted'; span.textContent = i - firstDow + 1 - inMonth; }
			else {
				const d = i - firstDow + 1;
				const iso = `${viewY}-${String(viewM + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
				const past = isCurrentMonth() && d < today.getDate();
				span.textContent = d;
				span.className = state.date === iso ? 'picked' : past ? 'muted' : 'selectable';
				if (!past) span.addEventListener('click', () => {
					state.date = iso;
					state.dateSkipped = false;
					$('[data-move-date]').value = iso;
					$('[data-date-undecided]').value = 'false';
					$('[data-cal-time-open]').hidden = false;
					renderCalendar();
					refreshNext();
				});
			}
			daysBox.appendChild(span);
		}
	}
	const changeMonth = (delta) => {
		if (delta < 0 && isCurrentMonth()) return;
		viewM += delta;
		if (viewM < 0) { viewM = 11; viewY--; }
		if (viewM > 11) { viewM = 0; viewY++; }
		state.date = null;
		$('[data-move-date]').value = '';
		$('[data-cal-time-open]').hidden = true;
		renderCalendar();
		refreshNext();
	};
	$('[data-cal-prev]').addEventListener('click', () => changeMonth(-1));
	$('[data-cal-next]').addEventListener('click', () => changeMonth(1));
	$('[data-cal-time-open]').addEventListener('click', () => openModal('time'));
	$$('[data-time]').forEach((b) => b.addEventListener('click', () => {
		state.time = b.dataset.time;
		$('[data-move-time]').value = state.time;
		$('[data-cal-time-label]').textContent = timeLabel(state.time);
		$$('[data-time]').forEach((x) => x.classList.toggle('active', x === b));
		closeModal('time');
	}));
	$('[data-date-skip]').addEventListener('click', () => {
		state.dateSkipped = true;
		state.date = null;
		$('[data-move-date]').value = '';
		$('[data-date-undecided]').value = 'true';
		closeModal('date-skip');
		renderCalendar();
		goStep(5);
	});
	renderCalendar();

	/* ---------- 6. 예상 견적 ---------- */
	function renderSummary() {
		const est = DATA.estimate || {};
		$('[data-est-kicker]').textContent = state.dateSkipped ? '예상 견적 범위' : 'AI 예상 견적';
		$('[data-est-amount]').textContent = state.dateSkipped
			? `${(est.min || 0).toLocaleString('ko-KR')} ~ ${fmtWon(est.max || 0)}`
			: fmtWon(est.amount || 0);
		$('[data-est-note]').textContent = state.dateSkipped
			? '이사 날짜에 따라 비용이 달라질 수 있어요.'
			: '선택한 일정과 영상 분석 결과를 반영했어요.';
		$('[data-sum="date"]').textContent = state.dateSkipped ? '일정 미정' : dateLabel() || '—';
		$('[data-sum="from"]').textContent = state.address.from || '—';
		$('[data-sum="to"]').textContent = state.address.to || '—';
		$('[data-sum="options"]').textContent = state.options.houseType && state.options.packType
			? `${state.optionLabels.houseType} · ${state.optionLabels.packType}` : '—';
	}

	/* ---------- 쉬운 모드 음성 안내 ---------- */
	const VOICE = [
		() => '영상 올리기 단계입니다. 집 안 전체가 보이도록 촬영한 동영상을 올려주세요. 동영상 선택 버튼을 누르면 됩니다.',
		() => state.analysisDone
			? '영상 분석이 끝났습니다. 찾은 짐 목록을 확인해주세요. 빠진 짐이 있거나 정보가 다르면 해당 품목을 눌러 수정할 수 있습니다.'
			: '짐 확인 단계입니다. 올려주신 영상에서 가구와 짐을 찾고 있습니다. 잠시 기다려주세요.',
		() => '이사 정보 단계입니다. 현재 사는 집의 종류와 원하는 포장 방식을 차례로 선택해주세요.',
		() => '주소 입력 단계입니다. 출발할 곳과 도착할 곳의 주소를 차례로 입력해주세요.',
		() => '날짜 선택 단계입니다. 달력에서 이사할 날짜를 고르고, 아래에서 시간을 선택해주세요. 날짜를 아직 모르면 날짜를 모르겠어요 버튼을 누르세요.',
		() => '견적 확인 단계입니다. 예상 금액과 입력한 이사 정보를 확인해주세요. 바꿀 내용이 있으면 수정 버튼을 누르세요.',
	];
	const voiceBtn = $('[data-voice-toggle]');
	let voiceOn = false;
	const setVoiceUi = (speaking) => {
		if (!voiceBtn) return;
		voiceBtn.classList.toggle('active', voiceOn);
		$('[data-voice-label]').textContent = voiceOn ? (speaking ? '음성 안내 중' : '음성 안내 켜짐') : '음성으로 듣기';
		$('[data-voice-off]').hidden = !voiceOn;
		voiceBtn.setAttribute('aria-label', voiceOn ? '음성 안내 끄기' : '음성 안내 켜기');
	};
	function speakStep() {
		if (!easy || !voiceOn || !window.speechSynthesis) return;
		speechSynthesis.cancel();
		const u = new SpeechSynthesisUtterance(VOICE[state.step]());
		u.lang = 'ko-KR';
		u.rate = 0.88;
		u.onstart = () => setVoiceUi(true);
		u.onend = u.onerror = () => setVoiceUi(false);
		speechSynthesis.speak(u);
	}
	if (voiceBtn) voiceBtn.addEventListener('click', () => {
		voiceOn = !voiceOn;
		if (!voiceOn && window.speechSynthesis) speechSynthesis.cancel();
		setVoiceUi(false);
		speakStep();
	});

	render();
})();
