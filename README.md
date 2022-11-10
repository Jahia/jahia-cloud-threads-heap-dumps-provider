# Jahia Cloud - Thread and heap dumps provider
This community module (not supported by Jahia) is meant to give access to the threads and heap dump being generated automatically on Jahia Cloud

### MINIMAL REQUIREMENTS
* DX 8.1.0.0

### INSTALLATION
- Download the jar and deploy it on your instance
- Go to Administrat -> Server settings -> System components -> Mount points
- Add a new mount point of the type **Jahia Cloud - Threads and heap dumps mount point**
  - Give a name to the mount point
  - Specify where to mount it

### HOW TO USE
 -  Open the repository explorer and go the mount point
 - **Note: ** for security reasons, do not forget to filter the access to this mount point thanks to the Jahia permissions
