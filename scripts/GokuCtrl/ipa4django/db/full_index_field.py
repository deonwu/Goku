

from django.db.models import TextField

class FullIndexTextField(TextField):
    def __init__(self, copy_from="", **kwargs):
        self.copy_from = copy_from
        super(FullIndexTextField, self).__init__(**kwargs)
            
    
    def pre_save(self, model_instance, add):
        if self.copy_from:
            value = getattr(model_instance, self.copy_from)
        else:
            value = super(FullIndexTextField, self).pre_save(model_instance, add)
        
        value = value.upper().replace("-", "_")
        setattr(model_instance, self.attname, value)
        return value
