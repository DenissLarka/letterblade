package com.druvu.letterblade.msg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;
import org.testng.annotations.Test;

/**
 * Fixture-based tests of {@link MsgService#parse} against real {@code .msg} files copied from outlook-message-parser
 * (see {@code test-messages/README.md} for provenance). Expected values were observed by parsing the fixtures directly,
 * not assumed.
 */
public class MsgServiceTest {

    private final MsgService service = new MsgService();

    private File fixture(String name) throws Exception {
        return new File(getClass().getResource("/test-messages/" + name).toURI());
    }

    @Test
    public void parsesRtfOnlyEmail() throws Exception {
        ParsedMessage m = service.parse(fixture("rtf-sample-email.msg"));
        assertThat(m.subject()).isEqualTo("RtfSampleEmail");
        assertThat(m.fromName()).isEqualTo("Wilson, Chris");
        assertThat(m.fromEmail()).isEqualTo("Chris.Wilson@leeds.gov.uk");
        assertThat(m.displayTo()).contains("Wilson, Chris");
        assertThat(m.date()).isNotNull();
        // Outlook stores the body as RTF -> converted-HTML branch, no native HTML.
        assertThat(m.pickBranch()).isEqualTo(BodyBranch.RTF_CONVERTED);
        assertThat(m.bodyHtml()).isNotBlank();
        assertThat(m.plainText()).isNotBlank();
        assertThat(m.attachments()).isEmpty();
        assertThat(m.embeddedMessages()).isEmpty();
        assertThat(m.cidMap()).isEmpty();
    }

    @Test
    public void parsesEnvelopeAttachmentsAndInlineImage() throws Exception {
        ParsedMessage m = service.parse(fixture("html-with-attachment-and-embedded-image.msg"));
        assertThat(m.subject()).isEqualTo("hey");
        assertThat(m.fromEmail()).isEqualTo("b.bottema@projectnibble.org");
        assertThat(m.pickBranch()).isEqualTo(BodyBranch.RTF_CONVERTED);
        assertThat(m.attachments()).hasSize(2);
        assertThat(m.attachments())
                .extracting(OutlookFileAttachment::getLongFilename)
                .contains("dresscode.txt", "location.txt");
        // one inline image referenced from the body by Content-ID
        assertThat(m.cidMap()).containsKey("thumbsup");
        assertThat(m.embeddedMessages()).isEmpty();
    }

    @Test
    public void parsesAttachmentsWithNullFilenames() throws Exception {
        ParsedMessage m = service.parse(fixture("attachments.msg"));
        // Two delivery-status parts whose filenames are null - documents the Gate-5 fallback need.
        assertThat(m.attachments()).hasSize(2);
        assertThat(m.attachments()).allSatisfy(a -> {
            assertThat(a.getLongFilename()).isNull();
            assertThat(a.getFilename()).isNull();
        });
    }

    @Test
    public void parsesEmbeddedMessage() throws Exception {
        ParsedMessage m = service.parse(fixture("nested-simple-mail.msg"));
        assertThat(m.subject()).isEqualTo("outlookmsg2html Testmail");
        assertThat(m.embeddedMessages()).hasSize(1);
        assertThat(m.attachments()).isEmpty();
    }

    @Test
    public void parseEmbeddedMapsNestedMessageToItsOwnViewModel() throws Exception {
        ParsedMessage outer = service.parse(fixture("nested-simple-mail.msg"));
        assertThat(outer.embeddedMessages()).hasSize(1);

        ParsedMessage inner = service.parseEmbedded(outer.embeddedMessages().get(0));
        assertThat(inner.subject()).isEqualTo("outlookmsg2html Testmail");
        assertThat(inner.fromName()).isEqualTo("REISINGER Emanuel");
        assertThat(inner.fromEmail()).isEqualTo("Emanuel.Reisinger@cargonet.software");
        assertThat(inner.plainText()).isNotBlank();
        assertThat(inner.attachments()).isEmpty();
        assertThat(inner.embeddedMessages()).isEmpty();
    }

    @Test
    public void corruptFileThrowsCheckedException() throws Exception {
        Path notMsg = Files.createTempFile("letterblade-corrupt", ".msg");
        Files.writeString(notMsg, "this is plain text, not an OLE2 .msg document");
        try {
            assertThatThrownBy(() -> service.parse(notMsg.toFile())).isInstanceOf(MsgParseException.class);
        } finally {
            Files.deleteIfExists(notMsg);
        }
    }
}
