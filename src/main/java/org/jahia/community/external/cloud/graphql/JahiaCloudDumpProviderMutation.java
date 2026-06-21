package org.jahia.community.external.cloud.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

@GraphQLName("JahiaCloudDumpProviderMutation")
@GraphQLDescription("Cloud dump provider mutations")
public class JahiaCloudDumpProviderMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpProviderMutation.class);

    // (Security LOW) Mount path must be an absolute JCR path inside one of these safe
    // root prefixes. The referenced node MUST be protected by an ACL that restricts
    // write access to administrators, preventing unprivileged users from redirecting
    // the mount to a sensitive JCR location.
    private static final List<String> ALLOWED_MOUNT_PREFIXES = Collections.unmodifiableList(
            Arrays.asList("/sites/", "/mounts/"));

    /**
     * Validates that mountPath is a safe, absolute JCR path.
     * Rejects null/blank values, path-traversal sequences, and paths outside the
     * allowed prefixes ({@code /sites/} or {@code /mounts/}).
     *
     * @param mountPath the proposed JCR mount path
     * @throws IllegalArgumentException if the path is invalid
     */
    private static void validateMountPath(String mountPath) {
        if (mountPath == null || mountPath.trim().isEmpty()) {
            throw new IllegalArgumentException("mountPath must not be null or blank");
        }
        if (mountPath.contains("..")) {
            throw new IllegalArgumentException("mountPath must not contain path-traversal sequences ('..')");
        }
        boolean allowed = false;
        for (String prefix : ALLOWED_MOUNT_PREFIXES) {
            if (mountPath.startsWith(prefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalArgumentException(
                    "mountPath must start with one of: " + ALLOWED_MOUNT_PREFIXES + ". Got: " + mountPath);
        }
    }

    @GraphQLField
    @GraphQLName("saveSettings")
    @GraphQLDescription("Saves the Cloud Dump Provider settings. The provider will remount at the new JCR path."
            + " The mountPath must be an absolute JCR path under /sites/ or /mounts/ and that node must be"
            + " protected by an ACL restricting access to administrators.")
    @GraphQLRequiresPermission("heapDumpsAdmin")
    public Boolean saveSettings(
            @GraphQLName("mountPath") @GraphQLDescription("JCR path where cloud dump files should be mounted") String mountPath) {
        try {
            // (Security LOW) Validate before writing to the OSGi configuration.
            validateMountPath(mountPath);

            final ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
            if (configAdmin == null) {
                return Boolean.FALSE;
            }
            final Configuration config = configAdmin.getConfiguration("org.jahia.community.cloudDumpProvider", null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            props.put("mountPath", mountPath);
            config.update(props);
            return Boolean.TRUE;
        } catch (IllegalArgumentException e) {
            // (Security LOW) Log validation failures at warn level; do not propagate the
            // exception as a GraphQL error to avoid leaking internal path structure.
            LOGGER.warn("Invalid mountPath rejected: {}", e.getMessage());
            return Boolean.FALSE;
        } catch (IOException e) {
            // (C2) Only catch the specific checked exception thrown by ConfigurationAdmin.
            // Unchecked exceptions are allowed to propagate to the GraphQL runtime.
            LOGGER.error("Failed to save cloud dump provider settings", e);
            return Boolean.FALSE;
        }
    }
}
