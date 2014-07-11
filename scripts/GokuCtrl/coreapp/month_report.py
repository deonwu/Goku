# -*- coding: utf8 -*-
from GokuCtrl.ipa4django.db import QueryUtils
from GokuCtrl.ipa4django.views import TableView, CellRender
import logging
from django.shortcuts import render_to_response
from django import template
from django.http import HttpResponse

class MonthReportRender(CellRender):
    def __init__(self, ):
        #self._build = task_list
        #'prb_name', 'cci_project', 'smt_project', 'smt_sut', 'smt_case_filter', 'prb_file_path'
        cols = [u'row_no@行',
            u'report_month@月份',
            u'a1001@外部报警',
            u'a1002@视频丢失',
            u'a1003@动态检测',
            u'a1004@硬盘错误',
            u'a2001@连接超时',
            u'a5001@图片告警'
            ]
        super(MonthReportRender, self).__init__(cols)
            
    def nav_title(self, d):
        return ""
    
    def tr_css(self, r):
        return "row%s" % ((self.row_number % 2) + 1)

    def td_css(self, r, name):
        return name
    
    def run(self, r):
        return "<a href='%s/areaci/build_lwt?lwt_id=%s'> Run now </a>" % (APP_ROOT, r.id)
    
    def row_no(self, e):
        self.row_number += 1        
        return unicode(self.row_number)
        
    def start_tbody(self, e):
        self.row_number = 0
        
    def _render_td(self, name, row):
        a = getattr(row, name)
        import types
        if isinstance(a, (types.FloatType, types.IntType)):
            a = u"%0.0d" % a        
        return a

def month_report(r, isExcel=False):
    query = QueryUtils(logging.getLogger('db'))
    month_report_sql = """
select (YEAR(startTime)*100 + month(startTime)) as report_month, 
sum(if(alarmCode='1001', 1, 0)) as a1001,
sum(if(alarmCode='1002', 1, 0)) as a1002,
sum(if(alarmCode='1003', 1, 0)) as a1003,
sum(if(alarmCode='1004', 1, 0)) as a1004,
sum(if(alarmCode='2001', 1, 0)) as a2001,
sum(if(alarmCode='5001', 1, 0)) as a5001

from alarm_record group by report_month order by report_month desc
    """
    report = query.execute_sql(month_report_sql, (), fetch_row=True)
        
    table = TableView(MonthReportRender(), report, )
    table.height = 50
    table.attr = {"class": "report", 'id': 'month_report',}
    table.group_by(['row_no', ])
    table.update_view(r)
    if isExcel:
        data = table.as_excel_data(u'month report')
        return HttpResponse(data, mimetype='application/ms-excel')
    else:
        table.as_table()
        #table.no_paging()
            
        return render_to_response("month_report.html", {"table": table,
                                                        },
                                    context_instance=template.RequestContext(r)
                              )
