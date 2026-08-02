/*
 * Shares the quote as an actual image of the styled letterhead preview,
 * not just a plain-text summary -- so whatever Dad sends over WhatsApp (or
 * anything else in the OS share sheet) looks like the real letterhead,
 * the same as what's on screen. Uses the OS's native share sheet
 * (navigator.share with a file), which also sidesteps the old limitation
 * where a WhatsApp link only worked if a customer phone had been entered --
 * the native sheet lets Dad pick the contact/app himself.
 *
 * Progressive enhancement only: the server always renders working
 * WhatsApp/email text-share links (.fallback-share) first. This script
 * only reveals the "Share Quote" button and hides those once it's
 * confirmed the browser can actually share files -- so a browser that
 * can't do any of this (most desktops) sees exactly what it saw before.
 *
 * Expects: #shareableQuote (the element to render), #shareQuoteBtn (the
 * button, data-share-title / data-share-text attributes for the caption),
 * html2canvas already loaded, and .fallback-share on the links to hide.
 */
(function () {
    function supportsFileShare() {
        if (!navigator.canShare || !navigator.share) return false;
        try {
            var probe = new File(['probe'], 'probe.png', { type: 'image/png' });
            return navigator.canShare({ files: [probe] });
        } catch (e) {
            return false;
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        var shareBtn = document.getElementById('shareQuoteBtn');
        var target = document.getElementById('shareableQuote');
        if (!shareBtn || !target || typeof html2canvas === 'undefined' || !supportsFileShare()) {
            return;
        }

        shareBtn.style.display = '';
        document.querySelectorAll('.fallback-share').forEach(function (el) {
            el.style.display = 'none';
        });

        var defaultLabel = shareBtn.textContent;

        shareBtn.addEventListener('click', function () {
            shareBtn.disabled = true;
            shareBtn.textContent = 'Preparing...';

            html2canvas(target, { backgroundColor: '#ffffff', scale: 2 })
                .then(function (canvas) {
                    return new Promise(function (resolve) {
                        canvas.toBlob(resolve, 'image/png');
                    });
                })
                .then(function (blob) {
                    var file = new File([blob], 'quote.png', { type: 'image/png' });
                    return navigator.share({
                        files: [file],
                        title: shareBtn.getAttribute('data-share-title') || document.title,
                        text: shareBtn.getAttribute('data-share-text') || ''
                    });
                })
                .catch(function () {
                    // User cancelled the share sheet, or sharing failed --
                    // either way there's nothing to recover, the button
                    // just resets below.
                })
                .then(function () {
                    shareBtn.disabled = false;
                    shareBtn.textContent = defaultLabel;
                });
        });
    });
})();
