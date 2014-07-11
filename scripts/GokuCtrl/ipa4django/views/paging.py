# -*- coding: utf-8 -*-

class Paging(object):
    def __init__(self, count, cur_page=0, url="x", limit=10, 
                 increase_mode=False,
                 show_require=True):
        self.count = count
        self.cur_page = cur_page
        self.url = url
        self.limit = limit
        
        self.increase_mode = increase_mode
        self.show_require = show_require
        self._output_html = None
    
    def output_html(self):
        """
        Previous 0 1 2 3 4 ... Next
        """
        
        if self._output_html is not None: return self._output_html
        
        page_count = self.count / self.limit
        if self.count % self.limit != 0: page_count += 1
        cur_page_range = max((self.cur_page - 1) / 10, 0)
        end_page = min(page_count, (cur_page_range + 1) * 10) + 1
        
        menu = []
        
        pre_page = max(self.cur_page -1, 1)
        url_link = self.url.replace("PAGE", str(pre_page))
        menu.append(u"<a href='%s'>Previous</a>" % url_link)
        for page in range(cur_page_range * 10 + 1, end_page):
            cur_style = page == self.cur_page and "class='cur'" or ''
            url_link = self.url.replace("PAGE", str(page))
            menu.append("<a href='%s' %s>%s</a>" % (url_link, cur_style, page))
        
        next_page = min(self.cur_page + 1, page_count)
        url_link = self.url.replace("PAGE", str(next_page))
        menu.append(u"<a href='%s'>Next</a>" % url_link)
        
        self._output_html = "".join(menu)
        return self._output_html
    
    def previous_url(self):
        previous_page = 0
        if self.increase_mode is True:
            previous_page = max(self.cur_page - self.limit + 1, 0)
        else:
            previous_page = max(self.cur_page - 1, 1)
        
        if not self.show_require or self.cur_page > 0:
            return self.url.replace("PAGE", str(previous_page))
        else:
            return ""

    def next_url(self):
        next_page = 0
        have_more = False
        if self.increase_mode is True:
            next_page = self.cur_page + self.limit - 1
            have_more = next_page < self.count
        else:
            next_page = self.cur_page + 1
            have_more = next_page < self.count / self.limit
        
        if not self.show_require or have_more:
            return self.url.replace("PAGE", str(next_page))
        else:
            return ""        
    
        
if "__main__" == __name__:
    p = Paging(190, 10, url="PAGE")
    print p.output_html()        