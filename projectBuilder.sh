# Build dependencies in the order given in README.md

cd commons
mvn clean install

cd ../rule-engine
mvn clean install

cd ../ramanujan-device-common
mvn clean install

cd ../developer-console-model
mvn clean install

cd ../monitoring-utils2
mvn clean install

cd ../db-layer
mvn clean install

cd ../kafka-manager
mvn clean install

cd ../orchestrator
mvn clean install

cd ../middleware
mvn clean install

cd ../developer-console
mvn clean install

cd ../ramanujan-native/native
[ ! -d "build" ] && mkdir build
cd build
cmake ..
cmake --build .