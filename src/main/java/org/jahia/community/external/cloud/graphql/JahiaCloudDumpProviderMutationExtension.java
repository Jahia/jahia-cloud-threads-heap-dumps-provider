package org.jahia.community.external.cloud.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Cloud dump provider mutations")
public class JahiaCloudDumpProviderMutationExtension {

    private JahiaCloudDumpProviderMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("cloudDump")
    @GraphQLDescription("Cloud dump provider mutation namespace")
    public static JahiaCloudDumpProviderMutation cloudDump() {
        return new JahiaCloudDumpProviderMutation();
    }
}
