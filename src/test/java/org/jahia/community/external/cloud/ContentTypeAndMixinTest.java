package org.jahia.community.external.cloud;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.VFS;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalData;
import org.jahia.services.content.JCRContentUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * U8 — image mixin tagging and the MIME-type fallback chain, exercised through the public
 * {@code getItemByPath} entry point (no visibility widening required).
 *
 * <p>commons-vfs2 deterministically reports {@code image/png} for a .png, {@code text/plain} for a
 * .txt, and {@code null} for an extensionless file, which lets the first two branches run against
 * real files. For the third branch (VFS returns null) {@link JCRContentUtils#getMimeType(String)} is
 * mocked so the "VFS -> JCRContentUtils -> application/octet-stream" fallback is deterministic and
 * does not depend on a running Jahia.
 */
public class ContentTypeAndMixinTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private JahiaCloudDumpDataSource dataSource;
    private File root;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("cloud");
        Files.write(new File(root, "pic.png").toPath(),
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        Files.write(new File(root, "thread_dump.txt").toPath(), "dump".getBytes());
        Files.write(new File(root, "mystery").toPath(), "data".getBytes());

        dataSource = new JahiaCloudDumpDataSource(root.toURI().toString());
        dataSource.setRoot();
    }

    @Test
    public void imageFile_isTaggedWithJmixImage() throws Exception {
        final ExternalData data = dataSource.getItemByPath("/pic.png");
        assertThat(data.getMixin()).contains(Constants.JAHIAMIX_IMAGE);
    }

    @Test
    public void textFile_isNotTaggedWithJmixImage() throws Exception {
        final ExternalData data = dataSource.getItemByPath("/thread_dump.txt");
        // A null/empty mixin list both mean "not tagged as image".
        assertThat(data.getMixin() == null || !data.getMixin().contains(Constants.JAHIAMIX_IMAGE)).isTrue();
    }

    @Test
    public void imageFile_contentType_isImagePng() throws Exception {
        final ExternalData content = dataSource.getItemByPath("/pic.png/" + Constants.JCR_CONTENT);
        assertThat(content.getProperties().get(Constants.JCR_MIMETYPE)).containsExactly("image/png");
    }

    @Test
    public void textFile_contentType_isTextPlain() throws Exception {
        final ExternalData content = dataSource.getItemByPath("/thread_dump.txt/" + Constants.JCR_CONTENT);
        assertThat(content.getProperties().get(Constants.JCR_MIMETYPE)).containsExactly("text/plain");
    }

    // The extensionless-file MIME fallback is asserted through getContentType(FileContent) directly
    // rather than getItemByPath, because getItemByPath first calls Escaping.unescapeIllegalJcrChars,
    // which delegates to JCRContentUtils.unescapeLocalNodeName — mocking JCRContentUtils to control
    // getMimeType would also break that unrelated unescape call. getContentType touches only
    // JCRContentUtils.getMimeType, so isolating it keeps the fallback deterministic.
    private FileContent contentOf(String name) throws Exception {
        return VFS.getManager().resolveFile(new File(root, name).toURI().toString()).getContent();
    }

    @Test
    public void extensionlessFile_whenVfsAndJcrUtilBothUnknown_fallsBackToOctetStream() throws Exception {
        final FileContent mystery = contentOf("mystery");
        try (MockedStatic<JCRContentUtils> jcrUtils = mockStatic(JCRContentUtils.class)) {
            // VFS already reports null for the extensionless file; force the second-stage lookup to
            // also return null so the terminal fallback branch is exercised.
            jcrUtils.when(() -> JCRContentUtils.getMimeType("mystery")).thenReturn(null);

            assertThat(dataSource.getContentType(mystery)).isEqualTo("application/octet-stream");
        }
    }

    @Test
    public void extensionlessFile_usesJcrContentUtilsMimeTypeWhenVfsUnknown() throws Exception {
        final FileContent mystery = contentOf("mystery");
        try (MockedStatic<JCRContentUtils> jcrUtils = mockStatic(JCRContentUtils.class)) {
            // Middle branch: VFS null, JCRContentUtils supplies a type -> that type wins.
            jcrUtils.when(() -> JCRContentUtils.getMimeType("mystery")).thenReturn("application/x-heap-dump");

            assertThat(dataSource.getContentType(mystery)).isEqualTo("application/x-heap-dump");
        }
    }
}
