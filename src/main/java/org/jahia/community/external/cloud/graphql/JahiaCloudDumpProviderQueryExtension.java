package org.jahia.community.external.cloud.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.external.cloud.JahiaCloudDumpMountPointService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLName("JahiaCloudDumpProviderQueries")
@GraphQLDescription("Jahia Cloud Dump Provider queries")
public class JahiaCloudDumpProviderQueryExtension {

    private JahiaCloudDumpProviderQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("cloudDumpSettings")
    @GraphQLDescription("Returns the current Cloud Dump Provider settings")
    @GraphQLRequiresPermission("admin")
    public static GqlSettings settings() {
        final JahiaCloudDumpMountPointService service = BundleUtils.getOsgiService(JahiaCloudDumpMountPointService.class, null);
        final String mountPath = service != null ? service.getMountPath() : JahiaCloudDumpMountPointService.DEFAULT_MOUNT_PATH;
        return new GqlSettings(mountPath, JahiaCloudDumpMountPointService.DUMP_PATH);
    }

    @GraphQLName("CloudDumpSettings")
    @GraphQLDescription("Cloud Dump Provider settings")
    public static class GqlSettings {

        private final String mountPath;
        private final String dumpPath;

        public GqlSettings(String mountPath, String dumpPath) {
            this.mountPath = mountPath;
            this.dumpPath = dumpPath;
        }

        @GraphQLField
        @GraphQLName("mountPath")
        @GraphQLDescription("JCR path where cloud dump files are mounted")
        public String getMountPath() {
            return mountPath;
        }

        @GraphQLField
        @GraphQLName("dumpPath")
        @GraphQLDescription("Filesystem path where cloud dumps are stored")
        public String getDumpPath() {
            return dumpPath;
        }
    }
}
