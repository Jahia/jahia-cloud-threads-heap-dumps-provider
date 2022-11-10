package org.jahia.community.external.cloud.factory;

import java.io.Serializable;
import java.util.Locale;
import javax.jcr.RepositoryException;
import org.jahia.modules.external.admin.mount.AbstractMountPointFactoryHandler;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.utils.i18n.Messages;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.binding.message.MessageContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.webflow.execution.RequestContext;

public final class JahiaCloudDumpMountPointFactoryHandler extends AbstractMountPointFactoryHandler<JahiaCloudDumpMountPointFactory> implements Serializable {

    private static final long serialVersionUID = 7189210242067838479L;
    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpMountPointFactoryHandler.class);
    private static final String BUNDLE = "resources.jahia-cloud-threads-heap-dumps-provider";
    private JahiaCloudDumpMountPointFactory jahiaCloudDumpMountPointFactory;
    private String stateCode;
    private String messageKey;

    public void init(RequestContext requestContext) {
        jahiaCloudDumpMountPointFactory = new JahiaCloudDumpMountPointFactory();
        try {
            super.init(requestContext, jahiaCloudDumpMountPointFactory);
        } catch (RepositoryException ex) {
            LOGGER.error("Error retrieving mount point", ex);
        }
        requestContext.getFlowScope().put("jahiaCloudDumpFactory", jahiaCloudDumpMountPointFactory);
    }

    public String getFolderList() {
        final JSONObject result = new JSONObject();
        try {
            final JSONArray folders = JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> getSiteFolders(session.getWorkspace()));

            result.put("folders", folders);
        } catch (RepositoryException ex) {
            LOGGER.error("Error trying to retrieve local folders", ex);
        } catch (JSONException ex) {
            LOGGER.error("Error trying to construct JSON from local folders", ex);
        }

        return result.toString();
    }

    public Boolean save(MessageContext messageContext, RequestContext requestContext) {
        stateCode = "SUCCESS";
        final Locale locale = LocaleContextHolder.getLocale();

        try {
            final boolean available = super.save(jahiaCloudDumpMountPointFactory);
            if (available) {
                stateCode = "SUCCESS";
                messageKey = "serverSettings.jahiaCloudDumpMountPointFactory.save.success";
                requestContext.getConversationScope().put("adminURL", getAdminURL(requestContext));
                return Boolean.TRUE;
            } else {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(String.format("Mount point availability problem : %s, the mount point is created but unmounted", jahiaCloudDumpMountPointFactory.getName()));
                }
                stateCode = "WARNING";
                messageKey = "serverSettings.jahiaCloudDumpMountPointFactory.save.unavailable";
                requestContext.getConversationScope().put("adminURL", getAdminURL(requestContext));
                return Boolean.TRUE;
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error saving mount point : " + jahiaCloudDumpMountPointFactory.getName(), e);
            final MessageBuilder messageBuilder = new MessageBuilder().error().defaultText(Messages.get(BUNDLE, "serverSettings.jahiaCloudDumpMountPointFactory.save.error", locale));
            messageContext.addMessage(messageBuilder.build());
        }
        return Boolean.FALSE;
    }

    @Override
    public String getAdminURL(RequestContext requestContext) {
        final StringBuilder builder = new StringBuilder(super.getAdminURL(requestContext));
        if (stateCode != null && messageKey != null) {
            builder.append("?stateCode=").append(stateCode).append("&messageKey=").append(messageKey).append("&bundleSource=").append(BUNDLE);
        }
        return builder.toString();
    }
}
