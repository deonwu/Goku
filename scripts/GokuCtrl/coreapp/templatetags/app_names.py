# -*- coding: utf-8 -*-
from django import template
from django.template.defaultfilters import stringfilter
import re

register = template.Library()



@register.filter
@stringfilter
def app(value):
    return "name:" + name

def app_root():
    """
    Returns the string contained in the setting ADMIN_MEDIA_PREFIX.
    """
    try:
        from django.conf import settings
    except ImportError:
        return ''
    return settings.APP_ROOT


def app_name(name):
    names = {'auth': "系统用户",
             'coreapp': "监控设备／告警",
             'sites': "网站设置",
             'sysparam': "系统参数",
             }
    return names.get(name.lower(), name);

app_root = register.simple_tag(app_root)
app_name = register.simple_tag(app_name)


#register.filter(robot_time2)
