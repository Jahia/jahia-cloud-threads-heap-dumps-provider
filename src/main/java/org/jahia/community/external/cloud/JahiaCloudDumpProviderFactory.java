package org.jahia.community.external.cloud;

import javax.jcr.RepositoryException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JahiaCloudDumpProviderFactory implements ProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpProviderFactory.class);
    private final String jahiaCloudDumpPath;

    public JahiaCloudDumpProviderFactory(String jahiaCloudDumpPath) {
        this.jahiaCloudDumpPath = jahiaCloudDumpPath;
    }

    @Override
    public String getNodeTypeName() {
        return "jnt:jahiaCloudDumpMountPoint";
    }

    @Override
    public JCRStoreProvider mountProvider(JCRNodeWrapper mountPoint) throws RepositoryException {
        final ExternalContentStoreProvider provider = (ExternalContentStoreProvider) SpringContextSingleton.getBean("ExternalStoreProviderPrototype");
        provider.setKey(mountPoint.getIdentifier());
        final boolean validVFSPoint = validateVFS(jahiaCloudDumpPath);
        provider.setMountPoint(mountPoint.getPath());

        final JahiaCloudDumpDataSource dataSource = new JahiaCloudDumpDataSource(jahiaCloudDumpPath);
        dataSource.setRoot();
        provider.setDataSource(dataSource);
        provider.setDynamicallyMounted(true);
        provider.setSessionFactory(JCRSessionFactory.getInstance());
        if (validVFSPoint) {
            try {
                provider.start();
            } catch (JahiaInitializationException ex) {
                throw new RepositoryException(ex);
            }
        } else {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(String.format("Error with the mount point with the root : %s", jahiaCloudDumpPath));
            }
        }

        return provider;

    }

    private boolean validateVFS(String jahiaCloudDumpPath) {
        try {
            final FileObject path = VFS.getManager().resolveFile(jahiaCloudDumpPath);
            return path.exists();
        } catch (FileSystemException ex) {
            LOGGER.warn(String.format("VFS mount point %s has validation problem", jahiaCloudDumpPath), ex);
            return false;
        }
    }
}
