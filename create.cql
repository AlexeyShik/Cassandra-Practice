create keyspace if not exists features
  with replication = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
  };

create table features.photo_data(
  photo_id bigint primary key,
  like_ctr double,
  quality double
);

insert into features.photo_data(photo_id, like_ctr, quality)
values (1, 0.3, 0.8);

select (photo_id, like_ctr, quality)
from features.photo_data;