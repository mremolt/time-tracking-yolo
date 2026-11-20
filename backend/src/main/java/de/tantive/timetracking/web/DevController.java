package de.tantive.timetracking.web;

import de.tantive.timetracking.config.ApiException;
import de.tantive.timetracking.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Local installation only: captured emails can be read without login (step 1). Disabled by app.mail-viewer-enabled=false. */
@RestController
@RequestMapping("/api/dev")
public class DevController {
    private final Repos.Mails mails;
    @Value("${app.mail-viewer-enabled}") boolean enabled;

    public DevController(Repos.Mails mails) { this.mails = mails; }

    @GetMapping("/mails")
    public List<MailMessage> mails() {
        if (!enabled) throw ApiException.notFound();
        return mails.findAllByOrderByIdDesc().stream().limit(100).toList();
    }
}
