(function (global) {
  var LOW = 36, HIGH = 96;
  function isWhite(midi) {
    return [0, 2, 4, 5, 7, 9, 11].indexOf(((midi % 12) + 12) % 12) >= 0;
  }
  function nameOf(midi) {
    var n = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
    return n[((midi % 12) + 12) % 12] + (Math.floor(midi / 12) - 1);
  }
  function letterOf(midi) {
    return nameOf(midi).replace(/\d+$/, "");
  }
  global.renderKeyboard = function (host, highlight) {
    var whites = [];
    var blacks = [];
    var labels = [];
    var wi = 0;
    var whiteCount = 0;
    for (var n = LOW; n <= HIGH; n++) if (isWhite(n)) whiteCount++;
    for (var m = LOW; m <= HIGH; m++) {
      if (isWhite(m)) {
        var cls = "white";
        if (m === highlight) cls += " target";
        else if (letterOf(m) === letterOf(highlight)) cls += " same";
        var label = m === highlight ? "<span>" + nameOf(m) + "</span>" : "";
        whites.push('<div class="' + cls + '" data-midi="' + m + '">' + label + "</div>");
        labels.push("<span>" + (letterOf(m) === "C" ? nameOf(m) : "") + "</span>");
        wi++;
      } else {
        blacks.push(
          '<div class="black" style="left:calc(' +
            ((wi / whiteCount) * 100).toFixed(3) +
            "% - 5px)" +
            '"></div>'
        );
      }
    }
    host.innerHTML =
      '<div class="kb"><div class="whites">' +
      whites.join("") +
      "</div>" +
      blacks.join("") +
      '<div class="octaves">' +
      labels.join("") +
      "</div></div>";
  };
})(this);
