
from django.http import HttpResponse
from django.core import serializers
import logging, urllib, types, hashlib

class FormDataSource(object):pass

class FormWrapper(object):
    def __init__(self, form_cls, data_cls=FormDataSource):
        self.form = form_cls(data={})
        self.data_source = data_cls()
        self.field_dependent = {}
        self.field_related = {}
        
        self._bound_fields = dict(((e.name, e) for e in iter(self.form)))
    
    def update_form_data(self, data):
        self._caching_key = None
        for e in data.keys():
            self.form.data[e] = data.get(e)
    
    def update_form_choice(self):
        """invoke in template for lazy loading data."""
        fields = self.form.fields
        for e in fields:
            if not hasattr(fields[e], 'choices'):continue
            choices = self.field_choice(e)
            fields[e].choices = choices or ()
        return ""
    
    def field_choice(self, name):
        if not hasattr(self.data_source, name): return ()
        loader = getattr(self.data_source, name)
        param = {}
        
        dependent = self.field_dependent.setdefault(name, [])
        for e in loader.func_code.co_varnames:
            if e not in self._bound_fields: continue
            dependent.append(e)
            #v = urllib.unquote(self.data.get(e, None))
            v = self._bound_fields[e].data
            if not v:continue
            if isinstance(v, types.ListType):
                v = ",".join(v)
            else:
                v = urllib.unquote(unicode(v))
            
            param[e] = v 
        return loader(**param)    
    
    def relation_as_js(self):
        try:
            for name, dependon in self.field_dependent.iteritems():
                for e in dependon:
                    related = self.field_related.setdefault(e, [])
                    related.append(name)
            
            dependent = {}
            for k, v in self.field_dependent.iteritems():
                if len(v) == 0: continue 
                dependent[k] = list(set(v))

            field_related = {}
            for k, v in self.field_related.iteritems():
                if len(v) == 0:continue
                field_related[k] = list(set(v))
            
            str = "var dependent=%s;\n" % serializers.serialize("mixed_json", 
                                                       dependent) 
            str += "var related=%s;" % serializers.serialize("mixed_json", 
                                                       field_related)
            return str
        except Exception, e:
            return str(e)
            
    def as_tag_expr(self):
        """
        (RELEASE_NAME-A100|RELEASE_NAME-A10)&
        ((RELEASE_NAME-A100|RELEASE_NAME-A10)&DAILY_BUILD))
        """
        tag_value = []
        for e in self.fields:
            field_value = self.data.get(e, None)
            tagname = e.upper()
            if not field_value:continue
            if not isinstance(field_value, types.ListType): 
                field_value = urllib.unquote(str(v)).split(",")
            expr = "|".join(["%s-%s" % (tagname, tv) for tv in field_value])
            tag_value.append("(%s)" % expr)
        return "&".join(tag_value)
        
    def dyn_field_choice(self, name):
        return HttpResponse(serializers.serialize("mixed_json", 
                                                  self.field_choice(name)))
        
    def bound_fields(self, names=[]):        
        return [ self._bound_fields[e] for e in names ]
    
    @property
    def media(self): return self.form.media
    
    def param_data(self, names=[]):
        single = lambda x: x # x[0] if isinstance(x, types.ListType) else x
        return dict(((e.name, single(e.data)) for e in self._bound_fields.values() if e.data ))
    
    def query_session_id(self, query_string = []):
        if self._caching_key: return self._caching_key
        for k, v in self.param_data().items():
            query_string.append(k)
            query_string.append(v)
        
        self._caching_key = hashlib.md5(";".join(query_string)).hexdigest()
        return self._caching_key
    