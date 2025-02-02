

java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 or-rest-1.0-SNAPSHOT-fat.jar -conf or-application-config.json &

java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 mi-rest-1.0-SNAPSHOT-fat.jar -conf mi-application-config.json &

wait