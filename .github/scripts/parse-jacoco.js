// Shared JaCoCo XML parser used by CI workflows.
// Extracts overall and per-class coverage counters from a JaCoCo XML report.

const fs = require('fs');

const zeroCov = { covered: 0, missed: 0 };

function parseJacocoXml(jacocoFile) {
  const result = { overall: {}, classes: {} };

  if (!fs.existsSync(jacocoFile)) {
    return null;
  }

  const xml = fs.readFileSync(jacocoFile, 'utf8');

  // Overall counters (outside <package> tags)
  const stripped = xml.replace(/<package[\s\S]*?<\/package>/g, '');
  const re = /<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\/>/g;
  let m;
  while ((m = re.exec(stripped)) !== null) {
    const entry = { covered: parseInt(m[3]), missed: parseInt(m[2]) };
    if (m[1] === 'LINE') result.overall.line = entry;
    else if (m[1] === 'BRANCH') result.overall.branch = entry;
    else if (m[1] === 'METHOD') result.overall.method = entry;
  }

  // Per-class counters from <package>/<class> elements.
  // The negative lookbehind (?<!\/) prevents matching self-closing <class .../> tags
  // (interfaces, annotations) which have no body and would otherwise steal the next
  // class's counters.
  const pkgRe = /<package\s+name="([^"]+)">([\s\S]*?)<\/package>/g;
  let pkgMatch;
  while ((pkgMatch = pkgRe.exec(xml)) !== null) {
    const pkgBody = pkgMatch[2];
    const classRe = /<class\s+name="([^"]+)"[^>]*(?<!\/)>([\s\S]*?)<\/class>/g;
    let classMatch;
    while ((classMatch = classRe.exec(pkgBody)) !== null) {
      const className = classMatch[1].replace(/\//g, '.');
      const classBody = classMatch[2];
      const counters = { line: { ...zeroCov }, branch: { ...zeroCov }, method: { ...zeroCov } };
      const cntRe = /<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\/>/g;
      let cntMatch;
      while ((cntMatch = cntRe.exec(classBody)) !== null) {
        const entry = { covered: parseInt(cntMatch[3]), missed: parseInt(cntMatch[2]) };
        if (cntMatch[1] === 'LINE') counters.line = entry;
        else if (cntMatch[1] === 'BRANCH') counters.branch = entry;
        else if (cntMatch[1] === 'METHOD') counters.method = entry;
      }
      // Skip classes with 0 total lines (empty interfaces, annotations)
      if (counters.line.covered + counters.line.missed > 0) {
        result.classes[className] = counters;
      }
    }
  }

  return result;
}

function pct(covered, missed) {
  const total = covered + missed;
  return total === 0 ? 0 : (covered / total * 100);
}

module.exports = { parseJacocoXml, pct, zeroCov };
