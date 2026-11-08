/* 고객 상담 (SCR-U-017~019): 메시지를 서버(/chat/messages)에 저장하고 챗봇 답변을 받음. 상담사 답장은 5초마다 확인 */
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

	const now = () => new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());

	const el = (tag, cls, text) => {
		const e = document.createElement(tag);
		if (cls) e.className = cls;
		if (text !== undefined) e.textContent = text;
		return e;
	};
	const avatar = () => {
		const box = el('div', 'chat-mini-avatar');
		const img = el('img');
		img.alt = '';
		img.src = `${A}mover-assistant-3d.svg`;
		box.appendChild(img);
		return box;
	};

	function addMessage(role, text, file, actions, time) {
		$('[data-chat-restart]').hidden = false;
		const wrap = el('div', `chat-message-wrap ${role}`);
		if (role === 'bot') wrap.appendChild(avatar());
		const content = el('div', 'chat-message-content');
		content.appendChild(el('div', `message ${role}`, text));
		if (file) {
			const f = el('div', 'chat-file-message');
			const img = el('img');
			img.alt = '';
			img.src = `${A}paperclip.svg`;
			f.append(img, el('span', '', file));
			content.appendChild(f);
		}
		if (actions) {
			const box = el('div', 'chat-quick-replies');
			actions.forEach((a) => {
				const b = el(a.link ? 'a' : 'button', 'action', a.label);
				if (a.link) b.href = LINKS[a.link];
				else { b.type = 'button'; b.addEventListener('click', () => ask(a.prompt)); }
				box.appendChild(b);
			});
			content.appendChild(box);
		}
		content.appendChild(el('small', 'chat-message-time', time || now()));
		wrap.appendChild(content);
		thread.appendChild(wrap);
		wrap.scrollIntoView({ behavior: 'smooth', block: 'end' });
	}

	function ask(value) {
		const text = (value || '').trim();
		const file = attachment;
		if (!text && !file) return;
		addMessage('user', text || '파일을 첨부했어요.', file && file.name);
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
				lastId = Math.max(lastId, res.lastId || 0);
				if (res.reply) addMessage('bot', res.reply.text, null, res.reply.actions);
			})
			.catch(() => {
				typing.remove();
				addMessage('bot', '메시지를 보내지 못했어요. 잠시 후 다시 시도해주세요.');
			});
	}

	/* 이전 상담 내용 */
	(window.CHAT_HISTORY || []).forEach((m) => {
		addMessage(m.role === 'user' ? 'user' : 'bot', m.text || '파일을 첨부했어요.', m.file, null, m.time);
		lastId = Math.max(lastId, m.id);
	});

	/* 상담사 답장 확인 */
	setInterval(() => {
		if (document.hidden) return;
		fetch(`${URL_MESSAGES}?after=${lastId}`)
			.then((r) => (r.ok ? r.json() : []))
			.then((list) => list.forEach((m) => {
				lastId = Math.max(lastId, m.id);
				addMessage('bot', `[상담사] ${m.text}`, null, null, m.time);
			}))
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
	fileInput.addEventListener('change', () => fileInput.files[0] && setAttachment(fileInput.files[0]));
	$('[data-chat-chip-remove]').addEventListener('click', () => setAttachment(null));
})();
