/*
 * New Quote's offline capture -- same shape as offline-queue.js for jobs:
 * intercepts the form submit, tries the CSRF-exempt JSON endpoint with a
 * short timeout, and if that fails (no signal, or too slow) queues the
 * quote in localStorage instead of losing it. A client-generated UUID
 * (clientId) makes retries safe -- if the first POST actually succeeded
 * but the response never arrived, the server recognizes the clientId and
 * returns the existing quote instead of creating a duplicate (see
 * QuoteService.createQuoteIdempotent).
 *
 * The letterhead-styled preview with the one-tap image share
 * (quote-share.js) needs the real server-rendered page, so it isn't
 * available the moment a quote is queued offline -- what's shown instead
 * is a plain-text summary built right here from the form data, with a
 * WhatsApp link and a mailto link, same as the offline job receipt. Both
 * of those work fine with no signal: they just hand off to WhatsApp/the
 * mail app, which queue the actual send themselves. Once the quote syncs,
 * the full styled/shareable version is one tap away on the quote list.
 */
(function () {
    var QUEUE_KEY = 'yincools_pending_quotes';
    var FETCH_TIMEOUT_MS = 6000;

    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(function () { /* not fatal */ });
    }

    function getQueue() {
        try {
            return JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]');
        } catch (e) {
            return [];
        }
    }

    function saveQueue(queue) {
        localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
    }

    function queueQuote(payload) {
        var queue = getQueue();
        queue.push(payload);
        saveQueue(queue);
        updateUnsyncedBadge();
    }

    function removeFromQueue(clientId) {
        saveQueue(getQueue().filter(function (p) { return p.clientId !== clientId; }));
        updateUnsyncedBadge();
    }

    function updateUnsyncedBadge() {
        var badge = document.getElementById('unsyncedBadge');
        if (!badge) return;
        var count = getQueue().length;
        if (count > 0) {
            badge.textContent = count + (count === 1 ? ' unsynced quote' : ' unsynced quotes');
            badge.style.display = 'block';
        } else {
            badge.style.display = 'none';
        }
    }

    function postQuote(payload) {
        var controller = ('AbortController' in window) ? new AbortController() : null;
        var timer = controller ? setTimeout(function () { controller.abort(); }, FETCH_TIMEOUT_MS) : null;
        return fetch('/api/quotes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: controller ? controller.signal : undefined
        }).then(function (response) {
            if (timer) clearTimeout(timer);
            if (!response.ok) throw new Error('request failed');
            return response.json();
        }).catch(function (err) {
            if (timer) clearTimeout(timer);
            throw err;
        });
    }

    function flushQueue() {
        getQueue().forEach(function (payload) {
            postQuote(payload)
                .then(function () { removeFromQueue(payload.clientId); })
                .catch(function () { /* still no signal -- stays queued for next try */ });
        });
    }

    function digits(phone) {
        if (!phone) return '';
        var d = phone.replace(/[^0-9]/g, '');
        if (d.charAt(0) === '0') d = '234' + d.substring(1);
        return d;
    }

    function buildQuoteText(payload) {
        var lines = [];
        lines.push((window.YINCOOLS_BUSINESS_NAME || 'Yincools') + ' Quote');
        lines.push(new Date().toISOString().slice(0, 10));
        if (payload.customerName) lines.push('Customer: ' + payload.customerName);
        if (payload.vehicleDescription) lines.push('Vehicle: ' + payload.vehicleDescription);
        lines.push('Work: ' + payload.workType);
        lines.push('');
        var total = 0;
        (payload.items || []).forEach(function (item) {
            var amount = parseFloat(item.amount) || 0;
            total += amount;
            lines.push(item.partName + ': NGN ' + amount.toFixed(2));
        });
        lines.push('');
        lines.push('Total: NGN ' + total.toFixed(2));
        lines.push('(saved offline -- will sync automatically)');
        return lines.join('\n');
    }

    function showOfflineBanner(payload) {
        var banner = document.getElementById('offlineSavedBanner');
        if (!banner) return;
        var text = buildQuoteText(payload);
        var escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;');
        var html = '<div class="label">SAVED OFFLINE</div><pre>' + escaped + '</pre>';
        if (payload.customerPhone) {
            var waLink = 'https://wa.me/' + digits(payload.customerPhone) + '?text=' + encodeURIComponent(text);
            html += '<a href="' + waLink + '" target="_blank" class="share-link">Share on WhatsApp</a>';
        }
        var mailtoLink = 'mailto:?subject=' + encodeURIComponent((window.YINCOOLS_BUSINESS_NAME || 'Yincools') + ' Quote')
            .replace(/\+/g, '%20') + '&body=' + encodeURIComponent(text).replace(/\+/g, '%20');
        html += '<a href="' + mailtoLink + '" class="share-link">Share by Email</a>';
        banner.innerHTML = html;
        banner.style.display = 'block';
    }

    function buildPayload(clientId) {
        var field = function (id) {
            var el = document.getElementById(id);
            return el ? el.value : '';
        };
        var items = [];
        try {
            items = JSON.parse(field('partsJson') || '[]');
        } catch (e) {
            items = [];
        }
        return {
            clientId: clientId,
            customerName: field('customerName'),
            customerPhone: field('customerPhone'),
            vehicleId: field('vehicleId') ? Number(field('vehicleId')) : null,
            vehicleDescription: field('vehicleDescription'),
            vehiclePlateNumber: field('vehiclePlateNumber'),
            workType: field('workType'),
            items: items
        };
    }

    function generateClientId() {
        if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
        return 'cid-' + Date.now() + '-' + Math.random().toString(16).slice(2);
    }

    function initFormInterception() {
        var form = document.getElementById('newQuoteForm');
        if (!form) return;

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var payload = buildPayload(generateClientId());

            postQuote(payload)
                .then(function (quote) {
                    window.location.href = '/quotes/' + quote.id;
                })
                .catch(function () {
                    queueQuote(payload);
                    showOfflineBanner(payload);
                    form.reset();
                    var vehicleIdField = document.getElementById('vehicleId');
                    if (vehicleIdField) vehicleIdField.value = '';
                    var partsJsonField = document.getElementById('partsJson');
                    if (partsJsonField) partsJsonField.value = '';
                });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initFormInterception();
        updateUnsyncedBadge();
        flushQueue();
    });

    window.addEventListener('online', flushQueue);
})();
