package org.jahia.community.external.cloud;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * U9 — {@link JahiaCloudDumpBinaryImpl} lifecycle and positional read contract.
 * getSize()/read(byte[],long) are exercised over a real commons-vfs2 {@link FileContent}
 * so the offset/skip logic is genuinely verified; dispose() is verified against a mock
 * to pin the documented handle-release contract (dispose -> fileContent.close()).
 */
public class JahiaCloudDumpBinaryImplTest {

    private static final String CONTENT = "0123456789";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private FileContent realContent;

    @Before
    public void setUp() throws Exception {
        final File file = tmp.newFile("dump.bin");
        Files.write(file.toPath(), CONTENT.getBytes(StandardCharsets.UTF_8));
        final FileObject fileObject = VFS.getManager().resolveFile(file.toURI().toString());
        realContent = fileObject.getContent();
    }

    @Test
    public void getSize_returnsBackingFileLength() throws Exception {
        final JahiaCloudDumpBinaryImpl binary = new JahiaCloudDumpBinaryImpl(realContent);
        assertThat(binary.getSize()).isEqualTo(CONTENT.length());
    }

    @Test
    public void read_atPositionThree_readsFromThatOffset() throws Exception {
        final JahiaCloudDumpBinaryImpl binary = new JahiaCloudDumpBinaryImpl(realContent);

        final byte[] buf = new byte[4];
        final int count = binary.read(buf, 3L);

        assertThat(count).isEqualTo(4);
        assertThat(new String(buf, StandardCharsets.UTF_8)).isEqualTo("3456");
    }

    @Test
    public void read_atPositionZero_readsFromStart() throws Exception {
        final JahiaCloudDumpBinaryImpl binary = new JahiaCloudDumpBinaryImpl(realContent);

        final byte[] buf = new byte[4];
        final int count = binary.read(buf, 0L);

        assertThat(count).isEqualTo(4);
        assertThat(new String(buf, StandardCharsets.UTF_8)).isEqualTo("0123");
    }

    @Test
    public void dispose_closesUnderlyingFileContent() throws Exception {
        // A mock isolates the documented dispose() -> close() handle-release contract.
        final FileContent mockContent = mock(FileContent.class);
        final JahiaCloudDumpBinaryImpl binary = new JahiaCloudDumpBinaryImpl(mockContent);

        binary.dispose();

        verify(mockContent).close();
    }

    @Test
    public void getSize_isDelegatedToUnderlyingContent() throws Exception {
        // Confirms getSize() reflects the underlying content, independent of the file wiring above.
        final FileContent mockContent = mock(FileContent.class);
        when(mockContent.getSize()).thenReturn(42L);
        final JahiaCloudDumpBinaryImpl binary = new JahiaCloudDumpBinaryImpl(mockContent);

        assertThat(binary.getSize()).isEqualTo(42L);
    }
}
