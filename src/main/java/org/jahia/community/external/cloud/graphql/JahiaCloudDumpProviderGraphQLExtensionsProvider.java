package org.jahia.community.external.cloud.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

/**
 * Registers this bundle's GraphQL type extensions with the graphql-dxm-provider runtime.
 *
 * <p>The graphql-dxm auto-discovery mechanism scans in-bundle classes annotated with
 * {@code @GraphQLTypeExtension} when this {@link DXGraphQLExtensionsProvider} component is
 * registered. No explicit {@code getExtensions()} registration is needed; OSGi service
 * registration is sufficient.
 */
@Component(immediate = true)
public class JahiaCloudDumpProviderGraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {
}
