rm -rf ../ramanujan-native/native/build/
cp -r ../ramanujan-native/native/ ./native/
docker build -t prayog .
