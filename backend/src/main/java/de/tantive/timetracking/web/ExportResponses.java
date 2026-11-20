package de.tantive.timetracking.web;

import de.tantive.timetracking.config.ApiException;
import de.tantive.timetracking.service.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
public class ExportResponses {
    private final Exporter exporter;
    public ExportResponses(Exporter e) { exporter = e; }

    public ResponseEntity<byte[]> of(Table t, String format, String baseName) {
        byte[] body;
        MediaType type;
        String ext;
        switch (format == null ? "" : format.toLowerCase()) {
            case "csv" -> { body = exporter.csv(t); type = MediaType.parseMediaType("text/csv;charset=UTF-8"); ext = "csv"; }
            case "xlsx" -> { body = exporter.xlsx(t); type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); ext = "xlsx"; }
            case "pdf" -> { body = exporter.pdf(t); type = MediaType.APPLICATION_PDF; ext = "pdf"; }
            default -> throw ApiException.bad("FORMAT_UNKNOWN");
        }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(baseName + "." + ext).build().toString())
                .body(body);
    }
}
