///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.poi:poi:5.4.1

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hpsf.ClassID;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Writes the sailing-club newsletter test message: an HTML newsletter with one inline wide photo and two ordinary
 * attachments, laid out the way Outlook writes a .msg file (MS-OXMSG), so Outlook, the parser and Letterblade all read
 * the same message.
 *
 * <p>Usage, from the repository root:
 * {@code jbang src/test/fixtures/NewsletterMsg.java <banner.jpg> src/test/resources/test-messages/sailing-club-newsletter.msg}
 *
 * <p>The banner is "Sunset Key and Sailboats, Florida, 2025" by Julian Lupyan, CC0, from Wikimedia Commons
 * (https://commons.wikimedia.org/wiki/File:Sunset_Key_and_Sailboats,_Florida,_2025.jpg), cropped to 2.5:1 and
 * resized to 1440 x 576 (JPEG quality 70). The photo is not kept in the repository; it lives inside the message.
 * Not compiled by Maven: this folder is outside the test source tree, so the script needs no project dependency.
 */
public class NewsletterMsg {

    // MAPI property types (the low 16 bits of a property tag).
    static final int PT_LONG = 0x0003;
    static final int PT_BOOLEAN = 0x000B;
    static final int PT_SYSTIME = 0x0040;
    static final int PT_UNICODE = 0x001F;
    static final int PT_BINARY = 0x0102;

    /** Property flags in the properties stream: readable + writable, what Outlook writes. */
    static final int PROP_READABLE_WRITABLE = 6;

    static final ZonedDateTime SENT = ZonedDateTime.of(2026, 9, 28, 18, 0, 0, 0, ZoneId.of("Europe/Zurich"));
    static final String CLUB = "Lake Geneva Sailing Club";
    static final String FROM_EMAIL = "newsletter@sailingclub.example.com";
    static final String TO_NAME = "Club Members";
    static final String TO_EMAIL = "members@sailingclub.example.com";
    static final String SUBJECT = "September Newsletter: regatta results and the autumn programme";
    static final String MESSAGE_ID = "<newsletter-2026-09@sailingclub.example.com>";
    static final String BANNER_CID = "banner@sailingclub.example.com";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: jbang NewsletterMsg.java <banner.jpg> <out.msg>");
            System.exit(2);
        }
        final byte[] banner = Files.readAllBytes(Path.of(args[0]));
        final Path out = Path.of(args[1]);

        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            final DirectoryEntry root = fs.getRoot();
            root.setStorageClsid(new ClassID("{00020D0B-0000-0000-C000-000000000046}")); // Outlook message storage

            final List<Attachment> attachments = List.of(
                    Attachment.inline("sunset-sailboats.jpg", "SUNSET~1.JPG", "image/jpeg", BANNER_CID, banner),
                    Attachment.file("autumn-programme-2026.pdf", "AUTUMN~1.PDF", "application/pdf", programmePdf()),
                    Attachment.file("autumn-regatta.ics", "AUTUMN~2.ICS", "text/calendar", regattaCalendar()));

            writeStorage(root, messageProperties(attachments.size()), rootHeader(1, attachments.size(), 1, attachments.size()));
            writeNamedPropertyStorage(root);
            writeStorage(root.createDirectory("__recip_version1.0_#00000000"), recipientProperties(), new byte[8]);
            for (int i = 0; i < attachments.size(); i++) {
                final DirectoryEntry dir = root.createDirectory(String.format("__attach_version1.0_#%08X", i));
                writeStorage(dir, attachments.get(i).properties(i), new byte[8]);
            }

            try (OutputStream os = Files.newOutputStream(out)) {
                fs.writeFilesystem(os);
            }
        }
        System.out.printf("wrote %s (%,d bytes): banner %,d B, %d attachments%n", out, Files.size(out), banner.length, 3);
    }

    // ---- the message ------------------------------------------------------------------------------------------------

    static List<Prop> messageProperties(int attachmentCount) {
        final String html = newsletterHtml();
        final List<Prop> p = new ArrayList<>();
        p.add(Prop.text(0x001A, "IPM.Note"));                       // message class
        p.add(Prop.text(0x0037, SUBJECT));                          // subject
        p.add(Prop.text(0x0E1D, SUBJECT));                          // normalized subject
        p.add(Prop.text(0x0070, SUBJECT));                          // conversation topic
        p.add(Prop.text(0x0C1A, CLUB));                             // sender name
        p.add(Prop.text(0x0C1E, "SMTP"));                           // sender address type
        p.add(Prop.text(0x0C1F, FROM_EMAIL));                       // sender email
        p.add(Prop.text(0x5D01, FROM_EMAIL));                       // sender SMTP address
        p.add(Prop.text(0x0042, CLUB));                             // sent-representing name
        p.add(Prop.text(0x0064, "SMTP"));                           // sent-representing address type
        p.add(Prop.text(0x0065, FROM_EMAIL));                       // sent-representing email
        p.add(Prop.text(0x5D02, FROM_EMAIL));                       // sent-representing SMTP address
        p.add(Prop.text(0x0E04, TO_NAME));                          // display To
        p.add(Prop.text(0x0E03, ""));                               // display Cc
        p.add(Prop.text(0x0E02, ""));                               // display Bcc
        p.add(Prop.text(0x1035, MESSAGE_ID));                       // internet message id
        p.add(Prop.text(0x007D, transportHeaders()));               // transport message headers
        p.add(Prop.text(0x1000, newsletterText()));                 // plain-text body
        p.add(Prop.binary(0x1013, html.getBytes(StandardCharsets.UTF_8))); // HTML body, in the internet code page
        p.add(Prop.number(0x3FDE, 65001));                          // internet code page: UTF-8
        p.add(Prop.number(0x3FFD, 1252));                           // message code page
        p.add(Prop.number(0x340D, 0x00040000));                     // store support mask: Unicode strings
        p.add(Prop.number(0x0E07, 0x11));                           // message flags: read + has attachments
        p.add(Prop.flag(0x0E1B, true));                             // has attachments
        p.add(Prop.number(0x0017, 1));                              // importance: normal
        p.add(Prop.number(0x0026, 0));                              // priority: normal
        p.add(Prop.number(0x0036, 0));                              // sensitivity: none
        p.add(Prop.flag(0x0029, false));                            // no read receipt
        p.add(Prop.time(0x0039, SENT));                             // client submit time
        p.add(Prop.time(0x0E06, SENT.plusSeconds(20)));             // message delivery time
        p.add(Prop.time(0x3007, SENT));                             // creation time
        p.add(Prop.time(0x3008, SENT.plusSeconds(20)));             // last modification time
        return p;
    }

    static List<Prop> recipientProperties() {
        final List<Prop> p = new ArrayList<>();
        p.add(Prop.number(0x3000, 0));                              // row id
        p.add(Prop.number(0x0C15, 1));                              // recipient type: To
        p.add(Prop.number(0x0FFE, 6));                              // object type: mail user
        p.add(Prop.number(0x3900, 0));                              // display type: mail user
        p.add(Prop.text(0x3001, TO_NAME));                          // display name
        p.add(Prop.text(0x3002, "SMTP"));                           // address type
        p.add(Prop.text(0x3003, TO_EMAIL));                         // email address
        p.add(Prop.text(0x39FE, TO_EMAIL));                         // SMTP address
        p.add(Prop.text(0x5FF6, TO_NAME));                          // transmittable display name
        return p;
    }

    static String transportHeaders() {
        return "From: " + CLUB + " <" + FROM_EMAIL + ">\r\n"
                + "To: " + TO_NAME + " <" + TO_EMAIL + ">\r\n"
                + "Subject: " + SUBJECT + "\r\n"
                + "Date: " + SENT.format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\r\n"
                + "Message-ID: " + MESSAGE_ID + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed\r\n";
    }

    static String newsletterHtml() {
        final String cell = "padding:6px 8px;border-bottom:1px solid #e1e6eb;";
        final String head = "padding:6px 8px;border-bottom:2px solid #c9d2db;";
        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
                <title>September Newsletter</title>
                </head>
                <body style="margin:0;padding:0;background:#eef2f5;">
                <table width="100%%" style="background:#eef2f5;"><tr><td align="center" style="padding:18px 0;">
                <table width="720" style="background:#ffffff;border:1px solid #d5dce3;">
                <tr><td style="background:#143a5c;color:#ffffff;padding:10px 24px;font-family:Arial,Helvetica,sans-serif;font-size:12px;letter-spacing:2px;">LAKE GENEVA SAILING CLUB &nbsp;|&nbsp; SEASON NEWSLETTER</td></tr>
                <tr><td style="padding:0;line-height:0;"><img src="cid:%s" width="720" alt="Two sailboats off a palm-lined shore in evening light" style="display:block;width:720px;height:auto;border:0;"></td></tr>
                <tr><td style="padding:28px 32px 8px;font-family:Georgia,'Times New Roman',serif;color:#222222;font-size:16px;line-height:1.5;">
                <h1 style="margin:0 0 14px;font-family:Georgia,'Times New Roman',serif;font-size:30px;color:#143a5c;">September Newsletter</h1>
                <p>Dear members,</p>
                <p>What a season. The June regatta drew 34 boats across three classes, a steady 12-knot westerly gave us two days of close racing, and the warm September kept the beginner courses full to the last place. Here is where we stand as the water cools.</p>
                <h2 style="font-size:19px;color:#143a5c;margin:22px 0 8px;">Regatta results</h2>
                <table width="100%%" style="border-collapse:collapse;font-family:Arial,Helvetica,sans-serif;font-size:14px;">
                <tr style="background:#eef2f5;"><th align="left" style="%2$s">Class</th><th align="left" style="%2$s">Boat</th><th align="left" style="%2$s">Skipper</th><th align="right" style="%2$s">Elapsed</th></tr>
                <tr><td style="%3$s">Keelboat</td><td style="%3$s">Albatros II</td><td style="%3$s">M. Favre</td><td align="right" style="%3$s">2:41:07</td></tr>
                <tr><td style="%3$s">Keelboat</td><td style="%3$s">Mistral</td><td style="%3$s">C. Rochat</td><td align="right" style="%3$s">2:43:52</td></tr>
                <tr><td style="%3$s">Dinghy</td><td style="%3$s">Libellule</td><td style="%3$s">A. Keller</td><td align="right" style="%3$s">1:58:30</td></tr>
                <tr><td style="%3$s">Catamaran</td><td style="%3$s">Foehn</td><td style="%3$s">S. Baumann</td><td align="right" style="%3$s">1:36:12</td></tr>
                </table>
                <p style="font-size:14px;color:#555555;">Full results, protests and the photo gallery are on the notice board at the clubhouse.</p>
                <h2 style="font-size:19px;color:#143a5c;margin:22px 0 8px;">Autumn programme</h2>
                <ul>
                <li><b>Autumn Regatta</b>, Saturday 17 October. Skippers' briefing at 09:00, first start 10:30. Calendar entry attached.</li>
                <li><b>Night sailing</b>, Friday 30 October. Navigation lights are checked at the pontoon from 18:00.</li>
                <li><b>Haul-out weekend</b>, 14 and 15 November. Sign up for a crane slot by 6 November.</li>
                <li><b>Season dinner</b>, Saturday 28 November at the clubhouse. Partners welcome.</li>
                </ul>
                <p>The complete programme with fees and contacts is attached as a PDF. Places for the Autumn Regatta are limited to 40 boats:</p>
                <p style="margin:18px 0 22px;"><a href="https://sailingclub.example.com/autumn-regatta" style="background:#143a5c;color:#ffffff;padding:10px 18px;text-decoration:none;font-family:Arial,Helvetica,sans-serif;font-size:14px;">Enter the Autumn Regatta</a></p>
                <p>Fair winds,<br>The Committee</p>
                </td></tr>
                <tr><td style="background:#eef2f5;color:#60707f;padding:12px 32px;font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:1.5;">You receive this newsletter as a member of the Lake Geneva Sailing Club. Photo: Julian Lupyan, CC0, via Wikimedia Commons.</td></tr>
                </table>
                </td></tr></table>
                </body>
                </html>
                """.formatted(BANNER_CID, head, cell);
    }

    static String newsletterText() {
        return """
                LAKE GENEVA SAILING CLUB | SEASON NEWSLETTER

                September Newsletter

                Dear members,

                What a season. The June regatta drew 34 boats across three classes, a steady 12-knot westerly gave us two days of close racing, and the warm September kept the beginner courses full to the last place. Here is where we stand as the water cools.

                Regatta results
                  Keelboat   Albatros II   M. Favre     2:41:07
                  Keelboat   Mistral       C. Rochat    2:43:52
                  Dinghy     Libellule     A. Keller    1:58:30
                  Catamaran  Foehn         S. Baumann   1:36:12
                Full results, protests and the photo gallery are on the notice board at the clubhouse.

                Autumn programme
                  - Autumn Regatta, Saturday 17 October. Skippers' briefing at 09:00, first start 10:30. Calendar entry attached.
                  - Night sailing, Friday 30 October. Navigation lights are checked at the pontoon from 18:00.
                  - Haul-out weekend, 14 and 15 November. Sign up for a crane slot by 6 November.
                  - Season dinner, Saturday 28 November at the clubhouse. Partners welcome.

                The complete programme with fees and contacts is attached as a PDF. Places for the Autumn Regatta are limited to 40 boats: https://sailingclub.example.com/autumn-regatta

                Fair winds,
                The Committee

                You receive this newsletter as a member of the Lake Geneva Sailing Club.
                """.replace("\n", "\r\n");
    }

    // ---- the attachments --------------------------------------------------------------------------------------------

    /** One attachment: inline ones carry a Content-ID the HTML refers to and are hidden from the attachment list. */
    record Attachment(String longName, String shortName, String mimeTag, String contentId, byte[] data) {

        static Attachment inline(String longName, String shortName, String mimeTag, String contentId, byte[] data) {
            return new Attachment(longName, shortName, mimeTag, contentId, data);
        }

        static Attachment file(String longName, String shortName, String mimeTag, byte[] data) {
            return new Attachment(longName, shortName, mimeTag, null, data);
        }

        boolean inline() {
            return contentId != null;
        }

        List<Prop> properties(int number) {
            final List<Prop> p = new ArrayList<>();
            p.add(Prop.number(0x0E21, number));                     // attachment number
            p.add(Prop.number(0x0FFE, 7));                          // object type: attachment
            p.add(Prop.number(0x3705, 1));                          // attach method: by value
            p.add(Prop.number(0x370B, -1));                         // rendering position: not in the text
            p.add(Prop.number(0x3714, inline() ? 4 : 0));           // attach flags: 4 = referenced from MHTML (cid)
            p.add(Prop.flag(0x7FFE, inline()));                     // hidden from the attachment list when inline
            p.add(Prop.text(0x3001, longName));                     // display name
            p.add(Prop.text(0x3703, longName.substring(longName.lastIndexOf('.')))); // extension
            p.add(Prop.text(0x3704, shortName));                    // 8.3 file name
            p.add(Prop.text(0x3707, longName));                     // long file name
            p.add(Prop.text(0x370E, mimeTag));                      // MIME tag
            if (inline()) {
                p.add(Prop.text(0x3712, contentId));                // Content-ID, the target of the cid: reference
            }
            p.add(Prop.binary(0x3701, data));                       // the bytes
            p.add(Prop.time(0x3007, SENT));
            p.add(Prop.time(0x3008, SENT));
            return p;
        }
    }

    /** A one-page PDF with the programme as text lines; hand-assembled so the fixture needs no PDF library. */
    static byte[] programmePdf() {
        final String[] lines = {
            "Lake Geneva Sailing Club - Autumn programme 2026",
            "",
            "Sat 17 Oct   Autumn Regatta       briefing 09:00, first start 10:30, entry CHF 40",
            "Fri 30 Oct   Night sailing        pontoon from 18:00, lights check compulsory",
            "Sat 14 Nov   Haul-out weekend     crane slots 08:00 - 17:00, book by 6 Nov",
            "Sun 15 Nov   Haul-out weekend     crane slots 08:00 - 17:00",
            "Sat 28 Nov   Season dinner        clubhouse 19:00, partners welcome, CHF 65",
            "",
            "Contacts: regatta@sailingclub.example.com, harbour@sailingclub.example.com",
        };
        final StringBuilder content = new StringBuilder("BT /F1 11 Tf 14 TL 56 780 Td\n");
        for (String line : lines) {
            content.append('(').append(line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")).append(") Tj T*\n");
        }
        content.append("ET\n");
        final String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>",
        };
        final StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        final List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        final int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] regattaCalendar() {
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Lake Geneva Sailing Club//Newsletter//EN",
                "BEGIN:VEVENT",
                "UID:autumn-regatta-2026@sailingclub.example.com",
                "DTSTAMP:20260928T160000Z",
                "DTSTART:20261017T070000Z",
                "DTEND:20261017T150000Z",
                "SUMMARY:Autumn Regatta 2026",
                "LOCATION:Club harbour",
                "DESCRIPTION:Skippers' briefing at 09:00 at the clubhouse\\, first start 10:30.",
                "END:VEVENT",
                "END:VCALENDAR",
                "").getBytes(StandardCharsets.UTF_8);
    }

    // ---- MS-OXMSG plumbing ------------------------------------------------------------------------------------------

    /** One MAPI property: either a fixed 8-byte value inside the properties stream, or data in its own stream. */
    record Prop(int id, int type, long fixedValue, byte[] data) {

        static Prop text(int id, String value) {
            return new Prop(id, PT_UNICODE, 0, value.getBytes(StandardCharsets.UTF_16LE));
        }

        static Prop binary(int id, byte[] value) {
            return new Prop(id, PT_BINARY, 0, value);
        }

        static Prop number(int id, int value) {
            return new Prop(id, PT_LONG, value & 0xFFFFFFFFL, null);
        }

        static Prop flag(int id, boolean value) {
            return new Prop(id, PT_BOOLEAN, value ? 1 : 0, null);
        }

        /** PT_SYSTIME: a Windows FILETIME, 100 ns ticks since 1601-01-01 UTC. */
        static Prop time(int id, ZonedDateTime value) {
            final long millisSince1601 = value.toInstant().toEpochMilli() + 11_644_473_600_000L;
            return new Prop(id, PT_SYSTIME, millisSince1601 * 10_000L, null);
        }

        boolean fixed() {
            return data == null;
        }

        String streamName() {
            return String.format("__substg1.0_%04X%04X", id, type);
        }

        /** The size field of a variable-length entry: Unicode strings count their missing terminator. */
        int declaredSize() {
            return data.length + (type == PT_UNICODE ? 2 : 0);
        }
    }

    /** The 32-byte header of the top-level properties stream. */
    static byte[] rootHeader(int nextRecipientId, int nextAttachmentId, int recipientCount, int attachmentCount) {
        final ByteBuffer b = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(0);
        b.putInt(nextRecipientId).putInt(nextAttachmentId).putInt(recipientCount).putInt(attachmentCount);
        b.putLong(0);
        return b.array();
    }

    /** Writes one storage: a stream per variable-length property plus the properties stream listing all of them. */
    static void writeStorage(DirectoryEntry dir, List<Prop> props, byte[] header) throws IOException {
        final ByteBuffer entries = ByteBuffer.allocate(header.length + props.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
        entries.put(header);
        for (Prop p : props) {
            entries.putInt((p.id() << 16) | p.type());
            entries.putInt(PROP_READABLE_WRITABLE);
            if (p.fixed()) {
                entries.putLong(p.fixedValue());
            } else {
                entries.putInt(p.declaredSize());
                entries.putInt(0);
                dir.createDocument(p.streamName(), new ByteArrayInputStream(p.data()));
            }
        }
        dir.createDocument("__properties_version1.0", new ByteArrayInputStream(entries.array()));
    }

    /** The named-property mapping storage: required by the format, empty because the message uses none. */
    static void writeNamedPropertyStorage(DirectoryEntry root) throws IOException {
        final DirectoryEntry nameid = root.createDirectory("__nameid_version1.0");
        for (String stream : List.of("__substg1.0_00020102", "__substg1.0_00030102", "__substg1.0_00040102")) {
            nameid.createDocument(stream, new ByteArrayInputStream(new byte[0]));
        }
    }
}
