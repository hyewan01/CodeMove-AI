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
		{ type: 'upload', name: '영상 올리기', title: '집 안을 촬영한 영상을 올려주세요', subtitle: 'AI가 가구와 짐을 찾아 부피를 계산해요.' },
		{ type: 'analysis', name: '짐 확인', title: 'AI가 영상을 분석하고 있어요', subtitle: '촬영한 영상을 바탕으로 짐의 종류와 부피를 확인합니다.',
			doneTitle: '찾은 짐을 확인해주세요', doneSubtitle: '다른 부분이 있으면 눌러서 고칠 수 있어요.' },
		{ type: 'extras', name: '추가 작업', title: '따로 맡길 작업이 있나요?', subtitle: '영상에서 찾은 물건은 미리 골라 두었어요.' },
		{ type: 'options', name: '이사 방식', title: '이사 정보를 알려주세요', subtitle: '주거 형태와 이사 방식을 선택해주세요.',
			easyTitle: '이사 정보를 선택해주세요', easySubtitle: '집 종류와 포장 방법을 하나씩 선택해주세요.' },
		{ type: 'address', name: '주소', title: '출발지와 도착지는 어디인가요?', subtitle: '상세주소에 동·호수까지 적어주세요.' },
		{ type: 'carry', name: '나르는 방법', title: '짐은 어떻게 옮기나요?', subtitle: '출발지와 도착지를 각각 골라주세요.' },
		{ type: 'calendar', name: '날짜', title: '이사 날짜를 선택해주세요', subtitle: '정확한 견적을 위해 가능한 일정을 알려주세요.' },
		{ type: 'memo', name: '요청 사항', title: '미리 알려주실 게 있나요?', subtitle: '귀중품이나 현장 사정을 적어 주시면 견적에 참고해요.' },
		{ type: 'summary', name: '예상 견적', title: '예상 견적을 확인해주세요', subtitle: '영상 분석과 입력 정보를 바탕으로 계산했어요.' },
	];
	const stepOf = (type) => STEPS.findIndex((s) => s.type === type);
	const EASY_NEXT = ['영상 분석하기', '추가 작업 고르기', '이사 정보 고르기', '주소 입력하기', '나르는 방법 고르기',
		'날짜 선택하기', '요청 사항 적기', '견적 확인하기', '이사 요청하기'];
	/* 표준 품목표: 품목마다 크기 3단계(01 소형 · 02 중형 · 03 대형)와 기준 부피(CBM, 1개) */
	const CATALOG = DATA.catalog || [];
	const CATEGORY_NAME = Object.fromEntries((DATA.categories || []).map((c) => [c.code, c.name]));
	const NO_SON = DATA.noSon || {};
	const HOLIDAYS = DATA.holidays || {};

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
		items: [], // /quote/analyze 가 채움
		analyzedFile: null, // 분석을 마친 파일 (같은 파일이면 다시 분석하지 않음)
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
	const catalogItem = (name) => CATALOG.find((c) => c.name === name);
	const sizeOf = (it) => {
		const c = catalogItem(it.name);
		if (!c) return { code: '02', grade: '중형', name: '중형', rule: '', cbm: it.baseCbm || 0 };
		return c.sizes.find((z) => z.code === it.sizeCode) || c.sizes[1] || c.sizes[0];
	};
	const iconOf = (it) => (catalogItem(it.name) || {}).icon || 'item-box.svg';
	const itemCbm = (it) => sizeOf(it).cbm * it.count;
	const esc = (t) => String(t).replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));

	const canProceed = () => {
		switch (STEPS[state.step].type) {
			case 'upload': return state.videoReady;
			case 'analysis': return state.analysisDone;
			case 'options': return !!(state.options.houseType && state.options.packType);
			case 'address': return !!(state.address.from && state.address.to);
			case 'carry': return carryReady();
			case 'calendar': return !!state.date;
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
		const done = s.doneTitle && state.analysisDone;
		$('[data-flow-title]').textContent = done ? s.doneTitle : easy && s.easyTitle ? s.easyTitle : s.title;
		$('[data-flow-subtitle]').textContent = done ? s.doneSubtitle : easy && s.easySubtitle ? s.easySubtitle : s.subtitle;
		const kicker = $('[data-flow-kicker]');
		if (kicker) kicker.textContent = s.name;
		$('[data-step-dots]').innerHTML = STEPS.map((x, i) => `<li class="${i < state.step ? 'done' : i === state.step ? 'now' : ''}"${
			i === state.step ? ' aria-current="step"' : ''}><span class="sr-only">${i + 1}. ${esc(x.name)}</span></li>`).join('');
		$('[data-step-count]').textContent = `${state.step + 1}/${STEPS.length}`;
		$$('[data-step]').forEach((sec) => (sec.hidden = sec.dataset.step !== s.type));
		if (s.type === 'analysis' && !state.analysisDone) startAnalysis();
		if (s.type === 'extras') renderExtras();
		if (s.type === 'carry') renderCarry();
		if (s.type === 'summary') renderSummary();
		refreshNext();
		speakStep();
		window.scrollTo(0, 0);
		document.querySelector('.screen')?.scrollTo(0, 0); // PC 스마트폰 프레임에서는 .screen이 스크롤됨
	};

	const goStep = (n) => { state.step = n; render(); };

	$('[data-step-back]').addEventListener('click', () => {
		if (state.step > 0) goStep(state.step - 1);
		else if (history.length > 1 && document.referrer.startsWith(location.origin)) history.back();
		else location.href = form.dataset.backUrl;
	});
	nextBtn.addEventListener('click', () => {
		if (!canProceed()) return;
		if (state.step === STEPS.length - 1) { writeItemInputs(); writeExtraInputs(); form.submit(); return; }
		goStep(state.step + 1);
	});
	$$('[data-go-step]').forEach((b) => b.addEventListener('click', () => goStep(stepOf(b.dataset.goStep))));

	/* ---------- 1. 영상 업로드 ---------- */
	const videoInput = $('[data-video-input]');
	$$('[data-video-pick]').forEach((b) => b.addEventListener('click', () => videoInput.click()));
	const MAX_VIDEO_BYTES = 1024 * 1024 * 1024; // application.yml max-file-size(1GB)와 같게
	const videoError = $('[data-video-error]');
	videoInput.addEventListener('change', () => {
		const file = videoInput.files && videoInput.files[0];
		if (!file) return;
		videoError.hidden = file.size <= MAX_VIDEO_BYTES;
		if (file.size > MAX_VIDEO_BYTES) {
			videoInput.value = '';
			return;
		}
		state.videoReady = true;
		state.analysisDone = false;
		state.analyzedFile = null;
		videoInput.setAttribute('name', 'video');
		$('[data-upload-card]').classList.add('uploaded');
		$('[data-upload-empty]').hidden = true;
		$('[data-upload-done]').hidden = false;
		$('[data-video-name]').textContent = file.name;
		refreshNext();
	});

	/* ---------- 2. AI 분석: 영상을 /quote/analyze 로 올려 품목을 받음 ---------- */
	let analysisRun = 0;
	let progressTimer = null;
	const PROGRESS = ['영상을 올리고 있어요', '방마다 가구와 가전을 찾고 있어요', '같은 물건을 두 번 세지 않게 맞춰 보고 있어요',
		'짐 크기를 품목표와 맞추고 있어요', '거의 다 됐어요. 조금만 기다려주세요'];
	function showAnalysis(view) {
		$('[data-analysis-loading]').hidden = view !== 'loading';
		$('[data-analysis-error]').hidden = view !== 'error';
		$('[data-analysis-result]').hidden = view !== 'result';
	}
	function finishAnalysis(items, memo, cached) {
		clearInterval(progressTimer);
		state.items = items.map((it, i) => ({ id: it.id || `ai${i}`, ...it }));
		state.analysisDone = true;
		if (!cached) state.extras = null; // 새 분석이면 추가 작업을 다시 짐작
		showAnalysis('result');
		const memoEl = $('[data-ai-memo]');
		memoEl.hidden = !memo;
		memoEl.textContent = memo || '';
		$('[data-flow-title]').textContent = STEPS[1].doneTitle;
		$('[data-flow-subtitle]').textContent = STEPS[1].doneSubtitle;
		renderItems();
		refreshNext();
		speakStep();
	}
	function failAnalysis(message) {
		clearInterval(progressTimer);
		$('[data-analysis-error-message]').textContent = message || '잠시 후 다시 시도해 주세요';
		showAnalysis('error');
		refreshNext();
	}
	function startAnalysis() {
		const file = videoInput.files && videoInput.files[0];
		if (state.analyzedFile && state.analyzedFile === file) {
			finishAnalysis(state.items, $('[data-ai-memo]').textContent, true);
			return;
		}
		if (!file) { failAnalysis('영상 파일을 다시 선택해 주세요'); return; }
		const run = ++analysisRun;
		showAnalysis('loading');
		let p = 0;
		const progress = $('[data-analysis-progress]');
		progress.textContent = PROGRESS[0];
		clearInterval(progressTimer);
		progressTimer = setInterval(() => { p = Math.min(p + 1, PROGRESS.length - 1); progress.textContent = PROGRESS[p]; }, 8000);
		const body = new FormData();
		body.append('video', file);
		fetch(DATA.analyzeUrl || 'quote/analyze', { method: 'POST', body, credentials: 'same-origin' })
			.then((r) => (r.ok ? r.json() : Promise.reject(new Error(r.status))))
			.then((res) => {
				if (run !== analysisRun) return;
				// 영상은 분석 전에 서버에 저장됐으므로 '이사 요청' 때 다시 보내지 않음
				videoInput.removeAttribute('name');
				if (!res.ok) { failAnalysis(res.message); return; }
				state.analyzedFile = file;
				finishAnalysis(res.items || [], [res.demo ? res.message : '', res.memo].filter(Boolean).join(' · '));
			})
			.catch(() => { if (run === analysisRun) failAnalysis('네트워크가 불안정해요. 다시 시도해 주세요'); });
	}
	$('[data-analysis-retry]').addEventListener('click', () => { state.analyzedFile = null; startAnalysis(); });
	$('[data-analysis-manual]').addEventListener('click', () => finishAnalysis([], ''));

	function renderItems() {
		const list = $('[data-item-list]');
		list.innerHTML = '';
		state.items.forEach((it) => {
			const size = sizeOf(it);
			const li = document.createElement('li');
			li.innerHTML = `
				<button type="button" class="action found-item${it.needsReview ? ' needs-review' : ''}" aria-label="${esc(it.name)} ${esc(size.name)} ${it.count}개 수정">
					<span class="found-item-icon"><img alt="" src="${A}${iconOf(it)}"></span>
					<span class="found-item-copy">
						<strong>${esc(it.name)}${it.needsReview ? '<em>확인 필요</em>' : ''}</strong>
						<small>${esc(size.name)} · ${it.count}개</small>
					</span>
					<span class="found-item-cbm">${itemCbm(it).toFixed(1)}<small>CBM</small></span>
					<svg class="found-item-chevron" aria-hidden="true" viewBox="0 0 16 16" width="16" height="16"><path d="m6 3.5 4.5 4.5L6 12.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
				</button>`;
			li.firstElementChild.addEventListener('click', () => openEditor(it, false));
			list.appendChild(li);
		});
		const review = state.items.filter((it) => it.needsReview).length;
		$('[data-review-banner]').hidden = review === 0;
		$('[data-review-count]').textContent = review;
		$('[data-item-kinds]').textContent = state.items.length;
		$('[data-item-total]').textContent = state.items.reduce((s, it) => s + it.count, 0);
		$('[data-cbm-total]').textContent = state.items.reduce((s, it) => s + itemCbm(it), 0).toFixed(1);
	}

	function writeItemInputs() {
		const box = $('[data-item-inputs]');
		box.innerHTML = '';
		state.items.forEach((it, i) => {
			[['name', it.name], ['size', sizeOf(it).grade], ['count', it.count], ['cbm', itemCbm(it).toFixed(2)],
				['detectedName', it.detectedName || ''], ['mapping', it.mapping || ''], ['needsReview', !!it.needsReview]].forEach(([k, v]) => {
				const input = document.createElement('input');
				input.type = 'hidden';
				input.name = `items[${i}].${k}`;
				input.value = v;
				box.appendChild(input);
			});
		});
	}

	/* 품목 수정 시트: detail(크기·수량) ↔ picker(품목 선택) */
	const sheet = $('.item-sheet');
	let draft = null;
	let adding = false;
	const showView = (view) => {
		sheet.dataset.sheetView = view;
		if (view === 'picker') {
			$('[data-picker-search]').value = '';
			renderPicker();
		}
		$(`[data-view="${view}"] .sheet-scroll, [data-view="${view}"] .picker-grid`)?.scrollTo(0, 0);
	};
	function openEditor(item, isNew) {
		adding = isNew;
		draft = { ...item };
		$('[data-edit-title]').textContent = isNew ? '품목 추가' : '품목 수정';
		$('[data-edit-delete]').hidden = isNew;
		$('[data-edit-save]').textContent = isNew ? '추가하기' : '수정 완료';
		openModal('item-edit');
		if (isNew) showView('picker');
		else { showView('detail'); renderDraft(); }
	}
	function renderDraft() {
		const c = catalogItem(draft.name);
		$('[data-edit-icon]').src = A + iconOf(draft);
		$('[data-edit-name]').textContent = draft.name;
		$('[data-edit-category]').textContent = c ? CATEGORY_NAME[c.category] || '' : '직접 입력한 품목';
		const box = $('[data-size-options]');
		box.innerHTML = '';
		const sizes = c ? c.sizes : [sizeOf(draft)];
		const current = sizeOf(draft).code;
		sizes.forEach((z) => {
			const b = document.createElement('button');
			b.type = 'button';
			b.className = 'action size-option' + (z.code === current ? ' active' : '');
			b.setAttribute('role', 'radio');
			b.setAttribute('aria-checked', String(z.code === current));
			const rule = (z.rule || '').replace(/^\[업체 확인\]\s*/, '');
			b.innerHTML = `
				<span class="size-radio" aria-hidden="true"></span>
				<span class="size-copy"><strong>${esc(z.name)}</strong>${rule ? `<small>${esc(rule)}</small>` : ''}</span>
				<span class="size-cbm">${z.cbm.toFixed(1)} CBM</span>`;
			b.addEventListener('click', () => { draft.sizeCode = z.code; renderDraft(); });
			box.appendChild(b);
		});
		$('[data-edit-count-value]').textContent = draft.count;
		$('[data-edit-count="-1"]').disabled = draft.count <= 1;
		$('[data-edit-cbm]').textContent = itemCbm(draft).toFixed(1);
	}
	/* 품목 선택: 분류별 제목 아래 한 목록으로 보여 주고, 위쪽 분류 버튼은 그 제목으로 이동. 검색하면 맞는 품목만 */
	const pickerGrid = $('[data-picker-grid]');
	const pickerTabs = $('.picker-tabs');
	function pickerButton(c) {
		const b = document.createElement('button');
		b.type = 'button';
		b.className = 'action picker-item' + (draft && draft.name === c.name ? ' active' : '');
		b.innerHTML = `<span><img alt="" src="${A}${c.icon}"></span><strong>${esc(c.name)}</strong>`;
		b.addEventListener('click', () => {
			draft.name = c.name;
			if (!c.sizes.some((z) => z.code === draft.sizeCode)) draft.sizeCode = '02';
			showView('detail');
			renderDraft();
		});
		return b;
	}
	function renderPicker() {
		const q = $('[data-picker-search]').value.trim();
		pickerGrid.innerHTML = '';
		pickerTabs.hidden = !!q;
		if (q) {
			const found = CATALOG.filter((c) => c.name.includes(q));
			if (!found.length) {
				pickerGrid.innerHTML = '<p class="picker-empty">찾는 품목이 없어요.<br>가장 비슷한 품목을 골라주세요.</p>';
				return;
			}
			found.forEach((c) => pickerGrid.appendChild(pickerButton(c)));
			return;
		}
		Object.keys(CATEGORY_NAME).forEach((code) => {
			const items = CATALOG.filter((c) => c.category === code);
			if (!items.length) return;
			const h = document.createElement('p');
			h.className = 'picker-group';
			h.dataset.group = code;
			h.textContent = CATEGORY_NAME[code];
			pickerGrid.appendChild(h);
			items.forEach((c) => pickerGrid.appendChild(pickerButton(c)));
		});
		markPickerTab();
	}
	/* 스크롤 위치에 맞는 분류 버튼 표시 */
	function markPickerTab() {
		const top = pickerGrid.scrollTop + 8;
		const atEnd = pickerGrid.scrollTop + pickerGrid.clientHeight >= pickerGrid.scrollHeight - 4;
		const groups = $$('.picker-group', pickerGrid);
		let current = groups[0]?.dataset.group;
		groups.forEach((g) => { if (g.offsetTop - pickerGrid.offsetTop <= top) current = g.dataset.group; });
		if (atEnd && groups.length) current = groups[groups.length - 1].dataset.group;
		$$('[data-picker-tab]').forEach((t) => {
			const on = t.dataset.pickerTab === current;
			t.classList.toggle('active', on);
			t.setAttribute('aria-current', on ? 'true' : 'false');
		});
	}
	pickerGrid.addEventListener('scroll', markPickerTab, { passive: true });
	$('[data-picker-search]').addEventListener('input', renderPicker);
	$$('[data-picker-tab]').forEach((t) => t.addEventListener('click', () => {
		const g = $(`.picker-group[data-group="${t.dataset.pickerTab}"]`, pickerGrid);
		if (g) pickerGrid.scrollTo({ top: g.offsetTop - pickerGrid.offsetTop - 4, behavior: 'smooth' });
	}));
	$('[data-edit-change]').addEventListener('click', () => showView('picker'));
	/* 추가 중에 아직 품목을 안 골랐으면 시트를 닫고, 아니면 상세로 돌아감 */
	$('[data-picker-back]').addEventListener('click', () => {
		if (!draft.name) closeModal('item-edit');
		else showView('detail');
	});
	$$('[data-edit-count]').forEach((b) => b.addEventListener('click', () => {
		draft.count = Math.min(200, Math.max(1, draft.count + Number(b.dataset.editCount)));
		renderDraft();
	}));
	$('[data-edit-delete]').addEventListener('click', () => {
		state.items = state.items.filter((it) => it.id !== draft.id);
		closeModal('item-edit');
		renderItems();
	});
	$('[data-edit-save]').addEventListener('click', () => {
		// 고객이 확인·수정하거나 새로 넣은 품목은 매핑방식 05(사용자 수정)
		const saved = { ...draft, needsReview: false, mapping: '05' };
		state.items = adding ? [...state.items, saved] : state.items.map((it) => (it.id === saved.id ? saved : it));
		closeModal('item-edit');
		renderItems();
	});
	$$('[data-item-add]').forEach((b) => b.addEventListener('click', () => openEditor(
		{ id: `item-${Date.now()}`, name: '', sizeCode: '02', count: 1 }, true)));

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
		// 보관이사(03)를 고르면 보관 기간 선택을 보여 줌
		if (group === 'packType') $('[data-storage-days]').hidden = btn.dataset.value !== '03';
		refreshNext();
	}));

	/* ---------- 3. 추가 작업 (견적서 옵션 칸) ---------- */
	/* 코드 → 아이콘으로 쓸 품목 · 영상에서 찾는 방법 */
	const EXTRA_ICON = { '01': '피아노', '02': '침대', '03': '옷장', '04': 'TV', '05': 'TV장', '07': '안마의자', '08': '에어컨' };
	/* 이사박스 · 주방용품은 "TV장 속 사운드바" 같은 설명이 붙어도 따로 맡길 물건이 아님 */
	const named = (re) => (it) => !['이사박스', '주방용품', '이불·침구', '옷·행거'].includes(it.name)
		&& re.test(`${it.name} ${it.detectedName || ''}`);
	const EXTRA_FIND = {
		'01': (it) => it.name === '피아노',
		'02': (it) => it.name === '침대' && /돌침대|흙침대|모션|안마/.test(it.detectedName || ''),
		'03': named(/조립식|조립 ?장|시스템 ?행거/),
		'04': (it) => it.name === 'TV' && !!it.wallMounted,
		'05': named(/홈 ?[씨시]어터|사운드바/),
		'07': (it) => it.name === '안마의자',
		'08': (it) => it.name === '에어컨',
	};
	state.extras = null; // 코드 → { mode: none | ours | other, first, second, count, found }
	function initExtras() {
		state.extras = {};
		$$('[data-extra]').forEach((li) => {
			const code = li.dataset.extra;
			const hits = state.items.filter(EXTRA_FIND[code] || (() => false));
			// 2in1 에어컨은 스탠드 + 벽걸이 2대
			const found = hits.reduce((s, it) => s + it.count * (code === '08' && it.sizeCode === '03' ? 2 : 1), 0);
			const grand = code === '01' && hits.some((it) => it.sizeCode === '03');
			state.extras[code] = { mode: found ? 'ours' : 'none', first: !grand, second: code === '01' ? grand : true,
				count: Math.max(1, found), found };
		});
	}
	function renderExtras() {
		if (!state.extras) initExtras();
		$$('[data-extra]').forEach((li) => {
			const x = state.extras[li.dataset.extra];
			$('[data-extra-icon]', li).src = A + iconOf({ name: EXTRA_ICON[li.dataset.extra] });
			const found = $('[data-extra-found]', li);
			found.hidden = !x.found;
			found.textContent = `영상에서 ${x.found}개 찾았어요`;
			$$('[data-extra-mode]', li).forEach((b) => {
				const on = b.dataset.extraMode === x.mode;
				b.classList.toggle('active', on);
				b.setAttribute('aria-checked', String(on));
			});
			li.classList.toggle('chosen', x.mode !== 'none');
			$('[data-extra-detail]', li).hidden = x.mode !== 'ours';
			$$('[data-extra-work]', li).forEach((b) => b.classList.toggle('active', !!x[b.dataset.extraWork]));
			$('[data-extra-count-value]', li).textContent = `${x.count}${li.dataset.extra === '03' ? '개' : '대'}`;
			$('[data-extra-count="-1"]', li).disabled = x.count <= 1;
		});
	}
	$$('[data-extra]').forEach((li) => {
		const x = () => state.extras[li.dataset.extra];
		$$('[data-extra-mode]', li).forEach((b) => b.addEventListener('click', () => { x().mode = b.dataset.extraMode; renderExtras(); }));
		$$('[data-extra-work]', li).forEach((b) => b.addEventListener('click', () => {
			const v = x(), k = b.dataset.extraWork, other = k === 'first' ? 'second' : 'first';
			if (li.dataset.extra === '01') { v[k] = true; v[other] = false; } // 피아노: 일반 · 그랜드 중 하나
			else if (!v[k] || v[other]) v[k] = !v[k]; // 하나는 남김
			renderExtras();
		}));
		$$('[data-extra-count]', li).forEach((b) => b.addEventListener('click', () => {
			x().count = Math.min(20, Math.max(1, x().count + Number(b.dataset.extraCount)));
			renderExtras();
		}));
	});
	/* 계산 · 제출용: 고른 것만 (맡김 · 다른 업체) */
	const chosenExtras = () => Object.entries(state.extras || {}).filter(([, v]) => v.mode !== 'none')
		.map(([code, v]) => ({ code, first: v.mode === 'ours' && v.first, second: v.mode === 'ours' && v.second,
			count: v.count, other: v.mode === 'other' }));
	function writeExtraInputs() {
		const box = $('[data-extra-inputs]');
		box.innerHTML = '';
		chosenExtras().forEach((o, i) => Object.entries(o).forEach(([k, v]) => {
			const input = document.createElement('input');
			input.type = 'hidden';
			input.name = `options[${i}].${k}`;
			input.value = v;
			box.appendChild(input);
		}));
	}

	/* ---------- 6. 짐 나르는 방법 (엘리베이터 E / 사다리차 L / 계단 S) ---------- */
	/* 상세주소에서 층수: "3층" → 3, "1203호" → 12 (서버 Floors 와 같은 규칙) */
	const floorOf = (detail) => {
		if (!detail || /지하|\bB\s*\d/i.test(detail)) return null;
		const valid = (n) => (n >= 1 && n <= 80 ? n : null);
		let m = detail.match(/(?<![\dA-Za-z])(\d{1,2})\s*층/);
		if (m) return valid(Number(m[1]));
		m = detail.match(/(?<!\d)(\d{3,4})\s*호/);
		return m ? valid(Math.floor(Number(m[1]) / 100)) : null;
	};
	const sites = { from: { floor: null, carry: null, asked: null }, to: { floor: null, carry: null, asked: null } };
	const CARRY = { E: '엘리베이터', L: '사다리차', S: '계단' };
	const detailOf = (key) => $(`[data-address="${key}"] [data-addr-detail]`).value.trim();
	const carryReady = () => ['from', 'to'].every((k) => sites[k].carry && (sites[k].carry === 'E' || sites[k].floor));
	function renderCarry() {
		$$('[data-carry-site]').forEach((card) => {
			const key = card.dataset.carrySite, s = sites[key];
			const parsed = floorOf(detailOf(key));
			s.floor = parsed || s.asked;
			$('[data-carry-address]', card).textContent = [state.address[key], detailOf(key)].filter(Boolean).join(' ') || '—';
			$('[data-carry-floor-text]', card).textContent = parsed ? `상세주소 기준 ${parsed}층` : '';
			$('[data-carry-floor-ask]', card).hidden = !!parsed || !s.carry || s.carry === 'E';
			$$('[data-carry]', card).forEach((b) => {
				const on = b.dataset.carry === s.carry;
				b.classList.toggle('active', on);
				b.setAttribute('aria-checked', String(on));
			});
			$('[data-carry-floor-value]', card).value = s.floor || '';
			$('[data-carry-value]', card).value = s.carry || '';
		});
		refreshNext();
	}
	$$('[data-carry-site]').forEach((card) => {
		const s = sites[card.dataset.carrySite];
		$$('[data-carry]', card).forEach((b) => b.addEventListener('click', () => { s.carry = b.dataset.carry; renderCarry(); }));
		$('[data-carry-floor-input]', card).addEventListener('input', (e) => {
			const n = parseInt(e.target.value, 10);
			s.asked = n >= 1 && n <= 80 ? n : null;
			renderCarry();
		});
	});

	/* ---------- 8. 요청 사항: 자주 쓰는 말은 눌러서 넣기 ---------- */
	$$('[data-memo-chip]').forEach((b) => b.addEventListener('click', () => {
		const box = $('[data-memo-text]'), text = b.dataset.memoChip;
		const parts = box.value.split(/\s*,\s*/).filter(Boolean);
		const on = !parts.includes(text);
		box.value = (on ? [...parts, text] : parts.filter((p) => p !== text)).join(', ').slice(0, 150);
		b.classList.toggle('active', on);
	}));

	/* ---------- 4. 주소 검색 (카카오 우편번호 서비스, 불러오지 못하면 목업 주소로 대체) ---------- */
	const addrSheet = $$('[data-addr-sheet]');
	const addrSheetBody = $('[data-addr-sheet-body]');
	const closeAddrSheet = () => {
		addrSheet.forEach((el) => (el.hidden = true));
		addrSheetBody.innerHTML = '';
	};
	$$('[data-addr-sheet-close]').forEach((el) => el.addEventListener('click', closeAddrSheet));
	const openPostcode = (label, term, onPick) => {
		$('[data-addr-sheet-title]').textContent = `${label} 주소 검색`;
		addrSheet.forEach((el) => (el.hidden = false));
		addrSheetBody.innerHTML = '';
		new window.daum.Postcode({
			width: '100%',
			height: '100%',
			theme: { searchBgColor: '#346538', queryTextColor: '#FFFFFF' },
			oncomplete: (data) => {
				// 도로명(신주소) + 지번(구주소). 지번을 고르지 않았으면 자동 매칭된 지번 사용
				const building = data.buildingName && data.apartment === 'Y' ? ` (${data.buildingName})` : '';
				onPick({
					road: (data.roadAddress || data.autoRoadAddress) + building,
					jibun: data.jibunAddress || data.autoJibunAddress || '',
					zonecode: data.zonecode,
				});
				closeAddrSheet();
			},
		}).embed(addrSheetBody, { q: term, autoClose: false });
	};

	$$('[data-address]').forEach((wrap) => {
		const key = wrap.dataset.address;
		const label = $('.addr-label', wrap).textContent;
		const q = $('[data-addr-query]', wrap);
		const results = $('[data-addr-results]', wrap);
		const search = () => {
			// 주소 칸은 직접 입력하지 않고, 검색 화면(카카오 우편번호)에서 찾아 고름
			if (window.daum && window.daum.Postcode) {
				openPostcode(label, '', pick);
				return;
			}
			// 우편번호 서비스를 못 불러오면 예비 주소 목록을 보여 줌
			const found = DATA.addresses || [];
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
			$('[data-addr-zip]', wrap).value = addr.zonecode || '';
			$('[data-addr-road]', wrap).textContent = (addr.zonecode ? `[${addr.zonecode}] ` : '') + addr.road;
			$('[data-addr-jibun]', wrap).textContent = addr.jibun ? `지번 ${addr.jibun}` : '';
			$('[data-addr-search]', wrap).hidden = true;
			$('[data-addr-selected]', wrap).hidden = false;
			$('[data-addr-detail-wrap]', wrap).hidden = false;
			results.hidden = true;
			refreshNext();
		};
		$('[data-addr-search-btn]', wrap).addEventListener('click', search);
		q.addEventListener('click', search);
		q.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); search(); } });
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
	const BLOCKED = DATA.blocked || {};
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
				/* 업체 휴무일은 초록 글씨, 예약 불가 · 마감은 흐리게 (둘 다 누를 수 없음) */
				const blocked = !past && BLOCKED[iso];
				span.textContent = d;
				span.className = state.date === iso ? 'picked' : past ? 'muted'
					: blocked ? (blocked === '휴무' ? 'closed blocked' : 'muted blocked') : 'selectable';
				/* 손 없는 날 · 공휴일은 빨간 글씨 (이사가 몰려 비용이 오를 수 있음) */
				const holiday = HOLIDAYS[iso];
				if (!past && !blocked && (NO_SON[iso] || holiday)) {
					span.classList.add('red');
					const why = [holiday, NO_SON[iso] ? `손 없는 날 ${NO_SON[iso]}` : ''].filter(Boolean).join(' · ');
					span.title = why;
					span.setAttribute('aria-label', `${viewM + 1}월 ${d}일 ${why}`);
				}
				if (blocked) {
					span.dataset.reason = blocked;
					span.setAttribute('aria-label', `${viewM + 1}월 ${d}일 ${blocked === '마감' ? '예약 마감' : blocked === '휴무' ? '업체 휴무일' : blocked}`);
					span.setAttribute('aria-disabled', 'true');
				}
				if (!past && !blocked) span.addEventListener('click', () => {
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
		goStep(stepOf('calendar') + 1);
	});
	renderCalendar();

	/* ---------- 9. 예상 견적 (영수증) ---------- */
	/* 서버 EstimateCalculator(대표님 견적 방식)로 계산. 이사 요청 때 저장되는 금액과 같은 식 */
	let estimateRun = 0;
	const won = (f) => (f.min === f.max ? fmtWon(f.min) : `${f.min.toLocaleString('ko-KR')}~${fmtWon(f.max)}`);
	function renderEstimate() {
		const run = ++estimateRun;
		const amountEl = $('[data-est-amount]');
		amountEl.textContent = '계산 중…';
		$('[data-est-breakdown]').hidden = true;
		const body = {
			packType: state.options.packType,
			moveDate: state.dateSkipped ? null : state.date,
			items: state.items.map((it) => ({ name: it.name, sizeCode: sizeOf(it).code, count: it.count })),
			fromAddress: state.address.from,
			toAddress: state.address.to,
			fromDetail: detailOf('from'),
			toDetail: detailOf('to'),
			fromFloor: sites.from.floor,
			fromCarry: sites.from.carry,
			toFloor: sites.to.floor,
			toCarry: sites.to.carry,
			storageDays: state.options.packType === '03' ? Number($('[name=storageDays]').value) : null,
			options: chosenExtras(),
		};
		fetch(DATA.estimateUrl || 'quote/estimate', {
			method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
		})
			.then((r) => (r.ok ? r.json() : Promise.reject(new Error(r.status))))
			.then((est) => {
				if (run !== estimateRun) return;
				$('[data-est-kicker]').textContent = state.dateSkipped ? '예상 견적 범위' : 'AI 예상 견적';
				amountEl.textContent = won({ min: est.min, max: est.max });
				$('[data-est-note]').textContent = state.dateSkipped
					? '이사 날짜에 따라 비용이 달라질 수 있어요.'
					: '선택한 일정과 영상 분석 결과를 반영했어요.';
				$('[data-est-volume]').textContent = `${Number(est.itemCbm).toFixed(1)} CBM · ${est.ton} (${est.trucks})`;
				$('[data-est-fees]').innerHTML = est.fees.map((f) => `<li><span>${esc(f.label)}${
					f.note ? `<small>${esc(f.note)}</small>` : ''}</span><b>${won(f)}</b></li>`).join('');
				$('[data-est-total]').textContent = won({ min: est.min, max: est.max });
				$('[data-est-deposit]').textContent = `약 ${fmtWon(est.deposit)}`;
				$('[data-est-notes]').innerHTML = est.notes.map((n) => `<li>${esc(n)}</li>`).join('');
				$('[data-est-breakdown]').hidden = false;
			})
			.catch(() => {
				if (run !== estimateRun) return;
				amountEl.textContent = '잠시 후 다시 확인해주세요';
			});
	}

	function renderSummary() {
		renderEstimate();
		$('[data-sum="date"]').textContent = state.dateSkipped ? '일정 미정' : dateLabel() || '—';
		const siteText = (k) => [sites[k].floor ? `${sites[k].floor}층` : '', CARRY[sites[k].carry] || '']
			.filter(Boolean).join(' · ');
		['from', 'to'].forEach((k) => {
			$(`[data-sum="${k}"]`).textContent = state.address[k]
				? [state.address[k], detailOf(k)].filter(Boolean).join(' ') + (siteText(k) ? ` (${siteText(k)})` : '') : '—';
		});
		$('[data-sum="options"]').textContent = state.options.houseType && state.options.packType
			? `${state.optionLabels.houseType} · ${state.optionLabels.packType}` : '—';
	}

	/* ---------- 쉬운 모드 음성 안내 ---------- */
	const VOICE = {
		upload: () => '영상 올리기 단계입니다. 집 안 전체가 보이도록 촬영한 동영상을 올려주세요. 동영상 선택 버튼을 누르면 됩니다.',
		analysis: () => state.analysisDone
			? '영상 분석이 끝났습니다. 찾은 짐 목록을 확인해주세요. 빠진 짐이 있거나 정보가 다르면 해당 품목을 눌러 수정할 수 있습니다.'
			: '짐 확인 단계입니다. 올려주신 영상에서 가구와 짐을 찾고 있습니다. 잠시 기다려주세요.',
		extras: () => '추가 작업 단계입니다. 에어컨이나 안마의자처럼 설치가 필요한 물건을 업체에 맡길지, 다른 업체에 맡길지 골라주세요.',
		options: () => '이사 정보 단계입니다. 현재 사는 집의 종류와 원하는 포장 방식을 차례로 선택해주세요.',
		address: () => '주소 입력 단계입니다. 출발할 곳과 도착할 곳의 주소를 차례로 입력하고, 상세주소에 동과 호수를 적어주세요.',
		carry: () => '나르는 방법 단계입니다. 출발지와 도착지에서 엘리베이터, 사다리차, 계단 중 하나를 골라주세요.',
		calendar: () => '날짜 선택 단계입니다. 달력에서 이사할 날짜를 고르고, 아래에서 시간을 선택해주세요. 빨간 날짜는 비용이 오를 수 있습니다.',
		memo: () => '요청 사항 단계입니다. 귀중품이나 담당자에게 남길 말이 있으면 적어주세요. 없으면 다음을 누르세요.',
		summary: () => '견적 확인 단계입니다. 예상 금액과 입력한 이사 정보를 확인해주세요. 바꿀 내용이 있으면 수정 버튼을 누르세요.',
	};
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
		const u = new SpeechSynthesisUtterance(VOICE[STEPS[state.step].type]());
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
