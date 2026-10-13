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

update();
setInterval(update, 1000);