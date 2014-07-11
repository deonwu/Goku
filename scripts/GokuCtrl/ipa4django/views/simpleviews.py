import types
from django.http import HttpResponse, HttpResponseRedirect
from django.shortcuts import render_to_response
from django import template
from django.db.models.query import QuerySet
import re
from django.core import serializers

class ViewHandler(object):
    def __init__(self, func, param_validator=None):
        self.target = func
        if func.func_defaults:
            reqiured_args_count = func.func_code.co_argcount - len(func.func_defaults)
        else:
            reqiured_args_count = func.func_code.co_argcount
            
        var_names = func.func_code.co_varnames
        self.reqiured_args = var_names[:reqiured_args_count]
        self.options_args = var_names[reqiured_args_count:]
        self.param_validator = param_validator
        
    def __parse_args(self, r, args, params):
        args = list(args)
        args.reverse()
        param = []
        for name in self.reqiured_args:
            if name in ['r', 'request']:
                param.append(r)
                continue
            if not params.has_key(name):
                if args:
                    param.append(args.pop())
                    continue
                else:
                    raise Exception, "Not found required parameter '%s'" % name
            param.append(params[name])
        
        kw_param = {}
        for name in self.options_args:
            if params.has_key(name):
                kw_param[str(name)] = params[name]
        
        return (param, kw_param)
    
    def __call__(self, r,*args, **kwargs):
        param, kw_args = self.__parse_args(r, args, r.REQUEST)
        for k, v in kw_args.iteritems():
            kwargs[k] = v
        if callable(self.param_validator):
            self.param_validator(r, self.target, param, kwargs)
        
        return self.target(*param, **kwargs)

class SimpleViews(object):
    
    def __init__(self, view):
        self.view = self.__import_views(view)
        self.cached = {}
        
        self._pre_handler = None
        self.__param_validator = None
        if hasattr(self.view, "pre_handler"):
            self._pre_handler = getattr(self.view, "pre_handler")
                 
        if hasattr(self.view, "__validation__"):
            self.__param_validator = getattr(self.view, "__validation__")

        
    def pre_handler(self, r, url):
        if self._pre_handler:
            return self._pre_handler(r, url)
        else:
            return None
            
    def __call__(self, request, url, **kwargs):
        pre = self.pre_handler(request, url)
        if pre is not None: return self.result_router(pre, request)
        
        if "/" in url:
            patterns = url.split("/")
            handle_url, args = patterns[0], patterns[1:]
        else:
            handle_url, args = url, ()
         
        h = None
        if self.cached.has_key(handle_url):
            h = self.cached[handle_url]
        else:
            try:
                obj = getattr(self.view, handle_url)
            except AttributeError, e:
                try:
                    obj = getattr(self.view, "default_view")
                except:
                    raise e
            
            h = ViewHandler(obj, self.__param_validator)
            self.cached[handle_url] = h
        
        return self.result_router(h(request, *args, **kwargs), request)
            
    def result_router(self, r, request):
        
        mime_types = ['text/javascript', ]
        
        if isinstance(r, HttpResponse):
            return r
        elif isinstance(r, types.TupleType):
            if isinstance(r[0], QuerySet):
                return HttpResponse(serializers.serialize("mixed_json", r))
            elif len(r) == 1 and re.match("^(http|redirect):", r[0]):
                url = r[0].replace("redirect:","")
                return HttpResponseRedirect(url)
            elif len(r) == 2 and r[0] in mime_types:
                return HttpResponse(r[1], r[0])
            elif len(r) == 2:
                temp, context = r
                return render_to_response(temp, context,
                                          context_instance=template.RequestContext(request))
            
            return render_to_response(*r)
        elif isinstance(r, basestring):
            return HttpResponse(r)
        else:
            return HttpResponse(serializers.serialize("mixed_json", r), 'application/json')
        
    def __import_views(self, v):
        
        if isinstance(v, types.ModuleType):
            return v
        elif isinstance(v, basestring):
            return __import__(v, globals(), locals(), ["__name__"], -1)
        elif isinstance(v, types.ClassType):
            return v()
        
        raise Exception, "Not supported views '%s'." % v

class SimpleUrl(SimpleViews):
    
    def __init__(self, handler):
        handler = handler.split(".")
        module_name, handler = ".".join(handler[:-1]), handler[-1]
        SimpleViews.__init__(self, module_name)
        
        self.action_url = handler
        
        #self.target = ViewHandler(self.__import_url(handler))
    
    def __call__(self, request, **kwargs):
        return SimpleViews.__call__(self, request, self.action_url, **kwargs)
    
def anti_crack(r, *args):
    from anticrack.image import picChecker 
    (code, image) = c.createChecker()
    
    sessionId = r.COOKIES.get('sessionid', None)
    if sessionId is not None:
        cache_key = "anti_%s" % sessionId
        memcache.add(key=cache_key, value=code, time=60 * 5, namespace='global')   
        logging.debug("save anti-crack code for:%s-->%s" % (sessionId, code))
    
    return HttpResponse(image.getdata, mimetype='image/gif')
    
def anti_verify(r, code=""):    
    old_code = None
    sessionId = r.COOKIES.get('sessionid', None)
    if sessionId is not None:
        cache_key = "anti_%s" % sessionId
        old_code = memcache.get(key=cache_key, namespace='global')   
        logging.debug("get anti-crack code for:%s-->%s" % (sessionId, old_code))
    
    return code == old_code
    
