package org.jahia.community.external.cloud.graphql;

import org.jahia.osgi.BundleUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.Dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * U6 / D3 / D4 — the mount-path security allowlist and the ConfigurationAdmin write mechanism.
 *
 * <p>Two layers are asserted:
 * <ul>
 *   <li>{@link JahiaCloudDumpProviderMutation#validateMountPath(String)} directly — the allowlist
 *       ({@code /sites/}, {@code /mounts/}), {@code ..} rejection, and null/blank rejection, with
 *       the specific {@link IllegalArgumentException} messages (security regression guard);</li>
 *   <li>the public {@code saveSettings(mountPath)} path with {@link BundleUtils} / ConfigurationAdmin
 *       stubbed — an accepted path returns TRUE and calls {@code config.update(props)} exactly once
 *       with the mountPath property (D4 mechanism), a rejected path returns FALSE and never touches
 *       ConfigurationAdmin.</li>
 * </ul>
 */
public class MountPathValidationTest {

    private static final String PID = "org.jahia.community.cloudDumpProvider";

    // ---- Layer 1: direct validateMountPath allowlist assertions ---------------------------------

    @Test
    public void validateMountPath_nullBlankOrWhitespace_isRejected() {
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    public void validateMountPath_pathTraversalSequences_areRejected() {
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath("/sites/../../etc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath("/mounts/a/../b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
    }

    @Test
    public void validateMountPath_outsideAllowedPrefixes_isRejected() {
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath("/etc/secrets"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with one of");
        assertThatThrownBy(() -> JahiaCloudDumpProviderMutation.validateMountPath("/foo/bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with one of");
    }

    @Test
    public void validateMountPath_allowedPrefixes_areAccepted() {
        // No exception thrown for paths under the allowed prefixes.
        JahiaCloudDumpProviderMutation.validateMountPath("/sites/systemsite/files/cloud-dumps");
        JahiaCloudDumpProviderMutation.validateMountPath("/mounts/x");
    }

    // ---- Layer 2: saveSettings end-to-end with stubbed ConfigurationAdmin (D4) ------------------

    @Test
    public void saveSettings_acceptedPath_returnsTrueAndUpdatesConfigOnce() throws Exception {
        final Configuration config = mock(Configuration.class);
        when(config.getProperties()).thenReturn(null);
        final ConfigurationAdmin configAdmin = mock(ConfigurationAdmin.class);
        when(configAdmin.getConfiguration(eq(PID), isNull())).thenReturn(config);

        final String mountPath = "/sites/systemsite/files/cloud-dumps";
        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(configAdmin);

            final Boolean result = new JahiaCloudDumpProviderMutation().saveSettings(mountPath);

            assertThat(result).isTrue();
            final ArgumentCaptor<Dictionary<String, Object>> captor = ArgumentCaptor.forClass(Dictionary.class);
            verify(config, times(1)).update(captor.capture());
            assertThat(captor.getValue().get("mountPath")).isEqualTo(mountPath);
        }
    }

    @Test
    public void saveSettings_rejectedPath_returnsFalseAndNeverTouchesConfigurationAdmin() throws Exception {
        final Configuration config = mock(Configuration.class);
        final ConfigurationAdmin configAdmin = mock(ConfigurationAdmin.class);
        when(configAdmin.getConfiguration(any(), any())).thenReturn(config);

        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(configAdmin);

            // Rejected by validateMountPath before any ConfigurationAdmin lookup.
            final Boolean result = new JahiaCloudDumpProviderMutation().saveSettings("/etc/secrets");

            assertThat(result).isFalse();
            verify(config, never()).update(any());
        }
    }

    @Test
    public void saveSettings_whenConfigurationAdminUnavailable_returnsFalse() {
        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(null);

            final Boolean result =
                    new JahiaCloudDumpProviderMutation().saveSettings("/sites/systemsite/files/cloud-dumps");

            assertThat(result).isFalse();
        }
    }
}
