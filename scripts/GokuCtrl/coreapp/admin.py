# -*- coding: utf-8 -*-
from django.contrib import admin
from models import *

from GokuCtrl.sysparam.models import AlarmDefine 

from django import forms

class MultipleAlarmCodeField(forms.ModelMultipleChoiceField):
    def clean(self, value):
        if self.required and not value:
            raise ValidationError(self.error_messages['required'])
        elif not self.required and not value:
            return []
        if not isinstance(value, (list, tuple)):
            raise ValidationError(self.error_messages['list'])
        return ",".join(value)
    
class SingleCheckboxSelectMultiple(forms.CheckboxSelectMultiple):
    def render(self, name, value, attrs=None, choices=()):
        from itertools import chain
        from django.utils.encoding import force_unicode
        from django.utils.safestring import mark_safe
        from django.utils.html import escape, conditional_escape
        if value is None: 
            value = []
        elif isinstance(value, basestring):     
            value = value.split(",") 
            
        has_id = attrs and 'id' in attrs
        final_attrs = self.build_attrs(attrs, name=name)
        output = [u'<div style="display:block;">']
        # Normalize to strings
        str_values = set([force_unicode(v) for v in value])
        for i, (option_value, option_label) in enumerate(chain(self.choices, choices)):
            i = i+1
            # If an ID attribute was given, add a numeric index as a suffix,
            # so that the checkboxes don't all have the same ID attribute.
            if has_id:
                final_attrs = dict(final_attrs, id='%s_%s' % (attrs['id'], i))
                label_for = u' for="%s"' % final_attrs['id']
            else:
                label_for = ''

            cb = forms.CheckboxInput(final_attrs, check_test=lambda value: value in str_values)
            option_value = force_unicode(option_value)
            rendered_cb = cb.render(name, option_value)
            option_label = conditional_escape(force_unicode(option_label))
            output.append(u'<label%s>%s %s</label>&nbsp;&nbsp;' % (label_for, rendered_cb, option_label))
        output.append(u'</div>')
        return mark_safe(u'\n'.join(output))            
        #return super(SingleCheckboxSelectMultiple, self).render(name, value, attrs, choices)

class BaseStationForm(forms.ModelForm):
    class Meta:
        model = BaseStation
        #forms.m
    supportAlarm = MultipleAlarmCodeField(label='告警列表', required=False,
                                          queryset= AlarmDefine.objects.filter(alarmStatus='2'), 
                                          widget=SingleCheckboxSelectMultiple)
    
    user_groups = forms.ModelMultipleChoiceField(label='监控用户组', required=False,
                                                 #基站只能和一般用户组关联。管理组只是用户属性。
                                                 queryset= UserGroup.objects.filter(isAdmin=0),
                                                 widget=forms.CheckboxSelectMultiple
                                                 )
    #forms.ModelForm.
    

class BaseStationAdmin(admin.ModelAdmin):
    fields = ['uuid', 'name', 'groupName', 'locationId', 'channels', 
              'devType', 'btsCategory', 'locationUUID', 'supportAlarm', 'user_groups']
    list_display = ('uuid', 'name', 'connectionStatus', 'locationUUID', 'routeServer', 'locationId', 
                    'alarmStatus', 'devType', 'btsCategory', 'lastActive', 'config')
    list_filter = ['devType', 'connectionStatus', 'alarmStatus', ]
    search_fields = ['uuid', 'locationId', ]
    form = BaseStationForm
        
    
class AlarmRecordAdmin(admin.ModelAdmin):
    fields = ['uuid', 'base_station', 'channelId', 'alarmCode', 'alarmStatus', 'startTime', 'alarmCategory', 'alarmLevel', 'combineUuid', 'dataSize', 'videoPath']
    list_display = ('uuid', 'base_station', 'channelId', 'alarmName', 'startTime', 'alarmCategory', 'alarmLevel', 'alarmStatus', 'user', 'download')
    #list_filter = ['alarmCode', ]
    #search_fields = ['base_station__uuid', 'alarmCode__alarmCode', ]
    search_fields = ['base_station', 'alarmCode', ]
    order_by = ("-startTime", "base_station", "channelId")
    where_sql = "alarmCategory != 4"
    
class UserAdminForm(forms.ModelForm):
    class Meta:
        model = User
        #forms.m
    user_groups = forms.ModelMultipleChoiceField(label='用户组', required=False,
                                                 queryset= UserGroup.objects.all(),
                                                 widget=forms.CheckboxSelectMultiple
                                                 )
class UserAdmin(admin.ModelAdmin):
    fields = ['name', 'display', 'password', 'status', 'user_groups']
    list_display = ('name', 'display', 'user_group_names', 'status', )
    search_fields = ['name', 'display', ]
    form = UserAdminForm

class UserGroupAdmin(admin.ModelAdmin):
    fields = ['name', 'isAdmin', ]
    list_display = ('name', 'isAdmin', )
    list_filter = ['name', ]              
    search_fields = ['name', ]
    
class SystemLogAdmin(admin.ModelAdmin):
    fields = ['actionType', 'actionOwner', 'actionObject', 'description', 'createDate' ]
    list_display = ('actionType', 'actionOwner', 'actionObject', 'description', 'createDate' )
    list_filter = ['actionType', ]              
    search_fields = ['actionOwner', 'actionObject']

class UserGroupRelationAdmin(admin.ModelAdmin):
    fields = ['user_group', 'user',  ]
    list_display = ('user_group', 'user', )
    list_filter = ['user_group', ]
    #search_fields = ['dev_area', 'team_name', ]    

class StationGroupRelationAdmin(admin.ModelAdmin):
    fields = ['user_group', 'base_station', ]
    list_display = ('user_group', 'base_station', )
    list_filter = ['user_group', ]

class LocationAdmin(admin.ModelAdmin):
    fields = ['uuid', 'name', 'parent']
    list_display = ('uuid', 'name', 'parent')
    
class VideoTaskAdmin(admin.ModelAdmin):
    fields = ["taskID", "userName", "windowID", "name", "uuid", "channel", 
              "weekDays", "startTime", "endTime",
              "minShowTime", "status", "showOrder", ]
    list_display = ("taskID", "userName", "windowID", "name", "uuid", "channel", 
                    "weekDays", "startTime", "endTime",
                    "minShowTime", "status", "showOrder",)
    order_by = ("userName", "windowID", "showOrder")
    list_filter = ['userName', ]
    
#class AlarmDefineAdmin(admin.ModelAdmin):
#    fields = ['alarmName', 'alarmLevel', 'alarmCategory', 'reActiveTime']
#    list_display = ('alarmCode', 'alarmName', 'alarmLevel', 'alarmCategory', 'reActiveTime')    

admin.site.register(Location, LocationAdmin)
admin.site.register(BaseStation, BaseStationAdmin)
admin.site.register(AlarmRecord, AlarmRecordAdmin)
admin.site.register(User, UserAdmin)
admin.site.register(UserGroup, UserGroupAdmin)
#admin.site.register(UserGroupRelation, UserGroupRelationAdmin)
#admin.site.register(StationGroupRelation, StationGroupRelationAdmin)
admin.site.register(SystemLog, SystemLogAdmin)
admin.site.register(VideoTask, VideoTaskAdmin)
#admin.site.register(AlarmDefine, AlarmDefineAdmin)





