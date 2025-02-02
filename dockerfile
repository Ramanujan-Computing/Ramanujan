#Pull base ubuntu image
FROM ubuntu:16.04

# Install OpenJDK-8
RUN apt-get update && \
    apt-get install -y openjdk-8-jdk && \
    apt-get install -y ant && \
    apt-get clean;

# Fix certificate issues
RUN apt-get update && \
    apt-get install ca-certificates-java && \
    apt-get clean && \
    update-ca-certificates -f;

    
# Setup JAVA_HOME -- useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

WORKDIR /
COPY runJvmInDocker.sh runJvmInDocker.sh 
RUN chmod +x runJvmInDocker.sh

EXPOSE 8888:8888
EXPOSE 8890:8890
EXPOSE 5005:5005
EXPOSE 5006:5006

# orchestrator files
COPY orchestrator/rest/target/rest-1.0.2-SNAPSHOT-fat.jar or-rest-1.0-SNAPSHOT-fat.jar
COPY orchestrator/config/application-config-prod.json or-application-config.json
COPY orchestrator/resources/OrchestratorCloudStorageWrite.json OrchestratorCloudStorageWrite.json
COPY orchestrator/resources/MetricPusherCred.json MetricPusherCred.json

# middleware files
COPY middleware/rest/target/rest-1.0-SNAPSHOT-fat.jar mi-rest-1.0-SNAPSHOT-fat.jar
COPY middleware/config/application-config-prod.json mi-application-config.json
COPY middleware/resources/MiddlewareCloudStorageWrite.json MiddlewareCloudStorageWrite.json

# run the shell file invoking two JVMs
CMD ["/bin/sh", "-c", "./runJvmInDocker.sh"]
