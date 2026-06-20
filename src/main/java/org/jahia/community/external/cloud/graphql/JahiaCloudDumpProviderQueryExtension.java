package org.jahia.community.external.cloud.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Cloud dump provider queries")
public class JahiaCloudDumpProviderQueryExtension {

    private JahiaCloudDumpProviderQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("cloudDump")
    @GraphQLDescription("Cloud dump provider query namespace")
    public static JahiaCloudDumpProviderQuery cloudDump() {
        return new JahiaCloudDumpProviderQuery();
    }
}
