/*
 * 고객 상담 (SCR-U-017~019): 메시지를 서버(/chat/messages)에 저장하고 챗봇 답변을 받음. 상담사 답장은 5초마다 확인.
 * 사진 · 동영상 · PDF 첨부, 내 메시지를 꾹 누르거나(휴대폰) 오른쪽 클릭(PC)하면 수정 · 삭제.
 * 수정은 보낸 지 하루 안, 삭제는 하루 안 + 상담사가 아직 읽지 않았을 때만 (서버가 최종 확인).
 */
(function () {
	const root = document.querySelector('[data-chat]');
	if (!root) return;

	const A = root.dataset.assets;
	const URL_MESSAGES = root.dataset.url;
	const LINKS = { move: root.dataset.linkMove, quote: root.dataset.linkQuote, estimates: root.dataset.linkEstimates };
	const $ = (sel) => root.querySelector(sel);
	const thread = $('[data-chat-thread]');
	const editor = $('[data-chat-editor]');
	const sendBtn = $('[data-chat-send]');
	const fileInput = $('[data-chat-file]');
	let attachment = null;
	let lastId = 0;
	const byId = new Map(); // 메시지 번호 → { wrap, state }

	const now = () => new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());

	const el = (tag, cls, text) => {
		const e = document.createElement(tag);
		if (cls) e.className = cls;
		if (text !== undefined) e.textContent = text;
		return e;
	};
	/* 챗봇 = 명품 이사 도우미 캐릭터, 상담사(관리자 답장) = 트럭 프로필 */
	const avatar = (role) => {
		const box = el('div', 'chat-mini-avatar');
		const img = el('img');
		img.alt = role === 'admin' ? '상담사' : '';
		img.src = role === 'admin' ? `${A}counselor-truck.svg` : `${A}mover-assistant-3d.svg`;
		box.appendChild(img);
		return box;
	};
	const toast = (text) => {
		const t = el('div', 'save-toast', text);
		document.querySelector('.app-shell').appendChild(t);
		setTimeout(() => t.remove(), 2600);
	};

	/* 첨부: 사진은 그대로, 동영상은 재생, 그 밖(PDF)은 파일 이름 링크 */
	function attachmentNode(file, url, kind) {
		if (kind === 'image' && url) {
			const a = el('a', 'chat-photo');
			a.href = url;
			a.target = '_blank';
			a.rel = 'noopener';
			const img = el('img');
			img.alt = file || '사진';
			img.loading = 'lazy';
			img.src = url;
			a.appendChild(img);
			return a;
		}
		if (kind === 'video' && url) {
			const v = el('video', 'chat-video');
			v.controls = true;
			v.preload = 'metadata';
			v.playsInline = true;
			v.src = url;
			return v;
		}
		const f = el(url ? 'a' : 'div', 'chat-file-message');
		if (url) { f.href = url; f.target = '_blank'; f.rel = 'noopener'; }
		const img = el('img');
		img.alt = '';
		img.src = `${A}paperclip.svg`;
		f.append(img, el('span', '', file));
		return f;
	}

	/* 메시지 한 줄의 내용 부분을 상태에 맞게 다시 그림 */
	function paint(entry) {
		const { box, state } = entry;
		box.innerHTML = '';
		if (state.deleted) {
			box.appendChild(el('div', `message ${state.role} deleted`, '삭제된 메시지예요'));
			return;
		}
		if (state.text) box.appendChild(el('div', `message ${state.role}`, state.text));
		if (state.file || state.fileUrl) box.appendChild(attachmentNode(state.file, state.fileUrl, state.fileKind));
		if (!state.text && !state.file && !state.fileUrl) box.appendChild(el('div', `message ${state.role}`, ''));
	}

	/* m: { id, role(user | bot | admin), text, file, fileUrl, fileKind, time, edited, deleted, editBlock, deleteBlock } */
	function addMessage(m, actions) {
		$('[data-chat-restart]').hidden = false;
		const role = m.role;
		const wrap = el('div', `chat-message-wrap ${role}`);
		if (role !== 'user') wrap.appendChild(avatar(role));
		const content = el('div', 'chat-message-content');
		if (role === 'admin') content.appendChild(el('span', 'chat-sender-name', '상담사'));
		const box = el('div', 'chat-message-body');
		content.appendChild(box);
		if (actions) {
			const qr = el('div', 'chat-quick-replies');
			actions.forEach((a) => {
				const b = el(a.link ? 'a' : 'button', 'action', a.label);
				if (a.link) b.href = LINKS[a.link];
				else { b.type = 'button'; b.addEventListener('click', () => ask(a.prompt)); }
				qr.appendChild(b);
			});
			content.appendChild(qr);
		}
		const meta = el('small', 'chat-message-time', m.time || now());
		content.appendChild(meta);
		wrap.appendChild(content);
		thread.appendChild(wrap);
		const entry = { wrap, box, meta, state: { ...m } };
		paint(entry);
		markEdited(entry);
		if (m.id) byId.set(m.id, entry);
		if (role === 'user') attachMenu(entry);
		/* 아래 고정 입력창에 가리지 않게, 입력창 높이만큼 위에서 멈춤 */
		wrap.style.scrollMarginBottom = `${($('.chat-input') || { offsetHeight: 60 }).offsetHeight + 28}px`;
		wrap.scrollIntoView({ behavior: 'smooth', block: 'end' });
		return entry;
	}

	function markEdited(entry) {
		const s = entry.state;
		entry.meta.textContent = (s.edited && !s.deleted ? '수정됨 · ' : '') + (s.time || '');
	}

	/* 서버에서 받은 최신 상태로 고침 (수정 · 삭제 · 읽음) */
	function applyState(s) {
		const entry = byId.get(s.id);
		if (!entry) return;
		const changed = entry.state.text !== s.text || entry.state.deleted !== s.deleted || entry.state.edited !== s.edited;
		entry.state = { ...entry.state, ...s };
		if (changed) { paint(entry); markEdited(entry); }
	}

	/* ---------- 내 메시지 수정 · 삭제 메뉴 ---------- */
	const menu = el('div', 'chat-menu');
	menu.setAttribute('role', 'menu');
	menu.hidden = true;
	const editItem = el('button', 'action', '수정');
	const deleteItem = el('button', 'action danger', '삭제');
	[editItem, deleteItem].forEach((b) => { b.type = 'button'; b.setAttribute('role', 'menuitem'); menu.appendChild(b); });
	root.appendChild(menu);
	let menuEntry = null;
	const closeMenu = () => { menu.hidden = true; menuEntry = null; };
	document.addEventListener('click', (e) => { if (!menu.contains(e.target)) closeMenu(); });
	document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeMenu(); });
	let openedAt = 0;
	thread.addEventListener('scroll', () => { if (Date.now() - openedAt > 400) closeMenu(); }, { passive: true });

	function openMenu(entry, x, y) {
		if (!entry.state.id || entry.state.deleted) return;
		menuEntry = entry;
		openedAt = Date.now();
		editItem.classList.toggle('blocked', !!entry.state.editBlock);
		deleteItem.classList.toggle('blocked', !!entry.state.deleteBlock);
		menu.hidden = false;
		const r = root.getBoundingClientRect();
		const w = menu.offsetWidth, h = menu.offsetHeight;
		menu.style.left = `${Math.min(Math.max(8, x - r.left - w + 12), r.width - w - 8)}px`;
		menu.style.top = `${Math.min(y - r.top + 6, r.height - h - 90)}px`;
	}

	function attachMenu(entry) {
		const target = entry.wrap.querySelector('.chat-message-content');
		target.addEventListener('contextmenu', (e) => { e.preventDefault(); openMenu(entry, e.clientX, e.clientY); });
		let timer = null;
		target.addEventListener('touchstart', (e) => {
			const t = e.touches[0];
			timer = setTimeout(() => { timer = null; openMenu(entry, t.clientX, t.clientY); }, 500);
		}, { passive: true });
		['touchend', 'touchmove', 'touchcancel'].forEach((ev) => target.addEventListener(ev, () => clearTimeout(timer), { passive: true }));
	}

	const post = (url, data) => fetch(url, {
		method: 'POST', credentials: 'same-origin',
		headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams(data || {}).toString(),
	}).then((r) => (r.ok ? r.json() : Promise.reject(r)));

	editItem.addEventListener('click', (e) => {
		e.stopPropagation();
		const entry = menuEntry;
		closeMenu();
		if (!entry) return;
		if (entry.state.editBlock) { toast(entry.state.editBlock); return; }
		openEditor(entry);
	});
	deleteItem.addEventListener('click', (e) => {
		e.stopPropagation();
		const entry = menuEntry;
		closeMenu();
		if (!entry) return;
		if (entry.state.deleteBlock) { toast(entry.state.deleteBlock); return; }
		if (!confirm('이 메시지를 삭제할까요? 상담사에게도 삭제된 메시지로 보여요.')) return;
		post(`${URL_MESSAGES}/${entry.state.id}/delete`).then((res) => {
			toast(res.message);
			if (res.ok) applyState({ id: entry.state.id, deleted: true, text: '', file: null, fileUrl: null, fileKind: '' });
		}).catch(() => toast('삭제하지 못했어요. 잠시 후 다시 시도해 주세요.'));
	});

	/* 수정 창 */
	const sheet = $('[data-chat-edit]');
	const sheetText = $('[data-chat-edit-text]');
	let editing = null;
	function openEditor(entry) {
		editing = entry;
		sheetText.value = entry.state.text || '';
		sheet.hidden = false;
		$('[data-chat-edit-scrim]').hidden = false;
		sheetText.focus();
	}
	const closeEditor = () => { sheet.hidden = true; $('[data-chat-edit-scrim]').hidden = true; editing = null; };
	root.querySelectorAll('[data-chat-edit-close]').forEach((b) => b.addEventListener('click', closeEditor));
	$('[data-chat-edit-save]').addEventListener('click', () => {
		const entry = editing;
		const text = sheetText.value.trim();
		if (!entry || !text) return;
		post(`${URL_MESSAGES}/${entry.state.id}/edit`, { text }).then((res) => {
			toast(res.message);
			if (res.ok) { applyState({ id: entry.state.id, text, edited: true }); closeEditor(); }
		}).catch(() => toast('고치지 못했어요. 잠시 후 다시 시도해 주세요.'));
	});

	/* ---------- 보내기 ---------- */
	function ask(value) {
		const text = (value || '').trim();
		const file = attachment;
		if (!text && !file) return;
		const kind = file ? (file.type.startsWith('image/') ? 'image' : file.type.startsWith('video/') ? 'video' : 'file') : '';
		const local = file && kind !== 'file' ? URL.createObjectURL(file) : null;
		const entry = addMessage({ role: 'user', text, file: file && file.name, fileUrl: local, fileKind: kind,
			editBlock: '전송 중이에요.', deleteBlock: '전송 중이에요.' });
		editor.innerText = '';
		setAttachment(null);
		const typing = el('div', 'chat-message-wrap bot');
		typing.appendChild(avatar());
		const dots = el('div', 'chat-typing');
		dots.setAttribute('aria-label', '답변 작성 중');
		dots.append(el('i'), el('i'), el('i'));
		typing.appendChild(dots);
		thread.appendChild(typing);
		const body = new FormData();
		body.append('text', text);
		if (file) body.append('file', file);
		fetch(URL_MESSAGES, { method: 'POST', body })
			.then((r) => (r.ok ? r.json() : Promise.reject(r)))
			.then((res) => {
				typing.remove();
				if (res.error) {
					entry.wrap.remove();
					addMessage({ role: 'bot', text: res.error });
					return;
				}
				// 서버 번호를 받으면 수정 · 삭제 가능 (보낸 직후는 상담사가 안 읽은 상태)
				entry.state.id = res.id;
				entry.state.editBlock = text ? null : '글이 없는 메시지는 고칠 수 없어요.';
				entry.state.deleteBlock = null;
				if (res.fileUrl) { entry.state.fileUrl = res.fileUrl; entry.state.fileKind = res.fileKind; paint(entry); }
				byId.set(res.id, entry);
				lastId = Math.max(lastId, res.lastId || 0);
				if (res.reply) addMessage({ role: 'bot', text: res.reply.text }, res.reply.actions);
			})
			.catch(() => {
				typing.remove();
				addMessage({ role: 'bot', text: '메시지를 보내지 못했어요. 잠시 후 다시 시도해주세요.' });
			});
	}

	/* 이전 상담 내용 */
	(window.CHAT_HISTORY || []).forEach((m) => {
		addMessage(m);
		lastId = Math.max(lastId, m.id);
	});

	/* 상담사 답장 · 수정 · 삭제 · 읽음 확인 */
	setInterval(() => {
		if (document.hidden) return;
		fetch(`${URL_MESSAGES}?after=${lastId}`)
			.then((r) => (r.ok ? r.json() : { messages: [], states: [] }))
			.then((res) => {
				(res.messages || []).forEach((m) => {
					lastId = Math.max(lastId, m.id);
					if (!byId.has(m.id)) addMessage(m);
				});
				(res.states || []).forEach(applyState);
			})
			.catch(() => {});
	}, 5000);

	function refreshSend() {
		sendBtn.classList.toggle('is-disabled', !editor.innerText.trim() && !attachment);
	}
	function setAttachment(file) {
		attachment = file;
		$('[data-chat-chip]').hidden = !file;
		$('[data-chat-chip-name]').textContent = file ? file.name : '';
		if (!file) fileInput.value = '';
		refreshSend();
	}

	root.querySelectorAll('[data-ask]').forEach((b) => b.addEventListener('click', () => ask(b.dataset.ask)));
	editor.addEventListener('input', refreshSend);
	editor.addEventListener('keydown', (e) => {
		if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); ask(editor.innerText); }
	});
	sendBtn.addEventListener('click', () => ask(editor.innerText));
	$('[data-chat-attach]').addEventListener('click', () => fileInput.click());
	fileInput.addEventListener('change', () => {
		const f = fileInput.files[0];
		if (!f) return;
		if (f.size > 100 * 1024 * 1024) { toast('100MB 보다 작은 파일만 보낼 수 있어요.'); fileInput.value = ''; return; }
		setAttachment(f);
	});
	$('[data-chat-chip-remove]').addEventListener('click', () => setAttachment(null));
})();
