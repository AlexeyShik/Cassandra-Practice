### source
https://github.com/apache/cassandra/tree/cassandra-4.1

### docker
https://github.com/docker-library/cassandra/tree/master/4.1

### setup idea
ant 1.10, java 11    
https://cassandra.apache.org/_/development/ide.html  
`ant generate-idea-files -Duse.jdk11=true`

### setup custom image
- Dockerfile
- env-file
- cassandra.yaml
- cassandra-rackdc.properties
- cassandra-topology.properties.example
- create-docker.sh
- docker-compose.yaml

### build custom image
`sh ./create-docker.sh`

### check new image
`docker images | grep cassandra-db`

### create network
`docker network create --subnet=172.22.0.0/16 photo-features-cdb-cluster-network`

### up docker
`docker-compose -p photo-features-cdb --env-file env-file  up -d`

### connect to container
`docker exec -it 1.prod.photo-features-cdb.dc /bin/bash`

### check cluster status
`nodetool status`  
Должен написать UN, то есть Up-Normal состояние каждого узла  
`nodetool ring`  
Покажет vnodes из consistent hashing  
`nodetool info`  
Подробная информация про узел  

### connect to CQL shell
`cqlsh`

### create keyspace
```
create keyspace if not exists features
  with replication = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
  };
```

### list keyspaces
`desc keyspaces;`

### choose keyspace for session
`use features;`

### create table
```
create table features.photo_data(
  photo_id bigint primary key,
  like_ctr double,
  quality double
);
```

### list tables
`desc tables`

### insert data
```
insert into features.photo_data(photo_id, like_ctr, quality)
values (1, 0.3, 0.8);

insert into features.photo_data(photo_id, like_ctr, quality) values (2, 0.2, 0.5);
insert into features.photo_data(photo_id, like_ctr, quality) values (3, 0, 0.5);
insert into features.photo_data(photo_id, like_ctr, quality) values (8, 0.2, 0.2);
```

### select data
```
select (photo_id, like_ctr, quality)
from features.photo_data;

select (photo_id, like_ctr, quality)
from features.photo_data
where photo_id = 7;
```

### allow filtering
```
select (photo_id, like_ctr, quality) 
from features.photo_data 
where like_ctr > 0.2;

select (photo_id, like_ctr, quality) 
from features.photo_data 
where like_ctr > 0.2 
allow filtering;
```
В данном случае allow filtering - плохо, потому что запрос обходит все партиции, соответственно, делает запрос на все инстансы и читает с диска все данные.

### Tracing
`tracing on`  
Показать трейс запроса с consistency one / all на select / update

### Consistency level
`consistency;`
- По умолчанию ONE - запрос на текущий узел  
- LOCAL_QUORUM - собирает кворум в датацентре текущего узла  
- QUORUM - собирает кворум по всем узлам  
- EACH_QUORUM - собирает кворум в каждом датацентре
- ALL - запрос на все узлы кластера  

`consistency local_quorum;`

### Почему в volume{1,2,3}/data/features пусто?
`nodetool flush`

### Что попадает на диск после flush
- CompressionInfo.db - информация для разжатия данных
- Data.db - sstable
- Digest.crc32 - контрольная сумма файла Data.db
- Filter.db - фильтр Блума
- Index.db - ключи и оффсеты на данные по этим ключам в Data.db файле
- Statistics.db - метаданные: число записей, число колонок, типы колонок
- Summary.db - (1/128 часть) ключей и оффсетов по ключам в Index.db, загружается в оперативку
- TOC.txt - список файлов для данного sstable

### Если инстанс остановился, hints
По gossip другие ноды узнают об этом  
В `nodetool status` меняется UN на DN  
Запрос с consistency level all фейлится, так как только на 2 репликах диск в норме  
C local_quorum запись проходит на 1 и 3 узел  
В volume1/hints появляются указания, что на 2 узел надо дослать запись  
Когда узел 2 поднимается, на него отправляют пропущенную запись  
"Finished hinted handoff of file <name>.hints to endpoint /172.22.1.2:7000"

### Про табличку consistent hashing
При поднятии инстанса происходит его join к кластеру, для этого инстансы обмениваются vnodes числами, чтобы определить отрезки, кто за какой отвечает.

### Логи кассандры
`cd /opt/cassandra/logs`

### Если инстанс остановился надолго и хинты не помогли, read-repair
По умолчанию хинты собираются 3 часа после остановки инстанса и не более MB  
Удаляю папку с хинтами  
Делаю запрос на чтение новых данных с consistency all  
После выполнения запроса на узел 2 производится запись актуальных данных  
"Sending read-repair-mutation to Full(/172.22.1.2:7000,(1937683495815773612,2337896811240953910])"  
"Sending READ_REPAIR_REQ message to /172.22.1.2:7000"  
При этом read-repair починил только неконсистентнось в запрашиваемых данных  

### repair
`nodetool repair --full`  
В system.log будут подробные логи  
"Requesting merkle trees for photo_data (to \[/172.22.1.2:7000, /172.22.1.3:7000, /172.2
2.1.1:7000\]" -- запрос merkle tree всех нод  
"Endpoints /172.22.1.1:7000 and /172.22.1.2:7000 have 4 range(s) out of sync for photo_data" -- обнаружено несовпадение в деревьях  
"Performing streaming repair of 4 ranges with /172.22.1.2:7000" -- на 2 инстанс отправлены все актуальные данные    
Можно сделать select * и проверить, что не будет read-repair, так как все данные уже актуальные
