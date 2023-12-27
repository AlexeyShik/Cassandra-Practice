#!/bin/bash

ant clean jar -Duse.jdk11=true
rm -rf cassandra-db
mkdir -p cassandra-db/lib
cp build/apache-cassandra-5.1-SNAPSHOT.jar cassandra-db/lib
cp -a build/lib/. cassandra-db/lib
cp -a lib/. cassandra-db/lib
cp -r conf cassandra-db
cp -r bin cassandra-db
cp -r pylib cassandra-db
cp -r tools cassandra-db
tar czf cassandra-db.tar.gz cassandra-db
docker build . -t cassandra-db:4.1-126
rm -rf cassandra-db
rm cassandra-db.tar.gz