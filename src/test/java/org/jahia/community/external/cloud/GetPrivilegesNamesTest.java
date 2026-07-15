package org.jahia.community.external.cloud;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * U3 — characterization of {@link JahiaCloudDumpDataSource#getPrivilegesNames(String, String)},
 * the actual read-authorization gate for dump binaries.
 *
 * <p>This pins the CURRENT behavior deliberately (a characterization test — do not "fix" the
 * design by changing these assertions):
 * <ul>
 *   <li>the check is {@code hasPermission("heapDumpsAdmin")} on the JCR root node {@code /};</li>
 *   <li>the {@code path} argument is <b>ignored</b> — two different paths yield the identical grant;</li>
 *   <li>granted -&gt; {@code ["jcr:read_default"]} ({@code JCR_READ_RIGHTS + "_" + EDIT_WORKSPACE});</li>
 *   <li>denied -&gt; empty array;</li>
 *   <li>a {@link RepositoryException} is caught and yields an empty array (fail-closed).</li>
 * </ul>
 *
 * <p><b>Accepted-risk decision (Stage 7):</b> this all-or-nothing, server-wide read grant — a single
 * {@code heapDumpsAdmin}-on-{@code /} check that disregards the requested path, with no per-file/
 * per-folder ACL and no per-site scoping — is <b>intentional and accepted</b> for the Jahia-Cloud
 * operator context (dumps at a hardcoded internal path {@code /var/tmp/cloud}; both dumps and
 * {@code heapDumpsAdmin} holders operator-controlled). Per-path privilege evaluation remains the
 * hardening option if finer-grained access is ever needed. See the Javadoc on
 * {@link JahiaCloudDumpDataSource#getPrivilegesNames(String, String)}. This test documents that
 * intentional current behavior; do not change these assertions to "fix" the design.
 */
public class GetPrivilegesNamesTest {

    private static final String EXPECTED_GRANT = Constants.JCR_READ_RIGHTS + "_" + Constants.EDIT_WORKSPACE;

    private JCRNodeWrapper mockRootNodeWithPermission(MockedStatic<JCRSessionFactory> factory, boolean granted)
            throws RepositoryException {
        final JCRNodeWrapper rootNode = mock(JCRNodeWrapper.class);
        when(rootNode.hasPermission("heapDumpsAdmin")).thenReturn(granted);
        final JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode("/")).thenReturn(rootNode);
        final JCRSessionFactory sessionFactory = mock(JCRSessionFactory.class);
        when(sessionFactory.getCurrentUserSession(eq(Constants.EDIT_WORKSPACE))).thenReturn(session);
        factory.when(JCRSessionFactory::getInstance).thenReturn(sessionFactory);
        return rootNode;
    }

    @Test
    public void granted_returnsReadDefault_regardlessOfPath() throws Exception {
        final JahiaCloudDumpDataSource ds = new JahiaCloudDumpDataSource("/var/tmp/cloud");
        try (MockedStatic<JCRSessionFactory> factory = mockStatic(JCRSessionFactory.class)) {
            mockRootNodeWithPermission(factory, true);

            final String[] forHeap = ds.getPrivilegesNames("someUser", "/heap/heapdump.hprof");
            final String[] forThread = ds.getPrivilegesNames("someUser", "/thread/other.txt");

            // Identical grant for two different paths => the path argument is ignored.
            assertThat(forHeap).containsExactly(EXPECTED_GRANT);
            assertThat(forThread).containsExactly(EXPECTED_GRANT);
            assertThat(EXPECTED_GRANT).isEqualTo("jcr:read_default");
        }
    }

    @Test
    public void denied_returnsEmptyArray() throws Exception {
        final JahiaCloudDumpDataSource ds = new JahiaCloudDumpDataSource("/var/tmp/cloud");
        try (MockedStatic<JCRSessionFactory> factory = mockStatic(JCRSessionFactory.class)) {
            mockRootNodeWithPermission(factory, false);

            assertThat(ds.getPrivilegesNames("someUser", "/heap/heapdump.hprof")).isEmpty();
        }
    }

    @Test
    public void repositoryException_isCaught_returnsEmptyArrayFailClosed() throws Exception {
        final JahiaCloudDumpDataSource ds = new JahiaCloudDumpDataSource("/var/tmp/cloud");
        try (MockedStatic<JCRSessionFactory> factory = mockStatic(JCRSessionFactory.class)) {
            final JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            when(session.getNode("/")).thenThrow(new RepositoryException("boom"));
            final JCRSessionFactory sessionFactory = mock(JCRSessionFactory.class);
            when(sessionFactory.getCurrentUserSession(anyString())).thenReturn(session);
            factory.when(JCRSessionFactory::getInstance).thenReturn(sessionFactory);

            // Fail-closed: exception swallowed, no privileges granted.
            assertThat(ds.getPrivilegesNames("someUser", "/heap/heapdump.hprof")).isEmpty();
        }
    }
}
