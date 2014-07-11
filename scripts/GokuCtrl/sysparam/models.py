# -*- coding: utf-8 -*-
from django.db import models

class AlarmDefine(models.Model):
    class Meta:
        db_table = 'alarm_code_list'
        verbose_name_plural = '告警编码列表'
        verbose_name = '告警编码'
        #app_label = 'sysparam'                 
        
    alarmCode = models.CharField(max_length=32, primary_key=True,
                                 #editable=False, 
                                 verbose_name="告警编码",
                                 help_text='告警编码，由系统定义不能修改。'
                                 )
    alarmName = models.CharField(max_length=50, verbose_name="告警名称",
                                 help_text='告警的显示名称。'
                                 )
    alarmLevel = models.CharField(max_length=10, verbose_name="告警级别",
                                  help_text='1-5, 数值越大，告警越严重。',
                                  )
    alarmCategory = models.CharField(max_length=4,
                                     choices=(('1', "视频"),
                                              ('2', "图片"),
                                              ('3', "无视频/图片"),
                                     ),                     
                                     default='',
                                     verbose_name="告警类型"
                                     )
    alarmStatus = models.CharField(max_length=4,
                                     choices=(('1', "全局告警"),
                                              ('2', "策略告警"),
                                              ('3', "禁用告警"),
                                     ),
                                     default='2',
                                     verbose_name="告警类型",
                                     help_text='全局告警-所有设备都启用; 策略告警-需要设备定义后生效; 禁用告警-在所有设备中禁用.'
                                     )    
    reActiveTime = models.IntegerField(default=5, verbose_name="间隔时间",
                                       help_text='告警重复激活的最短时间，单位：分钟. 间隔内的告警将忽略。')
    
    alarmDesc = models.TextField(verbose_name="告警描述", blank=True)
    
    
    def __unicode__(self):
        #return "%s<%s>" % (self.alarmName, self.alarmCode)
        return "%s" % (self.alarmName, )

class BTSCategory(models.Model):
    class Meta:
        db_table = 'bts_category_code'
        verbose_name_plural = '端局类型编码表'
        verbose_name = '端局类型编码'
        
    uuid = models.CharField(max_length=20, primary_key=True, verbose_name="类型编码")
    name = models.CharField(max_length=50, 
                                verbose_name='类型名称',
                                default='')
    def __unicode__(self):
        return "%s<%s>" % (self.name, self.uuid)
    