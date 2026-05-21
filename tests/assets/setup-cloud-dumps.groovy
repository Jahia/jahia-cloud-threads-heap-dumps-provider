new File("/var/tmp/cloud/heap").mkdirs()
new File("/var/tmp/cloud/thread").mkdirs()
new File("/var/tmp/cloud/heap/heapdump.hprof").createNewFile()
new File("/var/tmp/cloud/thread/thread_dump.txt").createNewFile()
// Folder whose name contains colons (ISO 8601 timestamp) — regression for SUPPORT-608
new File("/var/tmp/cloud/modulesdump/2026-01-22T14:15:28.106Z_cluster_restart").mkdirs()
