/*
 * New Quote's itemized part/amount table -- add/remove rows, live total,
 * part-name autocomplete merging the static seed with every part name a
 * quote has ever used (see /api/parts/suggestions). Serializes rows into
 * a hidden JSON field on submit; the server parses it (QuoteController)
 * into a List<QuotePartLine> rather than needing indexed form-field
 * binding for a variable-length list.
 *
 * Expects: #partsTableBody (tbody to hold rows), #addPartRow (button),
 * #quoteTotal (display span), #partsJson (hidden input), a <form> that
 * wraps all of this, and a <datalist id="parts-suggestions"> for row
 * autocomplete.
 */
(function () {
    var rowCount = 0;

    function currency(n) {
        return 'NGN ' + (isNaN(n) ? '0' : n.toFixed(2));
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

    function addRow() {
        rowCount++;
        var tbody = document.getElementById('partsTableBody');
        var row = document.createElement('tr');
        row.innerHTML =
            '<td><input type="text" class="part-name" list="parts-suggestions" placeholder="e.g. Compressor"></td>' +
            '<td><input type="text" inputmode="numeric" class="part-amount" placeholder="0"></td>' +
            '<td><button type="button" class="remove-row" aria-label="Remove">&times;</button></td>';
        tbody.appendChild(row);

        row.querySelector('.part-name').addEventListener('input', recompute);
        row.querySelector('.part-amount').addEventListener('input', recompute);
        row.querySelector('.remove-row').addEventListener('click', function () {
            row.remove();
            recompute();
        });
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
        var addBtn = document.getElementById('addPartRow');
        if (addBtn) addBtn.addEventListener('click', addRow);
        addRow(); // start with one empty row so the form isn't blank

        var form = document.getElementById('newQuoteForm');
        if (form) form.addEventListener('submit', recompute);
    });
})();
