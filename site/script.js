// RssRadar landing — minimal progressive enhancement.
// Smooth in-page anchor offset for the sticky topbar; nothing else required.

(function () {
  var TOPBAR = 64; // approx sticky header height
  document.querySelectorAll('a[href^="#"]').forEach(function (a) {
    a.addEventListener('click', function (e) {
      var id = a.getAttribute('href');
      if (id.length < 2) return;
      var t = document.querySelector(id);
      if (!t) return;
      e.preventDefault();
      var y = t.getBoundingClientRect().top + window.pageYOffset - TOPBAR;
      window.scrollTo({ top: y, behavior: 'smooth' });
      history.replaceState(null, '', id);
    });
  });
})();
