from django.conf.urls.defaults import *

# Uncomment the next two lines to enable the admin:
from django.contrib import admin
admin.autodiscover()
from settings import MEDIA_ROOT

urlpatterns = patterns('',
    # Example:
    # (r'^GokuCtrl/', include('GokuCtrl.foo.urls')),

    # Uncomment the admin/doc line below and add 'django.contrib.admindocs' 
    # to INSTALLED_APPS to enable admin documentation:
    # (r'^admin/doc/', include('django.contrib.admindocs.urls')),
    (r'^media2/(?P<path>.*)$', 'django.views.static.serve',
        {'document_root': MEDIA_ROOT}),

    # Uncomment the next line to enable the admin:
    (r'^admin/(.*)', admin.site.root),
    (r'^img_config/(\d+)', "GokuCtrl.coreapp.views.image_config"),
    (r'^month_report(\.xls)?', "GokuCtrl.coreapp.views.month_report"),
    (r'^(.*)', "GokuCtrl.coreapp.views.index"),
)
