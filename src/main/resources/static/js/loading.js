/* 화면을 옮기거나 저장할 때 위에 진행 표시줄 (눌렀다는 것이 바로 보이게). 다음 화면이 뜨면 저절로 사라짐 */
(() => {
	let bar = null, timer = null;
	const stop = () => { clearInterval(timer); if (bar) { bar.remove(); bar = null; } };
	const start = () => {
		if (bar) return;
		bar = document.createElement('div');
		bar.setAttribute('role', 'progressbar');
		bar.setAttribute('aria-label', '불러오는 중');
		bar.style.cssText = 'position:fixed;left:0;top:0;z-index:9999;height:3px;width:0;background:#f2c94c;box-shadow:0 0 8px rgb(242 201 76 / .8);transition:width .4s ease;pointer-events:none';
		document.body.appendChild(bar);
		let w = 12;
		requestAnimationFrame(() => { if (bar) bar.style.width = w + '%'; });
		timer = setInterval(() => { w += (92 - w) * 0.12; if (bar) bar.style.width = w + '%'; }, 400);
		setTimeout(stop, 30000); // 파일 받기처럼 화면이 안 바뀌는 경우
	};
	document.addEventListener('click', (e) => {
		const a = e.target.closest && e.target.closest('a[href]');
		if (!a || e.button || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || a.target || a.hasAttribute('download')) return;
		const url = new URL(a.href, location.href);
		if (url.origin !== location.origin || (url.pathname === location.pathname && url.search === location.search && url.hash)) return;
		setTimeout(() => { if (!e.defaultPrevented) start(); }, 0);
	});
	document.addEventListener('submit', (e) => setTimeout(() => { if (!e.defaultPrevented) start(); }, 0));
	window.addEventListener('pageshow', stop);
})();
