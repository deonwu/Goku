
from django.core.serializers.json import Serializer as BuitlInJsonSerializer
from django.utils.encoding import smart_unicode
import simplejson
from django.db.models.query import QuerySet
from django.db import models
from StringIO import StringIO
import types
from ipa4django.db.raw_sql import SQLRow

class DjangoSerializer(BuitlInJsonSerializer):
    
    def end_object( self, obj ):
        if(self.selected_fields ):
            for field_name in self.selected_fields:
                if self._current.has_key(field_name):continue
                try:
                    o = obj
                    for attr in field_name.split("."):
                        o = getattr(o, attr)
                    if callable(o): o = o()
                    field_name = field_name.replace(".", "_")
                    if type(o) not in [types.ListType, types.DictType]:
                        self._current[field_name] = smart_unicode(o, strings_only=True)
                    else:
                        self._current[field_name] = o
                except:
                    field_name = field_name.replace(".", "_")
                    self._current[field_name] = None
                        
        BuitlInJsonSerializer.end_object(self, obj)

    def end_serialization(self):
        pass
        
    def getvalue(self):
        return self.objects
        
class MixedSerializer(object):
    
    #set django base.Serializer
    internal_use_only = False
    
    def __init__(self):
        pass
        
    def serialize(self, object, **options):
        self.stream = options.get("stream", StringIO())
        self.selected_fields = options.get("fields")
        
        obj = self.object_to_serializable(object)
        
        from django.core.serializers.json import DjangoJSONEncoder
        simplejson.dump(obj, self.stream, cls=DjangoJSONEncoder, **options)
        
        return self.getvalue()

    def getvalue(self):
        if callable(getattr(self.stream, 'getvalue', None)):
            return self.stream.getvalue()
        
    def dict_to_serializable(self, o):

        for k, v in o.items():
            o[k] = self.object_to_serializable(v)
        
        return o
    
    def list_to_serializable(self, obj):
        
        r = []
        for o in obj:
            r.append(self.object_to_serializable(o))
            
        return r
    
    def sql_row_to_serializable(self, obj):
        o = {}
        if not hasattr(obj, '__json__'):
            for attr in obj.field_names:
                o[attr] = getattr(obj, attr)
        else:
            o = obj.__json__()
        return o

    def object_to_serializable(self, o):
        
        if isinstance(o, types.DictType):
            return self.dict_to_serializable(o)
        elif isinstance(o, types.TupleType):
            if len(o) <= 0: 
                return []
            elif isinstance(o[0], QuerySet):
                return self.queryset_to_serializable(*o)
            elif isinstance(o[0], models.Model):
                return self.django_model_to_serializable(*o)
            else:
                return self.list_to_serializable(o)
        elif isinstance(o, QuerySet):
            return self.queryset_to_serializable(o)
        elif isinstance(o, models.Model):
            return self.django_model_to_serializable(o)
        elif isinstance(o, types.ListType):
            return self.list_to_serializable(o)
        elif isinstance(o, SQLRow):
            return self.sql_row_to_serializable(o)
        elif hasattr(o, '__json__') and callable(o.__json__):
            return o.__json__()
        
        return o

    def queryset_to_serializable(self, o, args={}, *dummy):
        
        def pre(r, param):
            
            if param.has_key("pre"):
                for i in r:
                    for p in param["pre"]:
                        o = getattr(i, p)
                        if callable(o): o()
                del param["pre"]
            
            return param
            
        def merge_ext_fields(r, param):
        
            ext_fields = []
            if param.has_key("ext_fields"):
                ext_fields = param['ext_fields']
                del param['ext_fields']
            
            if len(o) <= 0: return param
            
            r = o[0]
            fields = not param.has_key("fields") and \
                        [ f.attname for f in r._meta.local_fields ] or \
                        list(param['fields'])
            
            fields.extend(ext_fields)
            param['fields'] = fields
            
            return param
        
        django_ser = DjangoSerializer()
        if args: args = pre(o, args)
        if args: args = merge_ext_fields(o, args)
                
        return django_ser.serialize(o, **args)
    
    def django_model_to_serializable(self, o, args={}, *dummy):
        r = self.queryset_to_serializable([o, ], args)
        return r[0]
    
Serializer = MixedSerializer
  