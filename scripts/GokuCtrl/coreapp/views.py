# -*- coding: utf-8 -*-
# Create your views here.
from django.http import HttpResponse, HttpResponseRedirect
from django.shortcuts import render_to_response
from django import template

from month_report import month_report

def index(r, *args):
    return HttpResponseRedirect("admin/")

def image_config(r, uuid):
    from models import BaseStation
    try:
        bs = BaseStation.objects.get(uuid = uuid)
        if bs.devType != 2:
            return HttpResponse("不是一个图片设备")
        return render_to_response("image_config.html",
                                  {'bs':bs},
                                  context_instance=template.RequestContext(r)
                                  )
    except BaseException, e:
        return HttpResponse(str(e))

