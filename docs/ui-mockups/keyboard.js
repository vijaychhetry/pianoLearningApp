(function (global) {
  var LOW = 36, HIGH = 96;
  var NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  function isWhite(midi) {
    return [0, 2, 4, 5, 7, 9, 11].indexOf(((midi % 12) + 12) % 12) >= 0;
  }
  function nameOf(midi) {
    return NAMES[((midi % 12) + 12) % 12] + (Math.floor(midi / 12) - 1);
  }
  function letterOf(midi) {
    return nameOf(midi).replace(/\d+$/, "");
  }
  function audible(midi) {
    return midi >= 45 && midi <= 89;
  }

  function miniMap(highlight) {
    var whites = [];
    var blacks = [];
    var labels = [];
    var wi = 0;
    var whiteCount = 0;
    var m;
    for (m = LOW; m <= HIGH; m++) if (isWhite(m)) whiteCount++;
    for (m = LOW; m <= HIGH; m++) {
      if (isWhite(m)) {
        var cls = "mini-white";
        if (m === highlight) cls += " target";
        else if (letterOf(m) === letterOf(highlight)) cls += " same";
        if (!audible(m)) cls += " faded";
        whites.push('<div class="' + cls + '"></div>');
        labels.push("<span>" + (letterOf(m) === "C" ? nameOf(m) : "") + "</span>");
        wi++;
      } else {
        blacks.push(
          '<div class="mini-black" style="left:calc(' +
            ((wi / whiteCount) * 100).toFixed(3) +
            "% - 3px)\"></div>"
        );
      }
    }
    return (
      '<div class="mini"><div class="mini-whites">' +
      whites.join("") +
      "</div>" +
      blacks.join("") +
      '<div class="mini-labels">' +
      labels.join("") +
      "</div></div>"
    );
  }

  function zoomStrip(highlight) {
    var from = 48, to = 72; // C3–C5 around C4; clamp if highlight moves
    var whitesAll = [];
    var n;
    for (n = LOW; n <= HIGH; n++) if (isWhite(n)) whitesAll.push(n);
    var center = whitesAll.indexOf(highlight);
    if (center < 0) center = whitesAll.indexOf(60);
    var start = Math.max(0, center - 7);
    var end = Math.min(whitesAll.length - 1, start + 14);
    start = Math.max(0, end - 14);
    from = whitesAll[start];
    to = whitesAll[end];

    var whites = [];
    var blacks = [];
    var wi = 0;
    var whiteCount = 0;
    var m;
    for (m = from; m <= to; m++) if (isWhite(m)) whiteCount++;
    for (m = from; m <= to; m++) {
      if (isWhite(m)) {
        var cls = "zoom-white";
        if (m === highlight) cls += " target";
        else if (letterOf(m) === letterOf(highlight)) cls += " same";
        var cap =
          m === highlight
            ? '<div class="callout">' + nameOf(m) + "</div>"
            : letterOf(m) === "C"
              ? '<div class="clabel">' + nameOf(m) + "</div>"
              : "";
        whites.push('<div class="' + cls + '">' + cap + "</div>");
        wi++;
      } else {
        blacks.push(
          '<div class="zoom-black" style="left:calc(' +
            ((wi / whiteCount) * 100).toFixed(3) +
            "% - 7px)\"></div>"
        );
      }
    }
    return (
      '<div class="zoom"><div class="zoom-whites">' +
      whites.join("") +
      "</div>" +
      blacks.join("") +
      "</div>"
    );
  }

  global.renderKeyboard = function (host, highlight) {
    host.innerHTML =
      '<div class="kb-stack">' + miniMap(highlight) + zoomStrip(highlight) + "</div>";
  };
})(this);
