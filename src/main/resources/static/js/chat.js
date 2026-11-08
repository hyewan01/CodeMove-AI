/* 고객 챗봇 (SCR-U-017~019) - 프로토타입 규칙 기반 응답. 챗봇 API/상담원 채팅 연동 시 서버 응답으로 교체 */
(function () {
	const root = document.querySelector('[data-chat]');
	if (!root) return;

	const A = root.dataset.assets;
	const status = root.dataset.status;
	const userName = root.dataset.user;
	const LINKS = { move: root.dataset.linkMove, quote: root.dataset.linkQuote, estimates: root.dataset.linkEstimates };
	const $ = (sel) => root.querySelector(sel);
	const thread = $('[data-chat-thread]');
	const editor = $('[data-chat-editor]');
	const sendBtn = $('[data-chat-send]');
	const fileInput = $('[data-chat-file]');
	let attachment = '';
	let replyTimer = null;

	const now = () => new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());

	function getReply(value, fileName) {
		if (fileName) return {
			text: `${fileName} 파일을 확인 요청에 첨부했어요.\n개인정보가 포함된 문서는 필요한 부분만 남기고 가려서 보내주세요.`,
			actions: [{ label: '첨부 관련 문의', prompt: '첨부한 파일로 어떤 상담을 받을 수 있나요?' }, { label: '상담사 연결', prompt: '상담사 연결 요청' }],
		};
		if (value.includes('상담사')) return value.includes('요청')
			? { text: '상담사 연결 요청을 접수했어요.\n운영시간에는 순서대로 상담이 시작되며, 답변이 오면 알림으로 알려드릴게요.', actions: [{ label: '자주 묻는 질문 보기', prompt: '자주 묻는 질문을 알려주세요' }] }
			: { text: '상담사에게 직접 문의할 수 있어요.\n상담 운영시간은 평일 09:00–18:00이며, 운영시간 외 요청은 다음 영업일에 확인합니다.', actions: [{ label: '상담사 연결 요청', prompt: '상담사 연결 요청' }] };
		if (value.includes('예약') || value.includes('일정')) return status === 'confirmed'
			? { text: `${userName}님의 이사 예약은 2026년 3월 18일 오전 10시로 확정되어 있어요.\n출발지는 강남구, 도착지는 서초구입니다.`, actions: [{ label: '예약 정보 보기', link: 'move' }] }
			: { text: '아직 확정된 예약은 없어요.\n도착한 견적서를 확인하고 확정하면 예약 일정이 생성됩니다.', actions: [{ label: '견적 정보 보기', link: 'move' }, { label: '예약 과정 안내', prompt: '견적 확정 후 예약 과정이 궁금해요' }] };
		if (value.includes('영상') || value.includes('분석') || value.includes('CBM')) return {
			text: '집 안을 촬영한 영상을 올리면 AI가 가구와 짐의 부피를 분석해 예상 CBM과 견적을 계산해요.\n영상은 방 전체가 끊기지 않게 천천히 촬영하면 더 정확합니다.',
			actions: [{ label: '영상 견적 시작', link: 'quote' }, { label: '촬영 방법 보기', prompt: '영상을 어떻게 촬영해야 하나요?' }],
		};
		if (value.includes('견적') || value.includes('비용')) {
			if (status === 'approved') return { text: '담당자가 확인한 견적이 준비되어 있어요.\n현재 견적 금액은 410,000원이며, 견적서의 작업 내용과 별도 비용을 확인한 뒤 확정할 수 있습니다.', actions: [{ label: '견적서 보기', link: 'move' }] };
			if (status === 'waiting-approval') return { text: '요청하신 견적을 담당자가 확인하고 있어요.\n견적이 준비되면 홈 알림과 견적내역에서 바로 알려드릴게요.', actions: [{ label: '견적내역 보기', link: 'estimates' }] };
			if (status === 'confirmed') return { text: '410,000원 견적을 확정해 예약이 완료된 상태예요.\n확정된 작업 내용은 예약 정보와 견적서에서 다시 확인할 수 있습니다.', actions: [{ label: '예약 정보 보기', link: 'move' }] };
			return { text: '영상 견적을 요청하면 AI 예상 견적을 바탕으로 담당자가 상세 내용을 확인해요.', actions: [{ label: '새 견적 받기', link: 'quote' }] };
		}
		if (value.includes('준비') || value.includes('체크리스트') || value.includes('촬영')) return {
			text: '이사 전에는 귀중품 별도 보관, 냉장고 음식 정리, 주소 이전, 가전 설치 일정을 먼저 확인해주세요.\n이사 전날에는 주차 공간과 엘리베이터 예약도 확인하는 것이 좋아요.',
			actions: [{ label: '예약 일정 확인', prompt: '내 예약 일정 확인' }, { label: '추가 준비 팁', prompt: '이사 당일 준비 팁도 알려주세요' }],
		};
		if (value.includes('결제') || value.includes('환불') || value.includes('예약금')) return {
			text: '견적 확정 후 안내된 예약금을 결제하면 예약이 최종 접수돼요.\n취소·환불 가능 금액은 작업일과 취소 시점에 따라 달라질 수 있어 상담사 확인이 필요합니다.',
			actions: [{ label: '상담사에게 문의', prompt: '결제 및 환불 상담사 연결 요청' }, { label: '예약 정보 보기', link: 'move' }],
		};
		if (value.includes('자주 묻는')) return {
			text: '많이 묻는 내용은 견적 산정 방식, 영상 촬영 방법, 예약 변경, 사다리차와 추가 비용, 취소·환불 기준이에요.\n궁금한 항목을 메시지로 입력해주세요.',
			actions: [{ label: '추가 비용', prompt: '추가 비용이 발생하는 경우를 알려주세요' }, { label: '예약 변경', prompt: '예약 일정을 변경하고 싶어요' }],
		};
		return {
			text: '문의 내용을 확인했어요.\n견적, 예약 일정, 영상 분석, 결제·환불 중 어떤 도움이 필요한지 조금 더 자세히 알려주세요.',
			actions: [{ label: '견적 문의', prompt: '이사 견적 알아보기' }, { label: '예약 문의', prompt: '예약 및 일정 확인' }, { label: '상담사 연결', prompt: '상담사 연결' }],
		};
	}

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

	function addMessage(role, text, file, actions) {
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
		content.appendChild(el('small', 'chat-message-time', now()));
		wrap.appendChild(content);
		thread.appendChild(wrap);
		wrap.scrollIntoView({ behavior: 'smooth', block: 'end' });
	}

	function ask(value) {
		const text = (value || '').trim();
		const file = attachment;
		if (!text && !file) return;
		addMessage('user', text || '파일을 첨부했어요.', file);
		editor.innerText = '';
		setAttachment('');
		const typing = el('div', 'chat-message-wrap bot');
		typing.appendChild(avatar());
		const dots = el('div', 'chat-typing');
		dots.setAttribute('aria-label', '답변 작성 중');
		dots.append(el('i'), el('i'), el('i'));
		typing.appendChild(dots);
		thread.appendChild(typing);
		clearTimeout(replyTimer);
		replyTimer = setTimeout(() => {
			typing.remove();
			const r = getReply(text, file);
			addMessage('bot', r.text, null, r.actions);
		}, 650);
	}

	function refreshSend() {
		sendBtn.classList.toggle('is-disabled', !editor.innerText.trim() && !attachment);
	}
	function setAttachment(name) {
		attachment = name;
		$('[data-chat-chip]').hidden = !name;
		$('[data-chat-chip-name]').textContent = name;
		if (!name) fileInput.value = '';
		refreshSend();
	}

	root.querySelectorAll('[data-ask]').forEach((b) => b.addEventListener('click', () => ask(b.dataset.ask)));
	editor.addEventListener('input', refreshSend);
	editor.addEventListener('keydown', (e) => {
		if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); ask(editor.innerText); }
	});
	sendBtn.addEventListener('click', () => ask(editor.innerText));
	$('[data-chat-attach]').addEventListener('click', () => fileInput.click());
	fileInput.addEventListener('change', () => fileInput.files[0] && setAttachment(fileInput.files[0].name));
	$('[data-chat-chip-remove]').addEventListener('click', () => setAttachment(''));
})();
