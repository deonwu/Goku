#

rrdtool create route_status.rrd \
 --start 920804400 \
 DS:requestCmd:GAUGE:600:U:U \
 RRA:AVERAGE:0.5:1:24 \
 RRA:AVERAGE:0.5:6:10 \
 RRA:LAST:0.5:1:10

rrdtool update route_status.rrd -t requestCmd N:1000000:2000000:3000000
rrdtool update route_status.rrd -t requestCmd 1292798400:12345 1292798700:12357 1292799900:12363

rrdtool update route_status.rrd -t requestCmd 1292901912:2000000

rrdtool update route_status.rrd -t requestCmd 1292902800:3000000


rrdtool graph speed.png  --start 1292895600 --end 1292902800 \
 DEF:myspeed=route_status.rrd:requestCmd:AVERAGE \
 DEF:myspeed2=route_status.rrd:requestCmd:LAST \
 LINE2:myspeed#FF0000 \
 LINE2:myspeed2#FFFF00 

rrdtool graph speed.png  --start now-2h \
 DEF:myspeed=route_status.rrd:requestCmd:AVERAGE \
 DEF:myspeed2=route_status.rrd:requestCmd:LAST \
 LINE2:myspeed#FF0000 \
 LINE2:myspeed2#FFFF00 


rrdtool fetch route_status.rrd AVERAGE --start 1292895600 --end 1292902512

rrdtool fetch route_status.rrd LAST --start 1292895600 --end 1292902512


rrdtool dump route_status.rrd

rrdtool last route_status.rrd


#//
1292902800:requestCmd:3000000

