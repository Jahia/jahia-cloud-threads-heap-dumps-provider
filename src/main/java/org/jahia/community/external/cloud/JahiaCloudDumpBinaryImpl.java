package org.jahia.community.external.cloud;

import java.io.IOException;
import java.io.InputStream;
import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JahiaCloudDumpBinaryImpl implements Binary {

    private static final Logger logger = LoggerFactory.getLogger(JahiaCloudDumpBinaryImpl.class);

    private FileContent fileContent;

    public JahiaCloudDumpBinaryImpl(FileContent fileContent) {
        super();
        this.fileContent = fileContent;
    }

    @Override
    public void dispose() {
        try {
            fileContent.close();
        } catch (FileSystemException ex) {
            logger.warn("Impossible to close file content", ex);
        }
    }

    @Override
    public long getSize() throws RepositoryException {
        try {
            return fileContent.getSize();
        } catch (FileSystemException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public InputStream getStream() throws RepositoryException {
        try {
            return fileContent.getInputStream();
        } catch (FileSystemException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public int read(byte[] b, long position) throws IOException, RepositoryException {

        int read = 0;
        try (final InputStream is = getStream()) {
            read = is.read(b, (int) position, b.length);
        }
        return read;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            JahiaCloudDumpBinaryImpl other = (JahiaCloudDumpBinaryImpl) obj;
            return this.fileContent == other.fileContent;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return fileContent.hashCode();
    }
}
