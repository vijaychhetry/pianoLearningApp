(function (global) {
  var LOW = 36, HIGH = 96;
  var NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  var COLORS = { 60: "#e5484d", 62: "#f76b15", 64: "#e8b931", 65: "#2fa84f", 67: "#3a7dde" };

  // Taught-but-not-target keys wear a pale wash of their own colour. Mixing
  // against white here rather than using alpha keeps them readable whatever
  // sits behind the keyboard.
  function tint(hex, amount) {
    var r = parseInt(hex.slice(1, 3), 16);
    var g = parseInt(hex.slice(3, 5), 16);
    var b = parseInt(hex.slice(5, 7), 16);
    var mix = function (c) { return Math.round(c * amount + 255 * (1 - amount)); };
    return "rgb(" + mix(r) + "," + mix(g) + "," + mix(b) + ")";
  }

  function isWhite(m) { return [0, 2, 4, 5, 7, 9, 11].indexOf(((m % 12) + 12) % 12) >= 0; }
  function nameOf(m) { return NAMES[((m % 12) + 12) % 12] + (Math.floor(m / 12) - 1); }
  function audible(m) { return m >= 45 && m <= 89; }

  function mini(highlight) {
    var w = [], b = [], l = [], wi = 0, count = 0, m;
    for (m = LOW; m <= HIGH; m++) if (isWhite(m)) count++;
    for (m = LOW; m <= HIGH; m++) {
      if (isWhite(m)) {
        var style = "";
        if (m === highlight) style = ' style="background:' + (COLORS[m] || "#5b3cc4") + '"';
        else if (COLORS[m]) style = ' style="background:' + tint(COLORS[m], 0.3) + '"';
        w.push("<i" + (audible(m) ? "" : ' class="dim"') + style + "></i>");
        l.push("<span>" + (nameOf(m).charAt(0) === "C" && nameOf(m).length === 2 ? nameOf(m) : "") + "</span>");
        wi++;
      } else {
        b.push('<div class="mini-b" style="left:calc(' + ((wi / count) * 100).toFixed(3) + '% - 3px)"></div>');
      }
    }
    return '<div class="mini"><div class="mini-w">' + w.join("") + "</div>" + b.join("") +
      '<div class="mini-l">' + l.join("") + "</div></div>";
  }

  function zoom(highlight, flash) {
    var whites = [], m;
    for (m = LOW; m <= HIGH; m++) if (isWhite(m)) whites.push(m);
    var center = whites.indexOf(highlight);
    if (center < 0) center = whites.indexOf(60);
    var start = Math.max(0, center - 7);
    var end = Math.min(whites.length - 1, start + 14);
    start = Math.max(0, end - 14);
    var from = whites[start], to = whites[end];

    var w = [], b = [], wi = 0, count = 0;
    for (m = from; m <= to; m++) if (isWhite(m)) count++;
    for (m = from; m <= to; m++) {
      if (isWhite(m)) {
        var cls = [], style = "", inner = "";
        if (COLORS[m]) {
          cls.push("taught");
          style = m === highlight
            ? "background:" + COLORS[m]
            : "background:" + tint(COLORS[m], 0.32);
          inner = '<span class="kname" style="color:' +
            (m === highlight ? "#fff" : "#3c3450") + '">' + nameOf(m) + "</span>";
        }
        if (m === flash) cls.push("flash");
        if (m === highlight) {
          inner += '<span class="callout" style="background:' + (COLORS[m] || "#5b3cc4") + '">' +
            nameOf(m) + "</span>";
        }
        w.push("<i" + (cls.length ? ' class="' + cls.join(" ") + '"' : "") +
          (style ? ' style="' + style + '"' : "") + ">" + inner + "</i>");
        wi++;
      } else {
        b.push('<div class="zoom-b" style="left:calc(' + ((wi / count) * 100).toFixed(3) + '% - 7px)"></div>');
      }
    }
    return '<div class="zoom"><div class="zoom-w">' + w.join("") + "</div>" + b.join("") + "</div>";
  }

  global.KEY_COLORS = COLORS;
  global.noteName = nameOf;
  global.renderKids = function (host, highlight, flash) {
    host.className = "kb";
    host.innerHTML = mini(highlight) + zoom(highlight, flash);
  };
})(this);
