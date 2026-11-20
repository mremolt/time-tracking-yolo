package de.tantive.timetracking.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class Exporter {
    public byte[] csv(Table t) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(line(t.headers()));
        for (List<String> r : t.rows()) sb.append(line(r));
        if (!t.totals().isEmpty()) sb.append(line(t.totals()));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String line(List<String> cells) {
        StringJoiner j = new StringJoiner(";", "", "\r\n");
        for (String c : cells) j.add(esc(c));
        return j.toString();
    }

    private static String esc(String c) {
        if (c == null) return "";
        // neutralise spreadsheet formula injection
        String v = (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0 && !c.matches("-?[0-9][0-9.,:]*")) ? "'" + c : c;
        return v.contains(";") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    public byte[] xlsx(Table t) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Report");
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true); bold.setFont(f);
            int rn = 0;
            rn = row(sh, rn, t.headers(), bold);
            for (List<String> r : t.rows()) rn = row(sh, rn, r, null);
            if (!t.totals().isEmpty()) rn = row(sh, rn, t.totals(), bold);
            for (int i = 0; i < t.headers().size(); i++) sh.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static int row(Sheet sh, int rn, List<String> cells, CellStyle style) {
        Row r = sh.createRow(rn);
        for (int i = 0; i < cells.size(); i++) {
            Cell c = r.createCell(i);
            c.setCellValue(cells.get(i) == null ? "" : cells.get(i)); // always text: no formulas can be injected
            if (style != null) c.setCellStyle(style);
        }
        return rn + 1;
    }

    public byte[] pdf(Table t) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            float margin = 30, y = 0, lh = 13;
            int cols = Math.max(1, t.headers().size());
            float colW = (size.getWidth() - 2 * margin) / cols;
            int maxChars = Math.max(4, (int) (colW / 4.6));
            PDPageContentStream cs = null;
            List<List<String>> all = new ArrayList<>(t.rows());
            if (!t.totals().isEmpty()) all.add(t.totals());
            int idx = -1; // -1 header
            boolean first = true;
            int total = all.size();
            int i = 0;
            while (first || i < total) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                if (cs != null) cs.close();
                cs = new PDPageContentStream(doc, page);
                y = size.getHeight() - margin;
                if (first) {
                    text(cs, bold, 14, margin, y, t.title()); y -= 20;
                    for (String n : t.notes()) { text(cs, font, 9, margin, y, n); y -= 12; }
                    y -= 4;
                }
                first = false;
                drawRow(cs, bold, margin, y, colW, maxChars, t.headers()); y -= lh;
                while (i < total && y > margin + lh) {
                    List<String> r = all.get(i);
                    boolean isTotal = !t.totals().isEmpty() && i == total - 1;
                    drawRow(cs, isTotal ? bold : font, margin, y, colW, maxChars, r);
                    y -= lh; i++;
                }
            }
            if (cs != null) cs.close();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static void drawRow(PDPageContentStream cs, PDFont font, float x, float y, float colW, int maxChars, List<String> cells) throws Exception {
        for (int c = 0; c < cells.size(); c++) {
            String s = cells.get(c) == null ? "" : cells.get(c);
            if (s.length() > maxChars) s = s.substring(0, maxChars - 1) + "~";
            text(cs, font, 8, x + c * colW, y, s);
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s) throws Exception {
        StringBuilder safe = new StringBuilder();
        for (char ch : s.toCharArray()) safe.append(ch < 256 && ch >= 32 ? ch : (ch == '–' ? '-' : '?'));
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe.toString());
        cs.endText();
    }
}
