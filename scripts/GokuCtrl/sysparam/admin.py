
from django.contrib import admin
from models import *

class AlarmDefineAdmin(admin.ModelAdmin):
    fields = [ 'alarmName', 'alarmLevel', 'alarmCategory', 'reActiveTime', 'alarmStatus', 'alarmDesc']
    list_display = ('alarmCode', 'alarmName', 'alarmLevel', 'alarmCategory', 'reActiveTime', 'alarmStatus')
    list_filter = ['alarmStatus', 'alarmCategory', 'alarmLevel']
    #list_filter = ['alarmLevel', ]   
    def has_add_permission(self, r):
        return False
    def has_delete_permission(self, r, obj=None):
        return False
    
class BTSCategoryAdmin(admin.ModelAdmin):
    fields = ['uuid', 'name']
    list_display = ('uuid', 'name')       
        
admin.site.register(AlarmDefine, AlarmDefineAdmin)
admin.site.register(BTSCategory, BTSCategoryAdmin)
 