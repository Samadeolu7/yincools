/*
 * Shares the quote as an actual image of the styled letterhead preview,
 * not just a plain-text summary -- so whatever Dad sends over WhatsApp (or
 * anything else in the OS share sheet) looks like the real letterhead,
 * the same as what's on screen. Uses the OS's native share sheet
 * (navigator.share with a file), which also sidesteps the old limitation
 * where a WhatsApp link only worked if a customer phone had been entered --
 * the native sheet lets Dad pick the contact/app himself.
 *
 * The letterhead header/footer (logo, wordmark, tagline, address/contact/
 * bankers) are byte-for-byte identical on every quote -- only the card in
 * the middle (#quoteCard) actually changes. Re-rasterizing the logo image
 * and footer text on every single share is wasted work, so the header/
 * footer are rendered once with html2canvas and cached in localStorage as
 * plain PNGs; every share after that just draws those cached images and
 * only asks html2canvas to do fresh work on the small #quoteCard. The
 * cache key is a hash of the header+footer HTML *and* the CSS files'
 * actual text -- not a manually bumped version number, so it
 * self-invalidates whether the letterhead's markup changes OR just its
 * styling does (a pure CSS tweak leaves the HTML byte-identical, so
 * hashing markup alone would silently keep serving a stale pre-change
 * render forever; this bit us once already -- a "AUTO NIG." line-wrap
 * fix that was CSS-only would never have invalidated a phone's existing
 * cache under the old hash).
 *
 * Progressive enhancement only: the server always renders working
 * WhatsApp/email text-share links (.fallback-share) first. This script
 * only reveals the "Share Quote" button and hides those once it's
 * confirmed the browser can actually share files -- so a browser that
 * can't do any of this (most desktops) sees exactly what it saw before.
 *
 * Expects: #letterheadChromeTop / #quoteCard / #letterheadChromeBottom
 * (the three stacked regions to compose), #shareQuoteBtn (data-share-title
 * / data-share-text for the caption), html2canvas already loaded, and
 * .fallback-share on the links to hide.
 */
(function () {
    var CACHE_KEY = 'yincools-letterhead-chrome-cache';

    function supportsFileShare() {
        if (!navigator.canShare || !navigator.share) return false;
        try {
            var probe = new File(['probe'], 'probe.png', { type: 'image/png' });
            return navigator.canShare({ files: [probe] });
        } catch (e) {
            return false;
        }
    }

    function hashString(str) {
        var hash = 5381;
        for (var i = 0; i < str.length; i++) {
            hash = ((hash << 5) + hash) + str.charCodeAt(i);
            hash |= 0;
        }
        return hash.toString(36);
    }

    function loadImage(src) {
        return new Promise(function (resolve, reject) {
            var img = new Image();
            img.onload = function () { resolve(img); };
            img.onerror = reject;
            img.src = src;
        });
    }

    function renderToDataUrl(el) {
        return html2canvas(el, { backgroundColor: '#ffffff', scale: 2 })
            .then(function (canvas) { return canvas.toDataURL('image/png'); });
    }

    /** Cache key covers both the markup AND the CSS that styles it -- see the file header comment for why. */
    function computeCacheKey(topEl, bottomEl) {
        return Promise.all([
            fetch('/css/tokens.css').then(function (r) { return r.text(); }).catch(function () { return ''; }),
            fetch('/css/components.css').then(function (r) { return r.text(); }).catch(function () { return ''; })
        ]).then(function (css) {
            return hashString(css[0] + '|' + css[1] + '|' + topEl.outerHTML + '|' + bottomEl.outerHTML);
        });
    }

    /** Cached header/footer PNGs, re-rendered only when their markup or CSS changes. */
    function getChromeImages(topEl, bottomEl) {
        return computeCacheKey(topEl, bottomEl).then(function (key) {
            var cached = null;
            try {
                var raw = localStorage.getItem(CACHE_KEY);
                cached = raw ? JSON.parse(raw) : null;
            } catch (e) {
                cached = null;
            }

            if (cached && cached.key === key) {
                return Promise.all([loadImage(cached.top), loadImage(cached.bottom)]);
            }

            return Promise.all([renderToDataUrl(topEl), renderToDataUrl(bottomEl)])
                .then(function (urls) {
                    try {
                        localStorage.setItem(CACHE_KEY, JSON.stringify({ key: key, top: urls[0], bottom: urls[1] }));
                    } catch (e) {
                        // Storage full/unavailable (e.g. private browsing) -- fine,
                        // it just re-renders the chrome next time too.
                    }
                    return Promise.all([loadImage(urls[0]), loadImage(urls[1])]);
                });
        });
    }

    function composite(topImg, cardCanvas, bottomImg) {
        var width = Math.max(topImg.width, cardCanvas.width, bottomImg.width);
        var height = topImg.height + cardCanvas.height + bottomImg.height;
        var canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        var ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);
        ctx.drawImage(topImg, 0, 0);
        ctx.drawImage(cardCanvas, 0, topImg.height);
        ctx.drawImage(bottomImg, 0, topImg.height + cardCanvas.height);
        return canvas;
    }

    function captureQuote() {
        var topEl = document.getElementById('letterheadChromeTop');
        var cardEl = document.getElementById('quoteCard');
        var bottomEl = document.getElementById('letterheadChromeBottom');
        var wholeEl = document.getElementById('shareableQuote');

        return getChromeImages(topEl, bottomEl)
            .then(function (images) {
                return html2canvas(cardEl, { backgroundColor: '#ffffff', scale: 2 })
                    .then(function (cardCanvas) { return composite(images[0], cardCanvas, images[1]); });
            })
            .catch(function () {
                // Cached-chrome path failed for any reason -- fall back to
                // capturing the whole thing fresh, same as before this
                // optimization existed. Slower, but never worse than
                // sharing nothing.
                return html2canvas(wholeEl, { backgroundColor: '#ffffff', scale: 2 });
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var shareBtn = document.getElementById('shareQuoteBtn');
        if (!shareBtn || typeof html2canvas === 'undefined' || !supportsFileShare()) {
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

            captureQuote()
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
