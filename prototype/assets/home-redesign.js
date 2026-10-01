(() => {
  const toast = document.querySelector('.home-toast');
  let timer;
  document.querySelectorAll('[data-toast]').forEach((button) => button.addEventListener('click', () => {
    toast.textContent = button.dataset.toast;
    toast.hidden = false;
    clearTimeout(timer);
    timer = setTimeout(() => { toast.hidden = true; }, 2200);
  }));
  const rows = [...document.querySelectorAll('.problem-line')];
  const count = document.querySelector('[data-count]');
  document.querySelectorAll('[data-filter]').forEach((button) => button.addEventListener('click', () => {
    document.querySelectorAll('[data-filter]').forEach((item) => item.classList.toggle('selected', item === button));
    const filter = button.dataset.filter;
    let visible = 0;
    rows.forEach((row) => { const show = filter === '全部' || row.dataset.difficulty === filter; row.hidden = !show; if (show) visible += 1; });
    count.textContent = `显示 ${visible} / 10 道推荐题`;
    document.querySelector('.empty-inline').hidden = visible !== 0;
  }));
})();
