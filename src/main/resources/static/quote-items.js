/*
 * New Quote's itemized part/amount table -- no "add row" button. Starts
 * with two empty rows; as soon as the last row on screen gets any content,
 * a fresh empty row appears after it, so the table always has exactly one
 * spare row ready and grows for as long as Dad keeps typing. Remove (x)
 * stays available per row. Empty rows are never submitted -- recompute()
 * filters them out when building the JSON payload, so there's no need to
 * enforce a minimum row count in the UI.
 *
 * Live total + part-name autocomplete merging the static seed with every
 * part name a quote has ever used (see /api/parts/suggestions). Serializes
 * rows into a hidden JSON field kept live on every keystroke and again on
 * submit; the server parses it (QuoteController) into a
 * List<QuotePartLine> rather than needing indexed form-field binding for a
 * variable-length list.
 *
 * Expects: #partsTableBody (tbody to hold rows), #quoteTotal (display
 * span), #partsJson (hidden input), a <form id="newQuoteForm"> wrapping
 * all of this, and a <datalist id="parts-suggestions"> for row
 * autocomplete.
 *
 * Shared by both New Quote and Edit Quote. Edit Quote additionally
 * provides #quoteEditData with a data-items attribute (a JSON array of
 * {partName, amount}, HTML-escaped by Thymeleaf's th:attr so it round-
 * trips safely through getAttribute() even if a part name contains
 * quotes or angle brackets) -- when present, those become prefilled rows
 * instead of the two-empty-row starting state.
 */
(function () {
    function currency(n) {
        return 'NGN ' + (isNaN(n) ? '0' : n.toFixed(2));
    }

    function isRowEmpty(row) {
        return !row.querySelector('.part-name').value.trim() && !row.querySelector('.part-amount').value.trim();
    }

    function recompute() {
        var total = 0;
        document.querySelectorAll('#partsTableBody .part-amount').forEach(function (input) {
            var v = parseFloat(input.value);
            if (!isNaN(v)) total += v;
        });
        var totalEl = document.getElementById('quoteTotal');
        if (totalEl) totalEl.textContent = currency(total);

        var items = [];
        document.querySelectorAll('#partsTableBody tr').forEach(function (row) {
            var name = row.querySelector('.part-name').value.trim();
            var amount = row.querySelector('.part-amount').value;
            if (name && amount) {
                items.push({ partName: name, amount: amount });
            }
        });
        var jsonField = document.getElementById('partsJson');
        if (jsonField) jsonField.value = JSON.stringify(items);
    }

    /** If the row that just changed is the last one and now has content, grow one more empty row after it. */
    function maybeGrow(row) {
        var tbody = document.getElementById('partsTableBody');
        var rows = tbody.querySelectorAll('tr');
        var lastRow = rows[rows.length - 1];
        if (row === lastRow && !isRowEmpty(row)) {
            addRow();
        }
    }

    function escapeAttr(s) {
        return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function addRow(partName, amount) {
        var tbody = document.getElementById('partsTableBody');
        var row = document.createElement('tr');
        row.innerHTML =
            '<td><input type="text" class="part-name" list="parts-suggestions" placeholder="e.g. Compressor" value="' +
                (partName ? escapeAttr(partName) : '') + '"></td>' +
            '<td><input type="text" inputmode="numeric" class="part-amount" placeholder="0" value="' +
                (amount != null ? escapeAttr(amount) : '') + '"></td>' +
            '<td class="remove-cell"><button type="button" class="remove-row" aria-label="Remove">&times;</button></td>';
        tbody.appendChild(row);

        function onInput() {
            recompute();
            maybeGrow(row);
        }
        row.querySelector('.part-name').addEventListener('input', onInput);
        row.querySelector('.part-amount').addEventListener('input', onInput);
        row.querySelector('.remove-row').addEventListener('click', function () {
            row.remove();
            recompute();
        });
    }

    function loadExistingItems() {
        var dataEl = document.getElementById('quoteEditData');
        if (!dataEl) return [];
        try {
            return JSON.parse(dataEl.getAttribute('data-items') || '[]');
        } catch (e) {
            return [];
        }
    }

    function loadPartSuggestions() {
        var datalist = document.getElementById('parts-suggestions');
        if (!datalist) return;
        Promise.all([
            fetch('/parts-seed.json').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
            fetch('/api/parts/suggestions').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; })
        ]).then(function (results) {
            var merged = Array.from(new Set(results[0].concat(results[1])));
            merged.forEach(function (name) {
                var option = document.createElement('option');
                option.value = name;
                datalist.appendChild(option);
            });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        loadPartSuggestions();

        var existing = loadExistingItems();
        if (existing.length) {
            existing.forEach(function (item) { addRow(item.partName, item.amount); });
            addRow(); // one spare empty row after the prefilled ones
        } else {
            addRow(); // start with two empty rows
            addRow();
        }
        recompute(); // reflect prefilled rows in the total immediately, not just after the next keystroke

        var form = document.getElementById('newQuoteForm');
        if (form) form.addEventListener('submit', recompute);
    });
})();
