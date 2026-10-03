const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun, Header, Footer,
  AlignmentType, LevelFormat, HeadingLevel, BorderStyle, WidthType, ShadingType, PageNumber, PageBreak,
} = require("docx");

const GREEN = "1F7A3A", DGREEN = "155C2B", GOLD = "FFE08A", CREAM = "FAF7F0", GREY = "595959", LIGHT = "E6F4EA";
const W = 9026; // A4 content width in DXA with 1" margins
const FONT = "Calibri";

function runs(text, opts = {}) {
  // **bold** inline markup
  const parts = String(text).split(/(\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map((t) => {
    if (t.startsWith("**") && t.endsWith("**")) return new TextRun({ text: t.slice(2, -2), bold: true, font: FONT, ...opts });
    return new TextRun({ text: t, font: FONT, ...opts });
  });
}

const h1 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text: t, font: FONT })], pageBreakBefore: false });
const h2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text: t, font: FONT })] });
const h3 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text: t, font: FONT })] });
const p = (t, o = {}) => new Paragraph({ spacing: { after: 120, line: 276 }, children: runs(t, o.run || {}), alignment: o.align });
const note = (t) => new Paragraph({ spacing: { after: 120 }, children: runs(t, { italics: true, color: GREY, size: 19 }) });
const bullet = (t, level = 0) => new Paragraph({ numbering: { reference: "bul", level }, spacing: { after: 60, line: 264 }, children: runs(t) });
const num = (t, ref = "num") => new Paragraph({ numbering: { reference: ref, level: 0 }, spacing: { after: 60, line: 264 }, children: runs(t) });
const bullets = (arr) => arr.map((t) => bullet(t));
const pb = () => new Paragraph({ children: [new PageBreak()] });
const spacer = (n = 120) => new Paragraph({ spacing: { after: n }, children: [] });

const border = { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" };
const borders = { top: border, bottom: border, left: border, right: border };

function table(headers, rows, widths, opts = {}) {
  const total = widths.reduce((a, b) => a + b, 0);
  const mk = (text, w, head, shade) =>
    new TableCell({
      width: { size: w, type: WidthType.DXA },
      borders,
      shading: head ? { fill: GREEN, type: ShadingType.CLEAR, color: "auto" } : shade ? { fill: shade, type: ShadingType.CLEAR, color: "auto" } : undefined,
      margins: { top: 60, bottom: 60, left: 100, right: 100 },
      children: String(text).split("\n").map((line) =>
        new Paragraph({ spacing: { after: 20 }, children: runs(line, head ? { bold: true, color: "FFFFFF", size: 19 } : { size: 19 }) })),
    });
  const trs = headers ? [new TableRow({ tableHeader: true, children: headers.map((h, i) => mk(h, widths[i], true)) })] : [];
  rows.forEach((r, ri) => trs.push(new TableRow({ cantSplit: true, children: r.map((c, i) => mk(c, widths[i], false, opts.zebra && ri % 2 ? CREAM : (i === 0 && opts.firstColShade ? LIGHT : undefined))) })));
  return new Table({ width: { size: total, type: WidthType.DXA }, columnWidths: widths, rows: trs });
}

function callout(title, lines, fill = LIGHT, edge = GREEN) {
  const b = { style: BorderStyle.SINGLE, size: 4, color: edge };
  const lb = { style: BorderStyle.SINGLE, size: 24, color: edge };
  return new Table({
    width: { size: W, type: WidthType.DXA }, columnWidths: [W],
    rows: [new TableRow({ children: [new TableCell({
      width: { size: W, type: WidthType.DXA },
      borders: { top: b, bottom: b, right: b, left: lb },
      shading: { fill, type: ShadingType.CLEAR, color: "auto" },
      margins: { top: 100, bottom: 100, left: 160, right: 160 },
      children: [new Paragraph({ spacing: { after: 60 }, children: [new TextRun({ text: title, bold: true, font: FONT, color: DGREEN })] }),
        ...lines.map((l) => new Paragraph({ spacing: { after: 40 }, children: runs(l, { size: 20 }) }))],
    })] })],
  });
}

function img(file, widthIn = 6.2, caption) {
  const full = path.join(__dirname, "img", file);
  const buf = fs.readFileSync(full);
  // PNG dimensions
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  const pxW = Math.round(widthIn * 96), pxH = Math.round(pxW * h / w);
  const out = [new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 80, after: 40 }, keepNext: !!caption,
    children: [new ImageRun({ type: "png", data: buf, transformation: { width: pxW, height: pxH },
      altText: { title: caption || file, description: caption || file, name: file } })] })];
  if (caption) out.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 160 }, children: [new TextRun({ text: caption, italics: true, size: 18, color: GREY, font: FONT })] }));
  return out;
}

function cover(title, subtitle, meta) {
  return [
    new Paragraph({ spacing: { before: 1800, after: 120 }, children: [new TextRun({ text: "MarketMoo", bold: true, size: 28, color: GREEN, font: FONT })] }),
    new Paragraph({ spacing: { after: 160 }, children: [new TextRun({ text: title, bold: true, size: 60, color: DGREEN, font: FONT })] }),
    new Paragraph({ spacing: { after: 480 }, border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: GOLD, space: 8 } },
      children: [new TextRun({ text: subtitle, size: 28, color: GREY, font: FONT })] }),
    table(["Item", "Detail"], meta, [2400, 6626], { firstColShade: true }),
    spacer(300),
    note("Draft for group review. Items marked [TO CONFIRM] depend on group decisions or on verifying data sources; they are collected in the Open Questions section."),
    pb(),
  ];
}

function toc(items) {
  return [h1("Contents"), ...items.map((t, i) => new Paragraph({ spacing: { after: 60 }, children: [new TextRun({ text: `${i + 1}.  ${t}`, font: FONT })] })), pb()];
}

function build(file, children, footerLabel) {
  const doc = new Document({
    creator: "MarketMoo project group", title: footerLabel,
    styles: {
      default: { document: { run: { font: FONT, size: 22 } } },
      paragraphStyles: [
        { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 36, bold: true, font: FONT, color: DGREEN }, paragraph: { spacing: { before: 360, after: 160 }, outlineLevel: 0, keepNext: true } },
        { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 28, bold: true, font: FONT, color: GREEN }, paragraph: { spacing: { before: 280, after: 120 }, outlineLevel: 1, keepNext: true } },
        { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 24, bold: true, font: FONT, color: "8B5E34" }, paragraph: { spacing: { before: 200, after: 80 }, outlineLevel: 2, keepNext: true } },
      ],
    },
    numbering: { config: [
      { reference: "bul", levels: [
        { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } },
        { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1000, hanging: 270 } } } }] },
      ...["num", "num2", "num3", "num4", "num5", "num6", "num7", "num8", "num9", "num10"].map((r) => ({ reference: r, levels: [
        { level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 320 } } } }] })),
    ] },
    sections: [{
      properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1300, right: 1440, bottom: 1300, left: 1440 } } },
      headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [new TextRun({ text: "MarketMoo  |  " + footerLabel, size: 17, color: GREY, font: FONT })] })] }) },
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [
        new TextRun({ text: "HGISEO400 project, draft v0.1  |  Page ", size: 17, color: GREY, font: FONT }),
        new TextRun({ children: [PageNumber.CURRENT], size: 17, color: GREY, font: FONT })] })] }) },
      children,
    }],
  });
  return Packer.toBuffer(doc).then((b) => { fs.writeFileSync(file, b); console.log("wrote", file, b.length); });
}

module.exports = { h1, h2, h3, p, note, bullet, bullets, num, pb, spacer, table, callout, img, cover, toc, build, W };
