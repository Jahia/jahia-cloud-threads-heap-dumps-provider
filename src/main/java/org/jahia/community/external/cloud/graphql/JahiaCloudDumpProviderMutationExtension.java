package org.jahia.community.external.cloud.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("JahiaCloudDumpProviderMutations")
@GraphQLDescription("Jahia Cloud Dump Provider mutations")
public class JahiaCloudDumpProviderMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpProviderMutationExtension.class);

    private JahiaCloudDumpProviderMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("cloudDumpSaveSettings")
    @GraphQLDescription("Saves the Cloud Dump Provider settings. The provider will remount at the new JCR path.")
    @GraphQLRequiresPermission("admin")
    public static Boolean saveSettings(
            @GraphQLName("mountPath") @GraphQLDescription("JCR path where cloud dump files should be mounted") String mountPath) {
        try {
            final ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
            if (configAdmin == null) {
                return Boolean.FALSE;
            }
            final Configuration config = configAdmin.getConfiguration("org.jahia.community.cloudDumpProvider", null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            if (mountPath != null && !mountPath.isEmpty()) {
                props.put("mountPath", mountPath);
            }
            config.update(props);
            return Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.error("Failed to save cloud dump provider settings", e);
            return Boolean.FALSE;
        }
    }
}
