# jahia-cloud-threads-heap-dumps-provider

Jahia OSGi module that mounts Jahia cloud dump files (`/var/tmp/cloud`) as a JCR virtual filesystem via an `ExternalContentStoreProvider`. Admin UI at `/jahia/administration/cloudDump`.

## Key Facts

- **artifactId**: `jahia-cloud-threads-heap-dumps-provider` (module key: `cloudDumpProvider`)
- **Java package**: `org.jahia.community.cloudDumpProvider`
- **jahia-depends**: `default,graphql-dxm-provider,serverSettings`
- **OSGi config PID**: `org.jahia.community.cloudDumpProvider`
- `dumpPath = "/var/tmp/cloud"` — **hardcoded**, not configurable
- `mountPath` — configurable via admin UI; default: `/sites/systemsite/files/cloud-dumps`

## Architecture

| Class | Role |
|-------|------|
| `JahiaCloudDumpMountPointService` | Merged `ManagedService` + mount lifecycle; handles `updated()`, `remount()`, `@Deactivate` |
| `JahiaCloudDumpDataSource` | `ExternalDataSource`; bridges VFS2 `FileObject` to `ExternalData` |
| `CloudDumpProviderGraphQLExtensionsProvider` | Registers GraphQL extensions |
| `CloudDumpProviderQueryExtension` | GraphQL settings query |
| `CloudDumpProviderMutationExtension` | GraphQL save mutation |

`JahiaCloudDumpMountPointService` creates `new ExternalContentStoreProvider()` (not Spring-injected), sets `dynamicallyMounted = false`, calls `start()` / `stop()` on mount/remount.

VFS2 root: `StandardFileSystemManager.resolveFile("file:/var/tmp/cloud")`.

## GraphQL API

| Operation | Name | Notes |
|-----------|------|-------|
| Query | `cloudDumpSettings` → `{mountPath, dumpPath}` | `dumpPath` is always `/var/tmp/cloud` |
| Mutation | `cloudDumpSaveSettings(mountPath!)` → Boolean | Writes config + triggers remount |

Both require `admin` permission.

## Admin UI

- **Route**: `administration-server-systemHealth:99` (direct, no parent group)
- **Admin path**: `/jahia/administration/cloudDump`
- **CSS prefix**: `cdp_`
- **Input id**: `#cdp-mount-path`
- **Features**: Save button, Ctrl+Enter shortcut (fires when field non-empty), Browse in jContent button

### Browse in jContent

Converts `/sites/{siteKey}/files{rest}` → `/jahia/jcontent/{siteKey}/en/media/files{rest}`.  
Button is disabled when mount path does not match the `/sites/*/files/*` pattern.  
URL is derived from component state (not Apollo cache) to avoid staleness after mutation.

## Build

```bash
mvn clean install
yarn build
yarn lint
```

## Tests (Cypress Docker)

```bash
cd tests
cp .env.example .env          # fill JAHIA_IMAGE, JAHIA_LICENSE
yarn install
./ci.build.sh && ./ci.startup.sh
```

- Tests: `tests/cypress/e2e/01-cloudDumpProvider.cy.ts`
- `tests/assets/provisioning.yml` runs `setup-cloud-dumps.groovy` inside Jahia to create `/var/tmp/cloud/heap/heapdump.hprof` and `/var/tmp/cloud/thread/thread_dump.txt`
- Cypress container also has `/var/tmp/cloud` structure (via `tests/Dockerfile`)
- Tests cover: GraphQL API, JCR file node existence, admin UI (save via button + Ctrl+Enter), Browse in jContent URL construction

## Gotchas

- `/var/tmp/cloud` must exist in the Jahia container **before** the module starts — otherwise `resolveFile` fails silently and the mount shows empty
- The provisioning Groovy script (`tests/assets/setup-cloud-dumps.groovy`) creates the directories and placeholder files inside Jahia's JVM at test startup
- `tests/Dockerfile` mirrors the same structure for the Cypress container (separate filesystem)
- Provisioning manifests (`provisioning-manifest-build.yml` / `provisioning-manifest-snapshot.yml`) use bare filenames (`include: 'provisioning.yml'`) — Jahia engine resolves to `assets/` automatically; do **not** add `assets/` prefix
- CSS Modules in Cypress: match with `[class*="cdp_..."]`
