package org.jahia.community.external.cloud;

import org.jahia.api.Constants;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.ExternalProviderInitializerService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRStoreService;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;

import static org.osgi.framework.Constants.SERVICE_PID;

@Component(
        service = {JahiaCloudDumpMountPointService.class, ManagedService.class},
        immediate = true,
        property = SERVICE_PID + "=org.jahia.community.cloudDumpProvider"
)
public class JahiaCloudDumpMountPointService implements ManagedService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpMountPointService.class);
    private static final List<String> EXTENDABLE_TYPES = Arrays.asList(Constants.JAHIANT_FILE, Constants.JAHIANT_FOLDER);
    private static final List<String> OVERRIDABLE_ITEMS = Collections.singletonList("*.*");
    private static final String PROVIDER_KEY = "cloud-dumps";

    @SuppressWarnings("java:S1075")
    public static final String DUMP_PATH = "/var/tmp/cloud";

    @SuppressWarnings("java:S1075")
    public static final String DEFAULT_MOUNT_PATH = "/sites/systemsite/files/cloud-dumps";

    private volatile String mountPath = DEFAULT_MOUNT_PATH;
    private ExternalContentStoreProvider cloudDumpProvider;

    private JahiaUserManagerService userManagerService;
    private JahiaGroupManagerService groupManagerService;
    private JahiaSitesService sitesService;
    private JCRStoreService jcrStoreService;
    private JCRSessionFactory sessionFactory;
    private ExternalProviderInitializerService externalProviderInitializerService;

    @Reference
    public void setUserManagerService(JahiaUserManagerService userManagerService) {
        this.userManagerService = userManagerService;
    }

    @Reference
    public void setGroupManagerService(JahiaGroupManagerService groupManagerService) {
        this.groupManagerService = groupManagerService;
    }

    @Reference
    public void setSitesService(JahiaSitesService sitesService) {
        this.sitesService = sitesService;
    }

    @Reference
    public void setJcrStoreService(JCRStoreService jcrStoreService) {
        this.jcrStoreService = jcrStoreService;
    }

    @Reference
    public void setSessionFactory(JCRSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Reference
    public void setExternalProviderInitializerService(ExternalProviderInitializerService externalProviderInitializerService) {
        this.externalProviderInitializerService = externalProviderInitializerService;
    }

    // CM calls updated() immediately after activation with stored config (or null).
    // No need to call start() from @Activate — updated() is guaranteed to fire first.
    @Activate
    private void activate() {
        LOGGER.info("Cloud dump provider component activated");
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        if (properties != null) {
            final String mp = (String) properties.get("mountPath");
            mountPath = (mp != null && !mp.isEmpty()) ? mp : DEFAULT_MOUNT_PATH;
        } else {
            mountPath = DEFAULT_MOUNT_PATH;
        }
        LOGGER.info("Cloud dump provider config updated, mounting at {}", mountPath);
        remount();
    }

    public String getMountPath() {
        return mountPath;
    }

    private synchronized void remount() {
        stopProvider();
        try {
            final JahiaCloudDumpDataSource dataSource = new JahiaCloudDumpDataSource(DUMP_PATH);
            dataSource.setRoot();

            cloudDumpProvider = new ExternalContentStoreProvider();
            cloudDumpProvider.setUserManagerService(userManagerService);
            cloudDumpProvider.setGroupManagerService(groupManagerService);
            cloudDumpProvider.setSitesService(sitesService);
            cloudDumpProvider.setService(jcrStoreService);
            cloudDumpProvider.setSessionFactory(sessionFactory);
            cloudDumpProvider.setExternalProviderInitializerService(externalProviderInitializerService);
            cloudDumpProvider.setDataSource(dataSource);
            cloudDumpProvider.setExtendableTypes(EXTENDABLE_TYPES);
            cloudDumpProvider.setOverridableItems(OVERRIDABLE_ITEMS);
            cloudDumpProvider.setDynamicallyMounted(false);
            cloudDumpProvider.setMountPoint(mountPath);
            cloudDumpProvider.setKey(PROVIDER_KEY);
            cloudDumpProvider.start();
            LOGGER.info("Cloud dump provider started at {}", mountPath);
        } catch (JahiaInitializationException e) {
            LOGGER.error("Cannot start cloud dump provider", e);
        }
    }

    @Deactivate
    private void stopProvider() {
        if (cloudDumpProvider != null) {
            LOGGER.info("Stopping cloud dump provider");
            cloudDumpProvider.stop();
            cloudDumpProvider = null;
        }
    }
}
