const metricData = {
  views: { value: '24.8k', label: 'Vistas', growth: '12.6%', bars: [42, 58, 47, 76, 63, 91, 72] },
  likes: { value: '3,420', label: 'Likes', growth: '8.4%', bars: [37, 49, 66, 54, 79, 68, 87] },
  comments: { value: '684', label: 'Comentarios', growth: '5.2%', bars: [29, 45, 40, 61, 54, 73, 65] },
};

const value = document.querySelector('#metric-value');
const title = document.querySelector('#metric-title');
const growth = document.querySelector('#metric-growth');
const bars = document.querySelector('#metric-bars');
const chart = document.querySelector('.chart');

function renderBars(data) {
  bars.replaceChildren(...data.bars.map((height, index) => {
    const bar = document.createElement('i');
    bar.className = `bar${index === data.bars.length - 2 ? ' is-highlight' : ''}`;
    bar.style.height = `${height}%`;
    return bar;
  }));
  chart.setAttribute('aria-label', `Actividad de ${data.label.toLowerCase()} en los últimos siete días`);
}

document.querySelectorAll('.metric-option').forEach((button) => {
  button.addEventListener('click', () => {
    const metric = metricData[button.dataset.metric];
    if (!metric) return;
    document.querySelectorAll('.metric-option').forEach((option) => {
      const selected = option === button;
      option.classList.toggle('is-active', selected);
      option.setAttribute('aria-pressed', String(selected));
    });
    value.textContent = metric.value;
    title.textContent = metric.label;
    growth.textContent = metric.growth;
    renderBars(metric);
  });
});

renderBars(metricData.views);
