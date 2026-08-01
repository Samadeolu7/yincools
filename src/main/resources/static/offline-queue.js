/*
 * New Job's offline capture: intercepts the form submit, tries the
 * CSRF-exempt JSON endpoint with a short timeout, and if that fails
 * (no signal, or too slow) queues the job in localStorage instead of
 * losing it. A client-generated UUID (clientId) makes retries safe -- if
 * the first POST actually succeeded but the response never arrived, the
 * server recognizes the clientId and returns the existing job instead of
 * creating a duplicate (see JobService.createJobIdempotent).
 *
 * The receipt shown for a queued job is built right here from the form
 * data, not from a server round-trip, so sharing on WhatsApp still works
 * immediately with no signal. Aggregate screens (This Week, Who Owes Me)
 * are NOT made to work offline -- only capturing the job is worth the
 * complexity; those screens just fail to load with no signal, which is
 * an honest failure mode.
 */
(function () {
    var QUEUE_KEY = 'yincools_pending_jobs';
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

    function queueJob(payload) {
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
            badge.textContent = count + (count === 1 ? ' unsynced' : ' unsynced');
            badge.style.display = 'block';
        } else {
            badge.style.display = 'none';
        }
    }

    function postJob(payload) {
        var controller = ('AbortController' in window) ? new AbortController() : null;
        var timer = controller ? setTimeout(function () { controller.abort(); }, FETCH_TIMEOUT_MS) : null;
        return fetch('/api/jobs', {
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
            postJob(payload)
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

    function buildReceiptText(payload) {
        var lines = [];
        lines.push((window.YINCOOLS_BUSINESS_NAME || 'AC Tech') + ' Job Receipt');
        lines.push(new Date().toISOString().slice(0, 10));
        if (payload.customerName) lines.push('Customer: ' + payload.customerName);
        if (payload.vehicleDescription) lines.push('Vehicle: ' + payload.vehicleDescription);
        lines.push('Work: ' + payload.workType);
        var charge = parseFloat(payload.charge || '0') || 0;
        var paid = parseFloat(payload.paid || '0') || 0;
        lines.push('Charge: NGN ' + charge.toFixed(2));
        lines.push('Paid: NGN ' + paid.toFixed(2));
        lines.push('Balance: NGN ' + (charge - paid).toFixed(2));
        lines.push('(saved offline -- will sync automatically)');
        return lines.join('\n');
    }

    function showOfflineBanner(payload) {
        var banner = document.getElementById('offlineSavedBanner');
        if (!banner) return;
        var text = buildReceiptText(payload);
        var escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;');
        var html = '<div class="label">SAVED OFFLINE</div><pre>' + escaped + '</pre>';
        if (payload.customerPhone) {
            var waLink = 'https://wa.me/' + digits(payload.customerPhone) + '?text=' + encodeURIComponent(text);
            html += '<a href="' + waLink + '" target="_blank" class="wa-link">Share on WhatsApp</a>';
        }
        banner.innerHTML = html;
        banner.style.display = 'block';
    }

    function buildPayload(clientId) {
        var field = function (id) {
            var el = document.getElementById(id);
            return el ? el.value : '';
        };
        return {
            clientId: clientId,
            customerName: field('customerName'),
            customerPhone: field('customerPhone'),
            vehicleId: field('vehicleId') ? Number(field('vehicleId')) : null,
            vehicleDescription: field('vehicleDescription'),
            vehiclePlateNumber: field('vehiclePlateNumber'),
            sharedPartsCost: !!(document.getElementById('sharedPartsCost') && document.getElementById('sharedPartsCost').checked),
            workType: field('workType'),
            charge: field('charge'),
            partsCost: field('partsCost'),
            partsNote: field('partsNote'),
            paid: field('paid')
        };
    }

    function generateClientId() {
        if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
        return 'cid-' + Date.now() + '-' + Math.random().toString(16).slice(2);
    }

    function initFormInterception() {
        var form = document.getElementById('newJobForm');
        if (!form) return;

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var payload = buildPayload(generateClientId());

            postJob(payload)
                .then(function (job) {
                    window.location.href = '/jobs/' + job.id + '/receipt';
                })
                .catch(function () {
                    queueJob(payload);
                    showOfflineBanner(payload);
                    form.reset();
                    var vehicleIdField = document.getElementById('vehicleId');
                    if (vehicleIdField) vehicleIdField.value = '';
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
