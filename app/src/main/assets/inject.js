/**
 * @file inject.js
 * @description Enforces read-only mode on Twitter/X via MutationObserver and click interception.
 *              Works alongside inject.css as a reinforcement layer for dynamically added elements.
 */
(function() {
  'use strict';

  // Avoid double-injection
  if (window.__xreadonly_injected) return;
  window.__xreadonly_injected = true;

  var TAG = '[XReadOnly]';

  // Selectors to hide completely (matching inject.css display:none rules)
  var HIDE_SELECTORS = [
    '[data-testid="like"]',
    '[data-testid="unlike"]',
    '[data-testid="retweet"]',
    '[data-testid="unretweet"]',
    '[data-testid="bookmark"]',
    '[data-testid="removeBookmark"]',
    '[data-testid="tweetTextarea_0"]',
    '[data-testid="toolBar"]',
    '[data-testid="tweetButtonInline"]',
    '[data-testid="tweetButton"]',
    '[data-testid="bottomBar"]',
    '[data-testid="share"]'
  ];

  // Selectors to disable (visible but non-interactive, shows reply count)
  var DISABLE_SELECTORS = [
    '[data-testid="reply"]'
  ];

  // All blocked selectors (used for click interception)
  var SELECTORS = HIDE_SELECTORS.concat(DISABLE_SELECTORS);

  var HIDE_SELECTOR_STRING = HIDE_SELECTORS.join(',');
  var DISABLE_SELECTOR_STRING = DISABLE_SELECTORS.join(',');

  /**
   * Hides interaction elements and disables reply buttons (keeping count visible).
   */
  function hideInteractionElements() {
    var hidden = document.querySelectorAll(HIDE_SELECTOR_STRING);
    for (var i = 0; i < hidden.length; i++) {
      hidden[i].style.display = 'none';
    }
    var disabled = document.querySelectorAll(DISABLE_SELECTOR_STRING);
    for (var i = 0; i < disabled.length; i++) {
      disabled[i].style.pointerEvents = 'none';
      disabled[i].style.opacity = '0.5';
    }
  }

  // --- MutationObserver: catch elements added during infinite scroll ---
  var observer = new MutationObserver(function(mutations) {
    var needsScan = false;
    for (var i = 0; i < mutations.length; i++) {
      if (mutations[i].addedNodes.length > 0) {
        needsScan = true;
        break;
      }
    }
    if (needsScan) {
      hideInteractionElements();
    }
  });

  function startObserver() {
    if (document.body) {
      // Initial pass
      hideInteractionElements();
      // Observe for new elements
      observer.observe(document.body, {
        childList: true,
        subtree: true
      });
    } else {
      // Body not ready yet, retry
      document.addEventListener('DOMContentLoaded', function() {
        hideInteractionElements();
        observer.observe(document.body, {
          childList: true,
          subtree: true
        });
      });
    }
  }

  // --- Click interception fallback ---
  document.addEventListener('click', function(e) {
    var target = e.target;
    // Walk up the DOM to check if click is on/inside a hidden element
    while (target && target !== document.body) {
      var testId = target.getAttribute('data-testid');
      if (testId) {
        for (var i = 0; i < SELECTORS.length; i++) {
          if (SELECTORS[i] === '[data-testid="' + testId + '"]') {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            return false;
          }
        }
      }
      target = target.parentElement;
    }
  }, true); // capture phase to intercept before Twitter's handlers

  startObserver();
})();
