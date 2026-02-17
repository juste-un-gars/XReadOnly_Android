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

  // Selectors matching inject.css (caret kept visible per user choice)
  var SELECTORS = [
    '[data-testid="like"]',
    '[data-testid="unlike"]',
    '[data-testid="retweet"]',
    '[data-testid="unretweet"]',
    '[data-testid="reply"]',
    '[data-testid="bookmark"]',
    '[data-testid="removeBookmark"]',
    '[data-testid="tweetTextarea_0"]',
    '[data-testid="toolBar"]',
    '[data-testid="tweetButtonInline"]',
    '[data-testid="tweetButton"]',
    '[data-testid="bottomBar"]',
    '[data-testid="share"]'
  ];

  var SELECTOR_STRING = SELECTORS.join(',');

  /**
   * Hides all matching elements currently in the DOM.
   */
  function hideInteractionElements() {
    var elements = document.querySelectorAll(SELECTOR_STRING);
    for (var i = 0; i < elements.length; i++) {
      elements[i].style.display = 'none';
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
