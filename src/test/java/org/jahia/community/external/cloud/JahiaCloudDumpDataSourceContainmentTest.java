package org.jahia.community.external.cloud;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
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
}
