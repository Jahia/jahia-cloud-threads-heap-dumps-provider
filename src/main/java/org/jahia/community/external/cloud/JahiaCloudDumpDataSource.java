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
import java.io.File;
import java.io.IOException;
import java.util.*;

public class JahiaCloudDumpDataSource implements ExternalDataSource, ExternalDataSource.Writable, ExternalDataSource.CanLoadChildrenInBatch, ExternalDataSource.SupportPrivileges {

    private static final List<String> JCR_CONTENT_LIST = List.of(Constants.JCR_CONTENT);
    // SUPPORTED_NODE_TYPES is a static unmodifiable constant; getSupportedNodeTypes() returns
    // it directly rather than creating a defensive copy on every call.
    private static final Set<String> SUPPORTED_NODE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(Constants.JAHIANT_FILE, Constants.JAHIANT_FOLDER, Constants.JCR_CONTENT)));
    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaCloudDumpDataSource.class);
    private static final String JCR_CONTENT_SUFFIX = FileSystem.SEPARATOR + Constants.JCR_CONTENT;
    private static final String UNKNOWN_FILE_TYPE = "Found non file or folder entry at path {}, maybe an alias. VFS file type: {}";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private final String jahiaCloudDumpPath;
    private FileObject root;
    private String rootPath;
    // Canonical (symlink-resolved) form of rootPath, used for symlink containment check.
    private String canonicalRootPath;
    private FileSystemManager manager;

    public JahiaCloudDumpDataSource(String jahiaCloudDumpPath) {
        this.jahiaCloudDumpPath = jahiaCloudDumpPath;
    }

    // Converts a raw filesystem relative path to a JCR-safe path by escaping each
    // segment individually. escapeNodePath() preserves ':' (valid in qualified names
    // like jcr:content) but filenames such as ISO timestamps contain ':' which is
    // illegal in unqualified JCR node names.
    //
    // Escape/unescape coupling: all ExternalDataSource entry-points that receive a
    // JCR path (itemExists, getItemByPath, getChildren, getChildrenNodes) MUST call
    // Escaping.unescapeIllegalJcrChars() before passing the path to getFile(), so
    // the VFS layer always sees raw filesystem characters. Conversely, any path
    // returned to the JCR layer (e.g. in ExternalData or child name lists) MUST be
    // produced through toJcrPath() / Escaping.escapeIllegalJcrChars() so that
    // illegal characters are encoded as %-sequences.
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
            canonicalRootPath = resolveCanonicalPath(rootPath);
        } catch (FileSystemException ex) {
            throw new IllegalStateException("Cannot set root to " + jahiaCloudDumpPath, ex);
        }
    }

    // Resolves the OS-canonical (symlink-resolved) path, or null if it cannot be resolved
    // (e.g. the path does not exist yet) — in which case the symlink containment check is skipped.
    private static String resolveCanonicalPath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException ex) {
            LOGGER.warn("Cannot resolve canonical path for {}, symlink containment check will be skipped", path);
            return null;
        }
    }

    protected FileObject getRoot() {
        return root;
    }

    protected String getRootPath() {
        return rootPath;
    }

    // Exposed for testing the canonical-path containment check.
    protected String getCanonicalRootPath() {
        return canonicalRootPath;
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
        // (C1) Unescape JCR-encoded characters first, consistent with getItemByPath and
        // getChildren, so the containment check in getFile() sees the raw filesystem path.
        try {
            final String unescapedPath = Escaping.unescapeIllegalJcrChars(path);
            final String fsPath = unescapedPath.endsWith(JCR_CONTENT_SUFFIX)
                    ? StringUtils.substringBeforeLast(unescapedPath, JCR_CONTENT_SUFFIX)
                    : unescapedPath;
            final FileObject file = getFile(fsPath);
            return file.exists();
        } catch (FileSystemException e) {
            LOGGER.warn("Unable to check file existence for path {}", path, e);
        }
        return false;
    }

    @Override
    public void order(String path, List<String> children) throws RepositoryException {
        // ordering is not supported in VFS
    }

    @Override
    public Set<String> getSupportedNodeTypes() {
        // (M5) SUPPORTED_NODE_TYPES is already an unmodifiable constant; return it directly
        // rather than creating a defensive copy on every call.
        return SUPPORTED_NODE_TYPES;
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
        if (!isWithinRoot(resolved.getName().getPath())) {
            throw new FileSystemException("Path escapes configured root: " + path);
        }
        // (C3) Symlink containment: also verify the OS-level canonical path so that a symlink
        // planted inside the root cannot point outside it. Only applied when the file actually
        // exists (non-existent paths have no real canonical target to check).
        if (canonicalRootPath != null) {
            final File backingFile = new File(resolved.getName().getPath());
            if (backingFile.exists()) {
                try {
                    final String canonicalResolved = backingFile.getCanonicalPath();
                    if (!isWithinCanonicalRoot(canonicalResolved)) {
                        throw new FileSystemException("Path escapes configured root via symlink: " + path);
                    }
                } catch (IOException ex) {
                    throw new FileSystemException("Cannot verify canonical path for: " + path, ex);
                }
            }
        }
        return resolved;
    }

    // Containment check that resists sibling-directory escapes: a bare
    // startsWith(rootPath) would wrongly accept "/var/tmp/cloud-evil" when the
    // root is "/var/tmp/cloud". Require an exact match or a separator boundary.
    private boolean isWithinRoot(String resolvedPath) {
        if (resolvedPath == null) {
            return false;
        }
        return resolvedPath.equals(rootPath)
                || resolvedPath.startsWith(rootPath + FileSystem.SEPARATOR);
    }

    // Secondary containment check on the OS-canonical (symlink-resolved) path.
    // Uses the same boundary logic as isWithinRoot to prevent sibling-prefix bypasses.
    private boolean isWithinCanonicalRoot(String canonicalResolved) {
        if (canonicalResolved == null || canonicalRootPath == null) {
            return false;
        }
        return canonicalResolved.equals(canonicalRootPath)
                || canonicalResolved.startsWith(canonicalRootPath + File.separator);
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
            if (SUPPORTED_NODE_TYPES.contains(getDataType(object))) {
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
            if (SUPPORTED_NODE_TYPES.contains(getDataType(object))) {
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

    /**
     * Returns the JCR read privileges for the current user over any mounted dump file.
     *
     * <p><b>All-or-nothing read model (INTENTIONAL / accepted for the Jahia-Cloud operator context).</b>
     * Read access is gated by a single {@code hasPermission("heapDumpsAdmin")} check on the JCR root
     * node {@code /}; the {@code path} argument is deliberately <b>ignored</b>. Any holder of
     * {@code heapDumpsAdmin} therefore reads the entire mounted dump tree, with no per-file, per-folder,
     * or per-site ACL. This is an accepted design decision, not an oversight: the dumps live at a
     * hardcoded internal cloud path ({@code /var/tmp/cloud}) and both the dumps and the operators who
     * hold {@code heapDumpsAdmin} are Jahia-Cloud operator-controlled. If finer-grained access is ever
     * required (e.g. exposing dumps to a broader or per-site audience), per-path privilege evaluation
     * would be the hardening path. Fails closed: any {@link RepositoryException} yields no privileges.
     *
     * <p>Behavior pinned by {@code GetPrivilegesNamesTest} (a characterization test documenting this
     * intentional current behavior).
     */
    @Override
    public String[] getPrivilegesNames(String username, String path) {
        String[] privileges = new String[0];
        try {
            // (Security — accepted risk) heapDumpsAdmin-on-"/" gate; the requested path is intentionally
            // ignored (server-wide, all-or-nothing read). See method Javadoc for the rationale.
            if (JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE).getNode("/").hasPermission("heapDumpsAdmin")) {
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

    // (H7) Returns the JCR node type corresponding to the VFS file type.
    // FILE -> jnt:file, FOLDER -> jnt:folder.
    // IMAGINARY (non-existent) and other types are not valid states at this point in the
    // call chain (callers check existence before calling getDataType); throw to surface the
    // inconsistency rather than silently mis-classifying non-existent paths as folders.
    public String getDataType(FileObject fileObject) throws FileSystemException {
        if (fileObject.getType() == FileType.FILE) {
            return Constants.JAHIANT_FILE;
        } else if (fileObject.getType() == FileType.FOLDER) {
            return Constants.JAHIANT_FOLDER;
        } else {
            // IMAGINARY or other unexpected VFS types
            throw new FileSystemException("Unexpected VFS file type " + fileObject.getType()
                    + " for path: " + fileObject.getName().getPath());
        }
    }

    protected ExternalData getFileContent(final FileContent content) throws FileSystemException {
        final Map<String, String[]> properties = new HashMap<>(1);

        properties.put(Constants.JCR_MIMETYPE, new String[]{getContentType(content)});

        final String path = toJcrPath(content.getFile().getName().getPath().substring(rootPath.length()));
        final String jcrContentPath = path + FileSystem.SEPARATOR + Constants.JCR_CONTENT;
        final ExternalData externalData = new ExternalData(jcrContentPath, jcrContentPath, Constants.JAHIANT_RESOURCE, properties);

        // NOTE (H4 — deferred): callers that receive this ExternalData are responsible for
        // calling Binary.dispose() on the JahiaCloudDumpBinaryImpl instance stored in
        // JCR_DATA to release the underlying VFS2 FileContent resource.
        final Map<String, Binary[]> binaryProperties = new HashMap<>(1);
        binaryProperties.put(Constants.JCR_DATA, new Binary[]{new JahiaCloudDumpBinaryImpl(content)});
        externalData.setBinaryProperties(binaryProperties);

        return externalData;
    }

    protected String getContentType(FileContent content) throws FileSystemException {
        String mimeType = content.getContentInfo() != null ? content.getContentInfo().getContentType() : null;
        if (mimeType == null) {
            mimeType = JCRContentUtils.getMimeType(content.getFile().getName().getBaseName());
        }
        if (mimeType == null) {
            mimeType = DEFAULT_MIME_TYPE;
        }
        return mimeType;
    }
}
