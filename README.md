# Jahia Cloud — Threads & Heap Dumps Provider

Provides read-only JCR access to the thread dumps and heap dumps generated automatically on Jahia Cloud, stored at `/var/tmp/cloud`.

## Requirements

- Jahia 8.1.0+
- `graphql-dxm-provider` module

## Architecture

This module uses OSGi Declarative Services (no Spring XML). A single `JahiaCloudDumpMountPointService` component implements both the mount lifecycle and `ManagedService`, so OSGi ConfigAdmin calls `updated()` directly on the class that owns `remount()`.

The filesystem source path (`/var/tmp/cloud`) is **hardcoded** — only the JCR mount path is configurable.

## Configuration

Default OSGi CM configuration (`org.jahia.community.cloudDumpProvider.cfg`):

```properties
mountPath=/sites/systemsite/files/cloud-dumps
```

The mount path can be changed via the admin UI or by editing the `.cfg` file.

## Admin UI

Navigate to **Administration → Cloud Dump Provider → Configuration** to view the hardcoded dump path and update the JCR mount path.

## GraphQL API

**Query:**
```graphql
query {
    cloudDumpSettings {
        dumpPath    # hardcoded: /var/tmp/cloud
        mountPath   # current JCR mount path
    }
}
```

**Mutation:**
```graphql
mutation {
    cloudDumpSaveSettings(mountPath: "/sites/systemsite/files/cloud-dumps")
}
```
