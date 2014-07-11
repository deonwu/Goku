# -*- coding: utf8 -*-
import types
import logging
from paging import Paging
from django.utils.safestring import mark_safe
    
class CellRender(object):
    def __init__(self, cols=[]):
        self.cols = cols
    
    def render_title(self, name):
        return self._cols_titles.get(name, name)

    def nav_title(self, data):
        return "xx xxx"
    
    def _get_cols(self,):
        return self._data_cols

    def _set_cols(self, cols):
        self._cols_titles = {}
        self._data_cols = []
        self._hidden_cols = []
        for e in cols:
            if "@" in e:
                db_attr, title = e.split('@', 1)
            else:
                db_attr, title = e, str(e).split('.')[-1].replace("_", " ").capitalize()
            self._data_cols.append(db_attr)
            self._cols_titles[db_attr] = title
            if db_attr[0] == '_' or title[0] == '_':
                self._hidden_cols.append(db_attr)
        
        return self._data_cols
    
    cols = property(_get_cols, _set_cols)
    
    def is_hidden(self, name):
        return name in self._hidden_cols
    
    def __getattr__(self, name):
        if '_data_cols' not in self.__dict__ or name not in self.cols:
            raise AttributeError, "Not found attribute '%s' in %s" % (name, 
                self.__class__.__name__)
        td = lambda x: self._render_td(name, x)
        return td
    
    def _render_td(self, name, row):
        if '.' in name:
            data = row
            for attr in name.split("."):
                data = getattr(data, attr)
                if data is None: break
        else:
            data = getattr(row, name)
            
        if not data:
            return ""
        elif isinstance(data, basestring): 
            return data
        else:
            return unicode(data) 
            
    def parse_header_from_sql(self, sql):
        pass
    
    def td_css(self, row, column):return ''
    def tr_css(self, row):return ''
    def start_tbody(self, data):pass
    def end_tbody(self,):pass
    
        
class TableView(object):
    logger = logging.getLogger("table.view")
    def __init__(self, header=[], data=None, data_count=None):
        self._data = None
        self._cols = None
        self.attr = {}
        
        self.data_count = data_count
        self.cells = header
        self.data = data
        self.comments = ""
        self.__as_talbe = None
        self.group_column = []
                
        self.paging_controller = PagingController()
        
    
    def _get_data(self,):
        return self._data

    def _set_data(self, d):
        self._data = d
        if self.data_count is None:
            if isinstance(d, (types.TupleType, types.ListType)):
                self.data_count = len(d)
            else:
                self.data_count = d.count()
            self.logger.info("add new data in table:%s" % self.data_count)
        
        return self._data
    
    data = property(_get_data, _set_data)
    
    def _get_headers(self,):
        return self._cols

    def _set_headers(self, cols):
        self._cols = isinstance(cols, CellRender) and cols or CellRender(cols)
    
    cells = property(_get_headers, _set_headers)
    
    def _get_height(self,):
        return self.paging_controller.view_height

    def _set_height(self, height):
        self.paging_controller.view_height = height
    
    height = property(_get_height, _set_height)    
            
    def as_table(self):
        if not self.__as_talbe:
            self.logger.info("add new data in table:%s" % self.data_count)
            if self.paging_controller:
                self.paging_controller.update_view_size(self.data_count)
                data, cols = self.paging_controller.data_view(self.data, self.cells)
            else:
                data, cols = self.data, self.cells.cols
            
            output = u"<!--%s -->" % self.comments
            if self.paging_controller: output += self.render_page_nav()
            
            output += u"<table " + ' '.join(['%s="%s"' % (k,v) for k, v in self.attr.iteritems()]) + ">\n"
            output += self.render_header(cols)
            output += u"<tbody>" + self.render_body(data, cols) + u"</tbody>"
            output += u"</table>"
            
            if self.paging_controller: output += self.render_page_nav(False)
            self.__as_talbe = mark_safe(output)
            
        return self.__as_talbe
    
    def as_excel_data(self, sheet_name):
        from pyExcelerator import Workbook
        from excel import ExcelColumn, ExcelSheet
        import tempfile, os
        logger = logging.getLogger("report.excel")
        if self.paging_controller:
            self.paging_controller.update_view_size(self.data_count)
            data, cols = self.paging_controller.data_view(self.data, self.cells)
        else:
            data, cols = self.data, self.cells.cols
        
        temp_file = tempfile.NamedTemporaryFile(suffix='.xls', dir=tempfile.tempdir)
        logger.debug("exported tmp excel name: %s" % temp_file.name)
        temp_file.close()
                
        work_book = Workbook()
        
        log_colums = [ ExcelColumn(e, e) for e in cols if e != 'row_no']
        
        sheet = ExcelSheet(sheet_name, log_colums)
        sheet.init_sheet(work_book, data)
        sheet.export_header(data)
        sheet.export_data(data)
        
        work_book.save(temp_file.name)

        data = open(temp_file.name, "rb").read()
        logger.debug("done to export excel, data length: %s" % len(data))
        os.remove(temp_file.name)        
        return data
    
    def render_header(self, cols):
        data = u"<thead><tr>"
        for e in cols:
            row_data = u"<th>" + self.cells.render_title(e) + "</th>\n" 
            if self.cells.is_hidden(e):
                row_data = u"<!-- %s -->" % row_data
            data += row_data
            
        data += u"</tr></thead> \n"
        return mark_safe(data)
    
    def render_page_nav(self, header=True):
        page_nav = self.paging_nav()
        html_nav = page_nav and page_nav.output_html() or ""        
        if header:
            data = """<div class="page_nav nav_title"> %s%s
                    </div>""" % (self.cells.nav_title(self), html_nav)
        else:
            data = """<div class="page_nav">%s</div>""" % html_nav
        return data
    
    def render_body(self, data, cols):
        output = u""
        
        self.__process_row_span(data)
        self.cells.start_tbody(data)
        for row in data:
            tr_class = self.cells.tr_css(row)
            tr_attr = tr_class and " class='%s'" % tr_class or ''
            
            output += "<tr%s>" % tr_attr
            output += self._render_row(row, cols)
            output += "</tr>\n"
        self.cells.end_tbody()
            
        return mark_safe(output)
    
    def _render_row(self, row, cols):
        output = ""
        for e in cols:
            row_span = self._row_span(row, e)
            if row_span == 0: continue
            td_attr = ''
            td_attr += row_span > 1 and " rowspan='%s'" % row_span or ''
            td_class = self.cells.td_css(row, e)
            td_attr += td_class and " class='%s'" % td_class or ''
            
            row_data = "<td%s>" % td_attr + unicode(getattr(self.cells, e)(row)) + "</td>\n"
                
            if self.cells.is_hidden(e):
                row_data = "<!-- %s -->" % row_data
            output += row_data

        return mark_safe(output)
    
    def _row_span(self, row, col):
        padding_info = "__span_%s__" % col
        if hasattr(row, padding_info): 
            return getattr(row, padding_info)
        else:
            return 1
    
    def update_view(self, r):
        self.paging_controller.update_view(r)
        
    def paging_nav(self):
        return self.paging_controller.paging_nav()
    
    def no_paging(self):
        self.paging_controller = None
    
    def group_by(self, columns=[]):
        self.group_column = columns
    
    def __process_row_span(self, data):
        if len(self.group_column) == 0:return
        for e in self.group_column:
            self.__process_row_span_one_column(data, e)
        
    def __process_row_span_one_column(self, data, col):
        cur_val = last_row = None
        padding_count = 1
        padding_info = "__span_%s__" % col
        
        self.cells.start_tbody(data)
        for row in data:
            v = getattr(self.cells, col)(row)
            if cur_val != v:
                if last_row is not None:
                    setattr(last_row, padding_info, padding_count)
                    padding_count = 1
                last_row = row
                cur_val = v
            else:
                setattr(row, padding_info, 0)
                padding_count += 1
        if last_row is not None:
            setattr(last_row, padding_info, padding_count)
        self.cells.end_tbody()
    
class PagingController(object):
    def __init__(self, start_row=0, start_col=0, view_height=10, view_width=50):        
        self.default_view_height = view_height
        self.default_view_width = view_width
        
        self.start_row = start_row
        self.start_col = start_col
        self.view_height = view_height
        self.view_width = view_width
        self.max_height = 0
        self.max_width = 0
    
    def update_view(self, request=None):
        self.cur_query = request.META['QUERY_STRING'] or ''
        cols_coordinate = request.REQUEST.get("cols", '')
        if cols_coordinate:
            start_col, col_limit = cols_coordinate.split(",")
            start_col, col_limit = int(start_col), int(col_limit)
            self.start_col, self.view_width = start_col, col_limit        
        
        cols_coordinate = request.REQUEST.get("rows", '')
        if cols_coordinate:
            start_row, row_limit = cols_coordinate.split(",")
            start_row, row_limit = int(start_row), int(row_limit)
            self.start_row, self.view_height = start_row, row_limit   
        
        self.order_by = request.REQUEST.get("order_by", "")
        
    def update_view_size(self, max_height, max_width=1000):
        self.max_height = max_height
        self.max_width = max_width
        
    def data_view(self, data, cols):
        st = self.start_row > 0 and self.start_row -1 or 0
        return (data[self.view_height * st: self.view_height * (st + 1)], 
                cols.cols)

    def paging_nav(self):
        if self.max_height < self.view_height: return None
        return Paging(self.max_height, self.start_row, 
                      self.paging_nav_url(), 
                      self.view_height,
                      )
    
    def column_nav(self):
        if self.max_width < self.view_width: return None
        return Paging(self.max_width, self.start_col,
                      self.column_nav_url(), #???
                      self.view_width,
                      increase_mode=True,
                      show_require=True                     
                      )
        
    def paging_nav_url(self):
        query = self.query_string(['load_data', 'rows'])
        return "?%s&rows=PAGE,%s" % (query, self.view_height)

    def column_nav_url(self):
        query = self.query_string(['load_data', 'cols'])
        return "?%s&cols=PAGE,%s" % (query, self.view_width)
        
    def query_string(self, exclude=[]):
        query = self.cur_query
        params = query.split("&")
        params = [ e for e in query.split("&") if e.split("=")[0] not in exclude ]
        
        return "&".join(params)
