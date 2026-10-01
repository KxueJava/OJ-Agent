function refreshIcons() { if (window.lucide) window.lucide.createIcons(); }

function toast(message) {
  let item = document.querySelector('.toast');
  if (!item) {
    item = document.createElement('div');
    item.className = 'toast';
    item.style.cssText = 'position:fixed;z-index:30;right:20px;bottom:20px;padding:10px 13px;border:1px solid #b8d1fa;border-radius:5px;color:#245fae;background:#f7faff;box-shadow:0 5px 18px rgb(26 48 79 / 12%);font-size:12px;font-weight:650;opacity:0;transform:translateY(8px);transition:.18s';
    document.body.append(item);
  }
  item.textContent = message;
  requestAnimationFrame(() => { item.style.opacity = '1'; item.style.transform = 'translateY(0)'; });
  clearTimeout(window.toastTimer);
  window.toastTimer = setTimeout(() => { item.style.opacity = '0'; item.style.transform = 'translateY(8px)'; }, 2200);
}

document.addEventListener('DOMContentLoaded', () => {
  refreshIcons();
  document.querySelectorAll('[data-toast]').forEach(button => button.addEventListener('click', () => toast(button.dataset.toast)));
  document.querySelectorAll('[data-workspace-tab]').forEach(button => button.addEventListener('click', () => {
    const target = button.dataset.workspaceTab;
    document.querySelectorAll('[data-workspace-tab]').forEach(item => item.classList.toggle('active', item === button));
    document.querySelectorAll('[data-workspace-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.workspacePanel === target));
    if (target === 'agent') document.querySelector('.agent-workbench')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }));
  document.querySelectorAll('[data-hint]').forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll('[data-hint]').forEach(item => item.classList.toggle('selected', item === button));
    const answer = document.querySelector('[data-agent-answer]');
    const messages = {
      one: '先想想：每次遍历到一个数字时，能否快速判断它需要的配对数字是否已经出现？',
      two: '维护一个“数值 -> 下标”的 HashMap。处理 nums[i] 前，先查询 target - nums[i]。',
      three: '关键顺序：先检查 complement 是否存在，再写入当前值；这样不会把同一元素配对给自己。'
    };
    if (answer) answer.textContent = messages[button.dataset.hint];
  }));
  const state = document.querySelector('[data-console-state]');
  const output = document.querySelector('[data-console-output]');
  const verdict = document.querySelector('[data-verdict]');
  function execute(kind) {
    if (!state || !output || !verdict) return;
    state.textContent = kind === 'submit' ? '判题队列中...' : '正在运行样例...';
    output.textContent = '正在编译 Java 21 代码...';
    verdict.className = 'verdict running';
    verdict.innerHTML = '<i data-lucide="loader-circle"></i><span>' + (kind === 'submit' ? 'Judging' : 'Running') + '</span>';
    refreshIcons();
    setTimeout(() => {
      state.textContent = kind === 'submit' ? '判题完成' : '样例运行完成';
      output.textContent = kind === 'submit' ? '全部测试点通过。运行时间：42 ms，内存：41.2 MB。' : '样例 1：通过\n样例 2：通过\n输出与预期一致。';
      verdict.className = 'verdict accepted';
      verdict.innerHTML = '<i data-lucide="circle-check"></i><span>Accepted</span>';
      refreshIcons();
      toast(kind === 'submit' ? '提交已完成，所有测试点通过。' : '样例运行完成。');
    }, 820);
  }
  document.querySelector('[data-submit]')?.addEventListener('click', () => execute('submit'));
  document.querySelector('[data-run]')?.addEventListener('click', () => execute('run'));
});
