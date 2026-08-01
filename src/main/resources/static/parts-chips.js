/*
 * Shared by New Job's parts cost and New Quote's parts note -- tappable
 * chips from a static seed list (deliberately not merged with anything
 * dynamic, unlike vehicles: parts are usually multiple-at-once, so learning
 * new ones reliably from free text would need real structure that isn't
 * earning its keep yet). Plus an "Other" chip for free text. Builds a
 * comma-joined value into a hidden input. Expects element ids: partsChips
 * (container), partsNote (hidden input), partsOther (free-text input).
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

        fetch('/parts-seed.json')
            .then(function (r) { return r.ok ? r.json() : []; })
            .catch(function () { return []; })
            .then(function (parts) {
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
