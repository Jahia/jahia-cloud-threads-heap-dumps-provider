package org.jahia.community.external.cloud;

import org.jahia.modules.external.ExternalContentStoreProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * F4 — the OSGi config consumption side: {@code updated(props)} drives the mount path and remount.
 *
 * <p>The real {@link ExternalContentStoreProvider} cannot start() without field-injected Jahia
 * services and a running repository, so the {@code createProvider()} factory seam is overridden to
 * return observable Mockito doubles. This makes the full {@code updated -> remount} lifecycle
 * assertable without Docker/Jahia:
 * <ul>
 *   <li>a valid mountPath is reflected by {@code getMountPath()} and starts a provider;</li>
 *   <li>a second updated() with a different path stops the previous provider before starting the
 *       new one (remount ordering);</li>
 *   <li>null properties, a missing key, and a blank value all fall back to
 *       {@link JahiaCloudDumpMountPointService#DEFAULT_MOUNT_PATH} without NPE.</li>
 * </ul>
 */
public class JahiaCloudDumpMountPointServiceTest {

    /**
     * Test double: substitutes recorded mock providers for the inlined ExternalContentStoreProvider
     * (whose start()/stop() need a live Jahia). Records each created provider for ordering checks.
     */
    private static class TestableService extends JahiaCloudDumpMountPointService {
        private final List<ExternalContentStoreProvider> created = new ArrayList<>();

        @Override
        protected ExternalContentStoreProvider createProvider() {
            final ExternalContentStoreProvider provider = mock(ExternalContentStoreProvider.class);
            created.add(provider);
            return provider;
        }
    }

    private TestableService service;

    @Before
    public void setUp() {
        service = new TestableService();
    }

    private static Hashtable<String, Object> props(String mountPath) {
        final Hashtable<String, Object> p = new Hashtable<>();
        if (mountPath != null) {
            p.put("mountPath", mountPath);
        }
        return p;
    }

    @Test
    public void updated_withMountPath_reflectsPathAndStartsProvider() throws Exception {
        final String path = "/sites/systemsite/files/cloud-dumps-x";

        service.updated(props(path));

        assertThat(service.getMountPath()).isEqualTo(path);
        assertThat(service.created).hasSize(1);
        verify(service.created.get(0)).setMountPoint(path);
        verify(service.created.get(0)).start();
    }

    @Test
    public void updated_secondTime_stopsPreviousProviderBeforeStartingNewOne() throws Exception {
        service.updated(props("/sites/systemsite/files/cloud-dumps-a"));
        service.updated(props("/sites/systemsite/files/cloud-dumps-b"));

        assertThat(service.created).hasSize(2);
        final ExternalContentStoreProvider first = service.created.get(0);
        final ExternalContentStoreProvider second = service.created.get(1);

        // Remount ordering: the previous provider must be stopped before the new one starts.
        final InOrder order = inOrder(first, second);
        order.verify(first).start();
        order.verify(first).stop();
        order.verify(second).start();

        assertThat(service.getMountPath()).isEqualTo("/sites/systemsite/files/cloud-dumps-b");
    }

    @Test
    public void updated_withNullProperties_fallsBackToDefaultWithoutNpe() throws Exception {
        service.updated(null);
        assertThat(service.getMountPath()).isEqualTo(JahiaCloudDumpMountPointService.DEFAULT_MOUNT_PATH);
    }

    @Test
    public void updated_withMissingMountPathKey_fallsBackToDefault() throws Exception {
        service.updated(props(null));
        assertThat(service.getMountPath()).isEqualTo(JahiaCloudDumpMountPointService.DEFAULT_MOUNT_PATH);
    }

    @Test
    public void updated_withBlankMountPath_fallsBackToDefault() throws Exception {
        service.updated(props(""));
        assertThat(service.getMountPath()).isEqualTo(JahiaCloudDumpMountPointService.DEFAULT_MOUNT_PATH);
    }
}
