package de.tantive.timetracking.service;

import de.tantive.timetracking.domain.*;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.util.Locale;

/** Outgoing email with DE/EN templates. In the local installation mails are captured, not delivered. */
@Service
public class MailService {
    private final Repos.Mails mails;
    private final MessageSource messages;

    public MailService(Repos.Mails mails, MessageSource messages) { this.mails = mails; this.messages = messages; }

    public void send(AppUser to, String templateKey, Object... args) { send(to.email, to.language, templateKey, args); }

    public void send(String email, String lang, String templateKey, Object... args) {
        Locale l = Locale.forLanguageTag(lang == null ? "de" : lang);
        MailMessage m = new MailMessage();
        m.recipient = email;
        m.lang = l.getLanguage();
        m.subject = messages.getMessage("mail." + templateKey + ".subject", args, l);
        m.body = messages.getMessage("mail." + templateKey + ".body", args, l);
        mails.save(m);
    }
}
