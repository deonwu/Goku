# -*- coding: utf-8 -*-
from django.db import models
import os

class StationGroupFields(models.ManyToManyField):
    def save_form_data(self, instance, groups):
        user_groups = StationGroupRelation.objects.filter(base_station=instance)
        user_group_names = [ e.user_group.name for e in user_groups]    
        for g in groups:
            if g.name not in user_group_names:
                StationGroupRelation(base_station=instance, user_group=g).save()
                
        new_groups_names = [ e.name for e in groups ]
        for g in user_groups:
            if g.user_group.name not in new_groups_names:
                g.delete()

# Create your models here.
class BaseStation(models.Model):
    class Meta:
        db_table = 'base_station'
        #verbose_name = '基站列表'
        verbose_name_plural = '基站列表'
        verbose_name = '基站'
            
    uuid = models.CharField(max_length=10, primary_key=True, verbose_name="基站编号",
                            help_text='基站唯一编号。'
                            )
    name = models.CharField(max_length=50, verbose_name="基站名称",
                            help_text='基站显示名称。'
                            )
    connectionStatus = models.CharField(max_length=10, 
                                        choices=(('connected', "连接成功"),
                                                 ('timeout', "连接超时"),
                                                 ('error', "登录错误"),
                                                 ('new', "新增"),
                                        ),
                                        default='new',
                                        verbose_name="连接状态", 
                                        )
    groupName = models.CharField(max_length=50, default='default', verbose_name="监控分组",
                                 help_text='用来自动分配视频管理服务器。默认不需要修改。'
                                 )
    routeServer = models.CharField(max_length=50, null=True, verbose_name="转发服务器", editable=False)
    locationId = models.CharField(max_length=50, default='', verbose_name="IP地址",
                                  help_text='例如：192.168.1.1:9001')
    alarmStatus = models.CharField(max_length=50, null=True, verbose_name="告警状态")
    channels = models.CharField(max_length=150, null=True, verbose_name="通道列表",
                                help_text='可用的通道列表和名称，例如： 1:通道1,2:通道2'
                                )
    
    devType = models.IntegerField(default=1,
                                  choices=((1, "视频"),
                                         (2, "图片"),
                                         ),
                                   verbose_name="设备类型"
                                  )
    
    lastActive = models.DateTimeField(null=True, verbose_name="最后活动时间", editable=False)
    lastUpdate = models.DateTimeField( auto_now=True, editable=False, verbose_name="最后更新时间")
    createDate = models.DateTimeField(auto_now_add=True, editable=False, verbose_name="创建时间")
    
    locationUUID = models.ForeignKey('Location', db_column='locationUUID', verbose_name="基站位置")
    btsCategory = models.ForeignKey('sysparam.BTSCategory', default='', db_column='btsCategory', verbose_name="端局类型")
    supportAlarm = models.CharField(max_length=150, default='', verbose_name="告警策略",
                                    help_text='基站实现的告警。'
                                    )
    
    user_groups = StationGroupFields("UserGroup", through='StationGroupRelation')
    #supportAlarm = models.ManyToManyField('sysparam.AlarmDefine', verbose_name="告警策略")
    
    def config(self):
        #from django.utils.safestring import mark_safe
        if ":" in self.locationId:
            xx = self.locationId.split(":")
        ip = xx[0]
        port = xx[1]
        if self.devType == 1:
            #output = """<a href="javascript:cfg('%s', %s);">参数配置</a>"""
            output = u"""<a href="javascript:cfg('%s', %s);">参数配置</a>""" % (ip, port)
        elif self.devType == 2:
            from java.lang import System
            if System.getProperty("STATIC_ROOT", None):
                output = u"""<a href="/GokuCtrl/img_config/%s;">参数配置</a>""" % (self.uuid)
            else:
                output = u"""<a href="/img_config/%s;">参数配置</a>""" % (self.uuid)
        return output
    config.allow_tags = True
    config.verbose_name = "设备配置"
    
    def _get_supportAlarms(self,):
        from GokuCtrl.sysparam.models import AlarmDefine
        return AlarmDefine.objects.filter(alarmCode__in = self.supportAlarm.split(","))
    
    @property
    def supportAlarms(self):
        self._get_supportAlarms()
    
    def __unicode__(self):
        return self.uuid        

class UserGroupFields(models.ManyToManyField):
    def save_form_data(self, instance, groups):
        user_groups = UserGroupRelation.objects.filter(user=instance)
        user_group_names = [ e.user_group.name for e in user_groups]    
        for g in groups:
            if g.name not in user_group_names:
                UserGroupRelation(user=instance, user_group=g).save()
                
        new_groups_names = [ e.name for e in groups ]
        for g in user_groups:
            if g.user_group.name not in new_groups_names:
                g.delete()
            
# Create your models here.
class User(models.Model):
    class Meta:
        db_table = 'user_account'
        verbose_name_plural = '用户列表'
        verbose_name = '用户'
            
    name = models.CharField(max_length=20, primary_key=True, verbose_name="登录名")
    password = models.CharField(max_length=50, 
                                default='', verbose_name="密码")
    display = models.CharField(max_length=50, default='', verbose_name="用户别名", blank=True)
    lastActive = models.DateTimeField('last active', null=True)
    
    status = models.CharField(max_length=10, 
                              choices=(('ok', "正常"),
                                       ('removed', "已删除"),
                                       ('locked', "锁定"),
                                       ),
                             default='ok',
                             verbose_name="用户状态")
    
    user_groups = UserGroupFields("UserGroup", through='UserGroupRelation')
    
    def user_group_names(self):
        user_groups = UserGroupRelation.objects.filter(user=self)
        return ",".join([e.user_group.name for e in user_groups ])

    def __unicode__(self):
        return self.name        

# Create your models here.
class UserGroup(models.Model):
    class Meta:
        db_table = 'user_group'
        verbose_name_plural = '用户组'
        verbose_name = '用户组'
            
    name = models.CharField(max_length=20, primary_key=True, verbose_name="用户组名")
    isAdmin = models.IntegerField(default=0,
                                  choices=((0, "一般用户组"),
                                           (1, "管理员组"),
                                          ),
                                  verbose_name="是否管理员",
                                  help_text='管理员分组，始终可以查看所有的基站。'
                                  )
    def __unicode__(self):
        if self.isAdmin == 1:
            return self.name + "<*>"
        else:
            return self.name
    
class UserGroupRelation(models.Model):
    class Meta:
        db_table = 'relation_user_group'
        unique_together = (("user", "user_group"),)
        verbose_name_plural = '用户组关系'
        verbose_name = '用户组组关系'

    user = models.ForeignKey(User, verbose_name="用户")
    user_group = models.ForeignKey(UserGroup, verbose_name="用户分组")

class StationGroupRelation(models.Model):
    class Meta:
        db_table = 'relation_station_group'
        unique_together = (("base_station", "user_group"), )
        verbose_name_plural = '基站用户组关系'
        verbose_name = '基站用户组关系'

    base_station = models.ForeignKey(BaseStation, verbose_name="基站")
    user_group = models.ForeignKey(UserGroup, verbose_name="用户组")

class AlarmRecord(models.Model):
    class Meta:
        db_table = 'alarm_record'
        verbose_name_plural = '告警列表'
        verbose_name = '告警'
        
    uuid = models.CharField(max_length=32, primary_key=True, verbose_name="告警ID")
    
    base_station = models.CharField(max_length=32, db_column='baseStation', verbose_name="基站编号")
    #base_station = models.ForeignKey(BaseStation, db_column='baseStation', verbose_name="基站")
    channelId = models.CharField(max_length=10, default='', verbose_name="告警通道")
    alarmCode = models.CharField(max_length=32, verbose_name="告警编码")
    #alarmCode = models.ForeignKey('sysparam.AlarmDefine', db_column='alarmCode',  verbose_name="告警名称", )
    
    alarmLevel = models.CharField(max_length=10, default='', verbose_name="告警级别")
    
    #1.视频 2.图片, 3.无 
    alarmCategory = models.CharField(max_length=10,
                                     choices=(('1', "视频"),
                                       ('2', "图片"),
                                       ('3', "无视频/图片"),
                                       ('4', "副图片"),
                                       ),                     
                                     default='1',
                                     verbose_name="视频/图片"
                                     )
    
    alarmStatus = models.CharField(max_length=10, 
                                   choices=(('1', "未处理"),
                                            ('2', "告警超时自动处理"),
                                            ('3', "手动确认"),
                                            ('4', "无效告警"),
                                       ),                                   
                                   default='1',
                                   verbose_name="告警状态")
    combineUuid = models.CharField(max_length=32, verbose_name="主图片ID",
                                   help_text='只用于图片告警.',
                                   blank=True,
                                   null=True
                                   )
    dataSize = models.IntegerField(max_length=12, verbose_name="图片大小",
                                   help_text='图片的长宽规格，由基站协议定义。',
                                   default=0,
                                   blank=True)
    
    user = models.CharField(max_length=20, default='', null=True, verbose_name="确认人员")
    videoPath = models.CharField(max_length=1024, default='', blank=True, null=True, verbose_name="保存路径")
    startTime = models.DateTimeField(verbose_name='开始时间', null=True)
    endTime = models.DateTimeField(verbose_name='结束时间', null=True)
    lastUpdateTime = models.DateTimeField(verbose_name='最后更新时间', auto_now=True, editable=False, null=True)
    comfirmTime = models.DateTimeField(verbose_name='手动确认时间', null=True)
    
    def __unicode__(self):
        return "%s-%s" % (self.base_station, self.alarmCode)
    
    def alarmName(self):
        if not hasattr(AlarmRecord, '_name'):
            import GokuCtrl.sysparam.models as a
            _name = {}
            for e in a.AlarmDefine.objects.all():
                _name[e.alarmCode] = e.alarmName
            AlarmRecord._name = _name
        return AlarmRecord._name.get(self.alarmCode) or self.alarmCode
    
    def download(self, ):
        path = os.environ.get("DATA_ROOT", ".")
        if self.alarmCategory == '1':
            return u"<a href='/?q=replay&id=%s'>下载视频</a>" % (self.uuid,)
        elif self.alarmCategory == '2':
            return u"<a target='_blank' href='/?q=image_alarm&id=%s'>浏览图片</a>" % self.uuid
        elif self.alarmCategory == '4':
            path_link = u"<a target='_blank' href='/?q=img&id=%s'>单张</a>" % self.uuid
            path_link += u"&nbsp;<a target='_blank' href='/?q=image_alarm&id=%s'>浏览</a>" % self.combineUuid
            return path_link
        elif self.videoPath:
            return self.videoPath
    download.allow_tags = True
    download.verbose_name = "文件下载"
    alarmName.verbose_name = "告警类型"    
                
class SystemLog(models.Model):
    class Meta:
        db_table = 'goku_system_log'
        verbose_name_plural = '系统日志'
        verbose_name = '系统日志'
        
    uuid = models.CharField(max_length=32, primary_key=True)

    actionOwner = models.CharField(max_length=50, default='', verbose_name="发起对象")
    actionObject = models.CharField(max_length=50, default='', verbose_name="被操作对象" )
    actionType = models.CharField(max_length=50, default='', verbose_name="动作类型")

    description = models.CharField(max_length=1024, null=True, verbose_name="描述")
    createDate = models.DateTimeField(null=True, verbose_name="发生日期")
       
    def __unicode__(self):
        return "%s-%s" % (self.actionOwner, self.actionType)    
    
    
class Location(models.Model):
    class Meta:
        db_table = 'location'
        verbose_name_plural = '区域管理'
        verbose_name = '区域'
        
    uuid = models.CharField(max_length=32, primary_key=True, verbose_name="地点编码")
    name = models.CharField(max_length=50, default='', verbose_name="地点名称")
    parent = models.ForeignKey('Location', db_column='parent', blank=True, null=True, verbose_name="上级地名")

    def __unicode__(self):
        return "%s<%s>" % (self.name, self.uuid)
    
class VideoTask(models.Model):
    class Meta:
        db_table = 'video_task'
        verbose_name_plural = '监控计划任务'
        verbose_name = '计划任务'
        
    taskID = models.IntegerField(max_length=32, primary_key=True, verbose_name="计划ID")
    name = models.CharField(max_length=50, default='', verbose_name="计划名称")
    userName = models.CharField(max_length=50, default='', verbose_name="用户名")
    status = models.CharField(max_length=4, default='', verbose_name="计划状态",
                               choices=(('1', "运行状态"),
                                        ('2', "暂停运行"),
                                        ('9', "删除"),
                                   ),
                              )
    startDate = models.CharField(max_length=16, default='', verbose_name="开始日期",
                                 help_text='计划生效开始日期, 2010-10-11'
                                 )
    endDate = models.CharField(max_length=16, default='', verbose_name="结束日期",
                               help_text='结束日期, 2010-10-11'
                               )
    weekDays = models.CharField(max_length=20, default='', verbose_name="星期",
                                help_text='每周的星期几生效。例如：1,2,3,4'
                                )
    startTime = models.CharField(max_length=10, default='', verbose_name="开始时间",
                                 help_text='任务开始执行时间,精确到分， 格式：08:30'
                                 )
    endTime = models.CharField(max_length=10, default='', verbose_name="结束时间",
                                 help_text='任务结束的时间， 格式：09:30'
                               )
    uuid = models.CharField(max_length=12, default='', verbose_name="基站ID")
    channel = models.CharField(max_length=5, default='', verbose_name="视频通道")
    windowID = models.IntegerField(default=0, verbose_name="显示窗口")
    minShowTime = models.CharField(max_length=8, default='10', verbose_name="最短时间",
                                   help_text='当多个视频同时在一个窗口显示时，当前任务最少需要显示多长时间。单位：秒')
    showOrder = models.IntegerField(default=0, verbose_name="显示排序")
    

    def __unicode__(self):
        return "%s<%s>" % (self.name, self.uuid)

def update_system_reload_trigger(*args, **kw):
    syslog, c = SystemLog.objects.get_or_create(uuid='param_updated')
    syslog.actionOwner = 'system'
    syslog.actionObject = 'system'
    syslog.actionType = 'update'
    from datetime import datetime 
    syslog.createDate = datetime.now()
    syslog.save()

from django.db.models.signals import post_save
post_save.connect(update_system_reload_trigger, sender=BaseStation)
from GokuCtrl.sysparam.models import AlarmDefine
post_save.connect(update_system_reload_trigger, sender=AlarmDefine)
