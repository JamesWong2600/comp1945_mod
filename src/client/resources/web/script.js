const ORDER = ['project:red', 'project:blue', 'project:yellow'];
const NAMES = {
    'project:red': 'Red',
    'project:blue': 'Blue',
    'project:yellow': 'Yellow'
};
const COLORS = {
    'project:red': 'red',
    'project:blue': 'blue',
    'project:yellow': 'yellow'
};

async function update() {
    try {
        const res = await fetch('/data');
        const data = await res.json();

        const bars = document.getElementById('bars');
        bars.innerHTML = '';

        for (const biome of ORDER) {
            const prob = data.probs[biome] || 0;
            const percent = (prob * 100).toFixed(1);

            const container = document.createElement('div');
            container.className = 'bar-container';

            const fill = document.createElement('div');
            fill.className = 'bar-fill ' + COLORS[biome];
            fill.style.width = percent + '%';

            const label = document.createElement('div');
            label.className = 'bar-label';
            label.textContent = NAMES[biome];

            const pct = document.createElement('div');
            pct.className = 'bar-percent';
            pct.textContent = percent + '%';

            container.appendChild(fill);
            container.appendChild(label);
            container.appendChild(pct);
            bars.appendChild(container);
        }

        document.getElementById('coord').textContent = data.coord;
        document.getElementById('block').textContent = data.block;
        document.getElementById('tick').textContent = data.tick;
        document.getElementById('biome').textContent = data.biome;
    } catch (e) {
        console.error(e);
    }
}

const ctx = document.getElementById('chart').getContext('2d');
const chart = new Chart(ctx, {
    type: 'line',
    data: {
        labels: [],
        datasets: [
            { label: 'Red', borderColor: 'red', data: [], fill: false },
            { label: 'Blue', borderColor: 'blue', data: [], fill: false },
            { label: 'Yellow', borderColor: 'gold', data: [], fill: false }
        ]
    },
    options: {
        animation: false,
        scales: { y: { min: 0, max: 1 } }
    }
});

async function updateChart() {
    const res = await fetch('/history');
    const history = await res.json();
    chart.data.labels = history.map(h => h.tick);
    chart.data.datasets[0].data = history.map(h => h.red);
    chart.data.datasets[1].data = history.map(h => h.blue);
    chart.data.datasets[2].data = history.map(h => h.yellow);
    chart.update();
}


setInterval(updateChart, 1000);

update();
setInterval(update, 1000);