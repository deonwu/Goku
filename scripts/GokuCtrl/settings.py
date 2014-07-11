# Django settings for GokuCtrl project.

import os
DEBUG = True
TEMPLATE_DEBUG = DEBUG

ADMINS = (
    # ('Your Name', 'your_email@domain.com'),
)

MANAGERS = ADMINS

import logging, sys
FORMAT = "%(asctime)s %(name)s T[%(thread)d]P[%(process)d] %(levelname)8s %(message)s"
logging.basicConfig(level=logging.DEBUG, format=FORMAT, stream=sys.stdout)
from java.lang import System

DATABASE_ENGINE = 'mysql'           # 'postgresql_psycopg2', 'postgresql', 'mysql', 'sqlite3' or 'oracle'.
DATABASE_NAME = System.getProperty("db_master_db", 'goku')      # Or path to database file if using sqlite3.
DATABASE_USER = System.getProperty("db_master_username", 'root')             # Not used with sqlite3.
DATABASE_PASSWORD = System.getProperty("db_master_password", '')          # Not used with sqlite3.
DATABASE_HOST = System.getProperty("db_master_host", '')             # Set to empty string for localhost. Not used with sqlite3.
DATABASE_PORT = System.getProperty("db_master_port", '')             # Set to empty string for default. Not used with sqlite3.

# Local time zone for this installation. Choices can be found here:
# http://en.wikipedia.org/wiki/List_of_tz_zones_by_name
# although not all choices may be available on all operating systems.
# If running in a Windows environment this must be set to the same as your
# system time zone.
TIME_ZONE = 'Asia/Shanghai'

# Language code for this installation. All choices can be found here:
# http://www.i18nguy.com/unicode/language-identifiers.html
LANGUAGE_CODE = 'zh-CN'

SITE_ID = 1

# If you set this to False, Django will make some optimizations so as not
# to load the internationalization machinery.
USE_I18N = True

# Absolute path to the directory that holds media.
# Example: "/home/media/media.lawrence.com/"
media_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "static")
MEDIA_ROOT = System.getProperty("STATIC_ROOT", media_path)  

# URL that handles the media served from MEDIA_ROOT. Make sure to use a
# trailing slash if there is a path component (optional in other cases).
# Examples: "http://media.lawrence.com", "http://example.com/media/"
MEDIA_URL = '/media2/'

# URL prefix for admin media -- CSS, JavaScript and images. Make sure to use a
# trailing slash.
# Examples: "http://foo.com/media/", "/media/".
ADMIN_MEDIA_PREFIX = '/media/'

# Make this unique, and don't share it with anybody.
SECRET_KEY = 'zhc-$%@%j-ndi4akkw9aqopzx^x9=b$@%5%i#)oj9yf_x1=@+v'

# List of callables that know how to import templates from various sources.
TEMPLATE_LOADERS = (
    'django.template.loaders.filesystem.load_template_source',
    'django.template.loaders.app_directories.load_template_source',
#     'django.template.loaders.eggs.load_template_source',
)

MIDDLEWARE_CLASSES = (
    'django.middleware.common.CommonMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
)

ROOT_URLCONF = 'GokuCtrl.urls'

import os
TEMPLATE_DIRS = (
    # Put strings here, like "/home/html/django_templates" or "C:/www/django/templates".
    # Always use forward slashes, even on Windows.
    # Don't forget to use absolute paths, not relative paths.
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates")
)

INSTALLED_APPS = (
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.sites',
    'django.contrib.admin',
    'GokuCtrl.coreapp',
    'GokuCtrl.sysparam',
    'GokuCtrl.sysuser',
    'doj'
)

DATABASE_ENGINE='doj.backends.zxjdbc.mysql'

