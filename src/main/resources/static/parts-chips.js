/*
 * New Job's parts cost: tappable chips, seed list merged with every
 * distinct part name a quote has ever used (see /api/parts/suggestions --
 * the "database of parts" that grows from usage, same pattern as
 * vehicle-picker.js's vehicle suggestions). Plus an "Other" chip for free
 * text. Builds a comma-joined value into a hidden input. Expects element
 * ids: partsChips (container), partsNote (hidden input), partsOther
 * (free-text input).
 */

(function () {
    var selected = new Set();

    function updatePartsNote() {
        var parts = Array.from(selected);
        var other = document.getElementById('partsOther').value.trim();
        if (other) parts.push(other);
        document.getElementById('partsNote').value = parts.join(', ');
    }

    function toggleChip(chip, part) {
        if (selected.has(part)) {
            selected.delete(part);
            chip.classList.remove('selected');
        } else {
            selected.add(part);
            chip.classList.add('selected');
        }
        updatePartsNote();
    }

    function toggleOther(chip) {
        var otherInput = document.getElementById('partsOther');
        var showing = otherInput.style.display !== 'none';
        otherInput.style.display = showing ? 'none' : 'block';
        chip.classList.toggle('selected', !showing);
        if (showing) {
            otherInput.value = '';
        } else {
            otherInput.focus();
        }
        updatePartsNote();
    }

    function loadPartsChips() {
        var container = document.getElementById('partsChips');
        if (!container) return;

        Promise.all([
            fetch('/parts-seed.json').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
            fetch('/api/parts/suggestions').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; })
        ]).then(function (results) {
            var parts = Array.from(new Set(results[0].concat(results[1])));

            parts.forEach(function (part) {
                var chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'part-chip';
                chip.textContent = part;
                chip.onclick = function () { toggleChip(chip, part); };
                container.appendChild(chip);
            });

            var otherChip = document.createElement('button');
            otherChip.type = 'button';
            otherChip.className = 'part-chip';
            otherChip.textContent = 'Other';
            otherChip.onclick = function () { toggleOther(otherChip); };
            container.appendChild(otherChip);
        });

        document.getElementById('partsOther').addEventListener('input', updatePartsNote);
    }

    document.addEventListener('DOMContentLoaded', loadPartsChips);
})();
