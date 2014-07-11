from pyExcelerator import *

class ExcelSheet(object):
    def __init__(self, name, colums):
        self.name = name
        self.colums = colums
        self.cur_row = 0
        self.start_col = 0
        self.header_style = XFStyle()
        
        fnt = Font()
        fnt.name = 'Arial'
        fnt.outline = False
        fnt.bold = True
        fnt.height = 300
        
        self.header_style.font = fnt     

    def init_sheet(self, book, data):
        self.sheet = book.add_sheet(self.name)
    
    def export_header(self, data):
        
        cur_row = self.next_row()
        
        cur_col = self.start_col
        for col in self.colums:
            col.col = cur_col
            self.sheet.write(cur_row, col.col, unicode(col.name), self.header_style)
            if col.width > 0:
                for i in range(col.cols):
                    self.sheet.col(col.col + i).width = col.width * 300
            cur_col += col.cols
            
    def export_data(self, data):
        
        for row_data in data:
            cur_row = self.next_row()
            for col in self.colums:
                args = [cur_row, col.col, unicode(col.fetch_value(row_data))]
                style = col.fetch_style(row_data)
                if style: args.append(style)
                if col.cols == 1:
                    self.sheet.write(*args)
                else:
                    args.insert(1, cur_row)
                    args.insert(3, col.col + col.cols - 1)
                    self.sheet.write_merge(*args)


    def next_row(self, row=-1):
        if row != -1: 
            self.cur_row = row
        else:
            self.cur_row += 1
            
        return self.cur_row
        
# -*- coding: utf-8 -*-

class ExcelColumn():
    def __init__(self, name, attr_name, func=None, desc='', cols=1, width=0):
        self.col = 0
        self.cols = cols
        self.style = None
        self.name = name
        self.func = func
        self.attr_name = attr_name
        self.width = width
        if self.width == 0:
            self.width = len(self.name) + 2
    
    def fetch_value(self, data):
        if self.func is not None:
            return self.func(data)
        else:
            v = data
            for f in self.attr_name.split("."):
                v = self._get_attr(v, f)
                if v is None: return u''
            return v
        
    def _get_attr(self, v, f):
        if isinstance(v, dict) or hasattr(v, "__getitem__"):
            return v[f]
        else:
            return getattr(v, f)
    
    def fetch_style(self, data):
        pass
