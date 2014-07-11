CREATE DATABASE weibo_ads CHARACTER SET utf8 COLLATE utf8_bin;

mysql> alter table alarm_record add combineUuid varchar(32) after user;
mysql> alter table alarm_record add dataSize int(12) after combineUuid;

mysql> alter table base_station add lastDownVideo datetime null after lastUpdate;

