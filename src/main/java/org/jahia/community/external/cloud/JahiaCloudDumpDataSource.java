package org.jahia.community.external.cloud;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.*;
import org.apache.jackrabbit.core.fs.FileSystem;
import org.apache.jackrabbit.util.ISO8601;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.*;

public class JahiaCloudDumpDataSource implements ExternalDataSource, ExternalDataSource.Writable, ExternalDataSource.CanLoadChildrenInBatch, ExternalDataSource.SupportPrivileges {

    private static final List<String> JCR_CONTENT_LIST = List.of(Constants.JCR_CONTENT);
    private static final Set<String> SUPPORTED_NODE_TYPES = new HashSet<>(Arrays.asList(Constants.JAHIANT_FILE, Constants.JAHIANT_FOLDER, Constants.JCR_CONTENT));
    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpDataSource.class);
    private static final String JCR_CONTENT_SUFFIX = FileSystem.SEPARATOR + Constants.JCR_CONTENT;
    private static final String UNKNOWN_FILE_TYPE = "Found non file or folder entry at path {}, maybe an alias. VFS file type: {}";
    private final String jahiaCloudDumpPath;
    private FileObject root;
    private String rootPath;
    private FileSystemManager manager;

    public JahiaCloudDumpDataSource(String jahiaCloudDumpPath) {
        this.jahiaCloudDumpPath = jahiaCloudDumpPath;
    }

    // Converts a raw filesystem relative path to a JCR-safe path by escaping each
    // segment individually. escapeNodePath() preserves ':' (valid in qualified names
    // like jcr:content) but filenames such as ISO timestamps contain ':' which is
    // illegal in unqualified JCR node names.
    private static String toJcrPath(String filesystemRelativePath) {
        if (filesystemRelativePath == null || filesystemRelativePath.isEmpty()) {
            return FileSystem.SEPARATOR;
        }
        String[] segments = filesystemRelativePath.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                sb.append(FileSystem.SEPARATOR).append(Escaping.escapeIllegalJcrChars(segment));
            }
        }
        return sb.length() == 0 ? FileSystem.SEPARATOR : sb.toString();
    }

    public void setRoot() {
        try {
            manager = VFS.getManager();
            root = manager.resolveFile(jahiaCloudDumpPath);
            rootPath = root.getName().getPath();
        } catch (FileSystemException ex) {
            throw new IllegalStateException("Cannot set root to " + jahiaCloudDumpPath, ex);
        }
    }

    protected FileObject getRoot() {
        return root;
    }

    protected String getRootPath() {
        return rootPath;
    }

    protected FileSystemManager getManager() {
        return manager;
    }

    @Override
    public boolean isSupportsUuid() {
        return false;
    }

    @Override
    public boolean isSupportsHierarchicalIdentifiers() {
        return true;
    }

    @Override
    public boolean itemExists(String path) {
        try {
            final FileObject file = getFile(path.endsWith(JCR_CONTENT_SUFFIX) ? StringUtils.substringBeforeLast(
                    path, JCR_CONTENT_SUFFIX) : path);
            return file.exists();
        } catch (FileSystemException e) {
            LOGGER.warn("Unable to check file existence for path " + path, e);
        }
        return false;
    }

    @Override
    public void order(String path, List<String> children) throws RepositoryException {
        // ordering is not supported in VFS
    }

    @Override
    public Set<String> getSupportedNodeTypes() {
        return Set.copyOf(SUPPORTED_NODE_TYPES);
    }

    @Override
    public ExternalData getItemByIdentifier(String identifier) throws ItemNotFoundException {
        if (identifier.startsWith(FileSystem.SEPARATOR)) {
            try {
                return getItemByPath(identifier);
            } catch (PathNotFoundException e) {
                throw new ItemNotFoundException(identifier, e);
            }
        }
        throw new ItemNotFoundException(identifier);
    }

    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        try {
            String unescapedPath = Escaping.unescapeIllegalJcrChars(path);
            if (path.endsWith(JCR_CONTENT_SUFFIX)) {
                FileObject fileObject = getFile(StringUtils.substringBeforeLast(unescapedPath, JCR_CONTENT_SUFFIX));
                if (!fileObject.exists() || fileObject.getType() == FileType.FOLDER) {
                    throw new PathNotFoundException(path);
                }
                return getFileContent(fileObject.getContent());
            } else {
                FileObject fileObject = getFile(unescapedPath);
                if (!fileObject.exists()) {
                    throw new PathNotFoundException(path);
                }
                return getFile(fileObject);
            }
        } catch (FileSystemException ex) {
            throw new PathNotFoundException("File system exception while trying to retrieve " + path, ex);
        }
    }

    public FileObject getFile(String path) throws FileSystemException {
        if (path == null || path.isEmpty() || FileSystem.SEPARATOR.equals(path)) {
            return root;
        }
        final FileObject resolved = root.resolveFile(path.charAt(0) == FileSystem.SEPARATOR_CHAR ? path.substring(1) : path);
        final String resolvedPath = resolved.getName().getPath();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new FileSystemException("Path escapes configured root: " + path);
        }
        return resolved;
    }

    @Override
    public List<String> getChildren(String path) throws RepositoryException {
        try {
            if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                final FileObject fileObject = getFile(Escaping.unescapeIllegalJcrChars(path));
                return getChildNames(path, fileObject);
            }
        } catch (FileSystemException e) {
            LOGGER.error("Cannot get node children", e);
        }
        return Collections.emptyList();
    }

    private List<String> getChildNames(String path, FileObject fileObject) throws FileSystemException {
        if (fileObject.getType() == null) {
            warnOrThrowNotFound(path, fileObject);
            return Collections.emptyList();
        }
        switch (fileObject.getType()) {
            case FILE:
                return new ArrayList<>(JCR_CONTENT_LIST);
            case FOLDER:
                return listFolderChildNames(fileObject);
            default:
                warnOrThrowNotFound(path, fileObject);
                return Collections.emptyList();
        }
    }

    private List<String> listFolderChildNames(FileObject folder) throws FileSystemException {
        final FileObject[] files = folder.getChildren();
        if (files.length == 0) {
            return Collections.emptyList();
        }
        final List<String> children = new LinkedList<>();
        for (FileObject object : files) {
            if (getSupportedNodeTypes().contains(getDataType(object))) {
                children.add(Escaping.escapeIllegalJcrChars(object.getName().getBaseName()));
            }
        }
        return children;
    }

    @Override
    public List<ExternalData> getChildrenNodes(String path) throws RepositoryException {
        try {
            if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                final FileObject fileObject = getFile(Escaping.unescapeIllegalJcrChars(path));
                return getChildExternalData(path, fileObject);
            }
        } catch (FileSystemException e) {
            LOGGER.error("Cannot get node children", e);
        }
        return Collections.emptyList();
    }

    private List<ExternalData> getChildExternalData(String path, FileObject fileObject) throws FileSystemException {
        if (fileObject.getType() == null) {
            warnOrThrowNotFound(path, fileObject);
            return Collections.emptyList();
        }
        switch (fileObject.getType()) {
            case FILE:
                return Collections.singletonList(getFileContent(fileObject.getContent()));
            case FOLDER:
                return listFolderChildren(fileObject);
            default:
                warnOrThrowNotFound(path, fileObject);
                return Collections.emptyList();
        }
    }

    private List<ExternalData> listFolderChildren(FileObject folder) throws FileSystemException {
        // refresh because folder contents may change externally
        folder.refresh();
        final FileObject[] files = folder.getChildren();
        if (files.length == 0) {
            return Collections.emptyList();
        }
        final List<ExternalData> children = new LinkedList<>();
        for (FileObject object : files) {
            if (getSupportedNodeTypes().contains(getDataType(object))) {
                children.add(getFile(object));
                if (object.getType() == FileType.FILE) {
                    children.add(getFileContent(object.getContent()));
                }
            }
        }
        return children;
    }

    private void warnOrThrowNotFound(String path, FileObject fileObject) throws FileSystemException {
        if (fileObject.exists()) {
            LOGGER.warn(UNKNOWN_FILE_TYPE, fileObject, fileObject.getType());
        } else {
            throw new FileSystemException(path);
        }
    }

    @Override
    public void removeItemByPath(String path) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveItem(ExternalData data) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(String oldPath, String newPath) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getPrivilegesNames(String username, String path) {
        String[] privileges = new String[0];
        try {
            if (JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE).getNode("/").hasPermission("admin")) {
                privileges = new String[1];
                privileges[0] = Constants.JCR_READ_RIGHTS + "_" + Constants.EDIT_WORKSPACE;
            }
        } catch (RepositoryException ex) {
            LOGGER.error("Cannot get node privileges", ex);
        }
        return privileges;
    }

    private ExternalData getFile(FileObject fileObject) throws FileSystemException {
        final String type = getDataType(fileObject);

        final Map<String, String[]> properties = new HashMap<>();
        final List<String> addedMixins = new ArrayList<>();
        final FileContent content = fileObject.getContent();
        if (content != null) {
            final long lastModifiedTime = content.getLastModifiedTime();
            if (lastModifiedTime > 0) {
                final Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(lastModifiedTime);
                final String[] timestamp = new String[]{ISO8601.format(calendar)};
                properties.put(Constants.JCR_CREATED, timestamp);
                properties.put(Constants.JCR_LASTMODIFIED, timestamp);
            }
            if (content.getContentInfo() != null && content.getContentInfo().getContentType() != null
                    && content.getContentInfo().getContentType().matches("image/(.*)")) {
                addedMixins.add(Constants.JAHIAMIX_IMAGE);
            }
        }

        String path = toJcrPath(fileObject.getName().getPath().substring(rootPath.length()));
        if (!path.startsWith(FileSystem.SEPARATOR)) {
            path = FileSystem.SEPARATOR + path;
        }

        final ExternalData result = new ExternalData(path, path, type, properties);
        result.setMixin(addedMixins);
        return result;
    }

    public String getDataType(FileObject fileObject) throws FileSystemException {
        return fileObject.getType() == FileType.FILE ? Constants.JAHIANT_FILE
                : Constants.JAHIANT_FOLDER;
    }

    protected ExternalData getFileContent(final FileContent content) throws FileSystemException {
        final Map<String, String[]> properties = new HashMap<>(1);

        properties.put(Constants.JCR_MIMETYPE, new String[]{getContentType(content)});

        final String path = toJcrPath(content.getFile().getName().getPath().substring(rootPath.length()));
        final String jcrContentPath = path + FileSystem.SEPARATOR + Constants.JCR_CONTENT;
        final ExternalData externalData = new ExternalData(jcrContentPath, jcrContentPath, Constants.JAHIANT_RESOURCE, properties);

        final Map<String, Binary[]> binaryProperties = new HashMap<>(1);
        binaryProperties.put(Constants.JCR_DATA, new Binary[]{new JahiaCloudDumpBinaryImpl(content)});
        externalData.setBinaryProperties(binaryProperties);

        return externalData;
    }

    protected String getContentType(FileContent content) throws FileSystemException {
        String s1 = content.getContentInfo().getContentType();
        if (s1 == null) {
            s1 = JCRContentUtils.getMimeType(content.getFile().getName().getBaseName());
        }
        if (s1 == null) {
            s1 = "application/octet-stream";
        }
        return s1;
    }
}
