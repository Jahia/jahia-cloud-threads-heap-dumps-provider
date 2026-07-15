package org.jahia.community.external.cloud;

import org.jahia.modules.external.ExternalData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * U7 — the data source declares {@code ExternalDataSource.Writable} but is read-only in fact:
 * every mutating operation must throw {@link UnsupportedOperationException}. This pins F1's
 * "read-only mount" claim as an enforced code contract, not merely a convention, so a future
 * accidental implementation of one of these methods is caught.
 */
public class ReadOnlyEnforcementTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private JahiaCloudDumpDataSource dataSource;

    @Before
    public void setUp() throws Exception {
        final File root = tmp.newFolder("cloud");
        dataSource = new JahiaCloudDumpDataSource(root.toURI().toString());
        dataSource.setRoot();
    }

    @Test
    public void saveItem_alwaysThrowsUnsupportedOperation() {
        final ExternalData data = new ExternalData("/x", "/x", "jnt:file", new HashMap<>());
        assertThatThrownBy(() -> dataSource.saveItem(data))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void removeItemByPath_alwaysThrowsUnsupportedOperation() {
        assertThatThrownBy(() -> dataSource.removeItemByPath("/thread_dump.txt"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void move_alwaysThrowsUnsupportedOperation() {
        assertThatThrownBy(() -> dataSource.move("/a.txt", "/b.txt"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
