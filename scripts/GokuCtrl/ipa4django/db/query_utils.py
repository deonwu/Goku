
"""
A class for convert logical expression to Django Q object.
"""

from django.db.models import Q
import types
from copy import deepcopy

class EQ(Q):
    def __init__(self, **kwargs):
        kw = {}
        for k, v in kwargs.iteritems():
            if k.endswith("search"):
                kw[k] = TQ(v)
            else:
                kw[k] = v
        super(EQ, self).__init__(**kw)
    
    def __call__(self):
        return self
    
    def _combine(self, other, conn):
        if not isinstance(other, Q):
            raise TypeError(other)
        
        """connect tag expresion if the fields is same."""
        if len(other.children) == 1 and isinstance(other.children[0], types.TupleType) and \
            isinstance(other.children[0][1], TQ):
            filed_name, tag = other.children[0]
            
            if len(self.children) == 1 and isinstance(self.children[0], types.TupleType) and \
                isinstance(self.children[0][1], TQ):
                if self.children[0][0] == filed_name:
                    new_tag = deepcopy(self.children[0][1])
                    new_tag.add(tag, conn)
                    return self._update_children((filed_name, new_tag), 0)
                
            elif len(self.children) >= 1 and self.connector == conn:
                for i in range(len(self.children)):
                    f, v = self.children[i]
                    if f == filed_name:
                        new_tag = deepcopy(v)
                        new_tag.add(tag, conn)
                        return self._update_children((f, new_tag), i)
        
        return super(EQ, self)._combine(other, conn)

    def _update_children(self, item, index):
        obj = deepcopy(self)
        obj.children[index] = item
        return obj
    
    def __invert__(self):
        if len(self.children) == 1 and isinstance(self.children[0], types.TupleType) and \
            isinstance(self.children[0][1], TQ):
            obj = deepcopy(self)
            obj.children[0][1].negate() #= not obj.children[0][1].negated
            return obj 
        else:
            return super(EQ, self).__invert__()

class TQ(Q):
    """tag expression"""
    def __str__(self):
        if len(self.children) == 1:
            expr = str(self.children[0])
            if self.negated: expr = "(-%s)" % expr
            return expr
        elif len(self.children) == 0:
            return ''
        elif self.connector == self.AND:
            children = ["+%s" % str(e) for e in self.children ]
            return "(%s)" % (" ".join(children))
        elif self.connector == self.OR:
            children = ["%s" % str(e) for e in self.children ]
            return "(%s)" % (" ".join(children))

    def __invert__(self):
        obj = deepcopy(self)
        obj.negated = not obj.negated
        return obj
    