package org.jahia.community.external.cloud;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the path-traversal / containment boundary of {@link JahiaCloudDumpDataSource#getFile(String)}
 * against a real local filesystem root (commons-vfs2), without a running Jahia.
 */
public class JahiaCloudDumpDataSourceContainmentTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private JahiaCloudDumpDataSource dataSource;
    private File root;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("cloud");
        // A sibling directory whose path shares the root path as a string prefix.
        // The fix must NOT treat this as "inside" the root.
        new File(root.getParentFile(), "cloud-evil").mkdirs();
        Files.write(new File(root, "thread_dump.txt").toPath(), "dump".getBytes());

        dataSource = new JahiaCloudDumpDataSource(root.toURI().toString());
        dataSource.setRoot();
    }

    @Test
    public void getFile_nullOrRootPath_returnsRoot() throws FileSystemException {
        assertThat((Object) dataSource.getFile(null)).isSameAs(dataSource.getRoot());
        assertThat((Object) dataSource.getFile("")).isSameAs(dataSource.getRoot());
        assertThat((Object) dataSource.getFile("/")).isSameAs(dataSource.getRoot());
    }

    @Test
    public void getFile_childInsideRoot_resolves() throws FileSystemException {
        final FileObject file = dataSource.getFile("/thread_dump.txt");
        assertThat(file.exists()).isTrue();
        assertThat(file.getName().getPath()).startsWith(dataSource.getRootPath());
    }

    @Test
    public void getFile_parentTraversal_isRejected() {
        assertThatThrownBy(() -> dataSource.getFile("../cloud-evil"))
                .isInstanceOf(FileSystemException.class)
                .hasMessageContaining("escapes configured root");
    }

    @Test
    public void getFile_absoluteParentTraversal_isRejected() {
        assertThatThrownBy(() -> dataSource.getFile("/../etc/passwd"))
                .isInstanceOf(FileSystemException.class)
                .hasMessageContaining("escapes configured root");
    }

    @Test
    public void getFile_siblingDirectorySharingPrefix_isRejected() {
        // "<root>-evil" shares the root path as a raw string prefix; a bare
        // startsWith(rootPath) check would wrongly accept it.
        assertThatThrownBy(() -> dataSource.getFile("../cloud-evil/secret"))
                .isInstanceOf(FileSystemException.class)
                .hasMessageContaining("escapes configured root");
    }

    // (#1 / C1) itemExists must unescape JCR-encoded characters before resolving.
    @Test
    public void itemExists_withJcrContentSuffix_returnsTrueForExistingFile() {
        // thread_dump.txt exists; asking for the jcr:content child should also return true.
        assertThat(dataSource.itemExists("/thread_dump.txt/jcr:content")).isTrue();
    }

    @Test
    public void itemExists_withEscapedColonInName_returnsTrueAfterUnescape() throws Exception {
        // Create a file whose name contains ':', which must be %-encoded in JCR paths.
        final File timestampFile = new File(root, "2026-01-22T14:15:28Z.txt");
        Files.write(timestampFile.toPath(), "ts".getBytes());

        // The JCR-visible name has the colon escaped; itemExists must unescape it.
        assertThat(dataSource.itemExists("/2026-01-22T14%3A15%3A28Z.txt")).isTrue();
    }

    @Test
    public void itemExists_nonExistentPath_returnsFalse() {
        assertThat(dataSource.itemExists("/does-not-exist.txt")).isFalse();
    }

    // (#3 / C3) Symlink containment: a symlink inside the root pointing outside must be rejected.
    @Test
    public void getFile_symlinkEscapingRoot_isRejected() throws Exception {
        // Skip gracefully on filesystems / OS environments that do not support symlinks.
        final File target = tmp.newFolder("outside-root");
        final File secretFile = new File(target, "secret.txt");
        Files.write(secretFile.toPath(), "secret".getBytes());

        final File symlinkInRoot = new File(root, "escape-link");
        try {
            Files.createSymbolicLink(symlinkInRoot.toPath(), target.toPath());
        } catch (UnsupportedOperationException | IOException e) {
            Assume.assumeNoException("Symlinks not supported on this filesystem; skipping test", e);
        }

        // The symlink points outside the root; the canonical-path check must reject it
        // (fail-closed). Rejection is a FileSystemException whether the escape is detected
        // directly ("escapes configured root via symlink") or the canonical path cannot be
        // verified ("Cannot verify canonical path") — both block the traversal.
        assertThatThrownBy(() -> dataSource.getFile("/escape-link"))
                .isInstanceOf(FileSystemException.class);
    }

    @Test
    public void getCanonicalRootPath_isNotNullAfterSetRoot() {
        // Verify that canonical root path was successfully resolved during setRoot().
        assertThat(dataSource.getCanonicalRootPath()).isNotNull();
    }
}
