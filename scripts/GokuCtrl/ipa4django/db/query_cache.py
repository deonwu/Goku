"""
this module provide a temporary table to save PKs of query results. it's used to 
split a complicated 'join statement' to several simple SQL. the basic idea is 
saving the PKs of a query results to mediate table, then use the PKs to related
other query result.

Example:
    select l.test_case_execution_time, t.test_case_name 
    from test_case_log l 
    join test_case t on (l.case_id = t.id)
    where t.name like "test%" and l.test_case_execution_time > 1000
    
    split as two query:
    
    insert into mediate_table(name, result_id, test_case_name)
    values 
    select 'test_case', t.id, t.test_case_name from test_case t where t.name like "test%"
    
    select l.test_case_execution_time, t.test_case_name 
    from test_case_log l
    join mediate_table t on (l.case_id = t.result_id and name = 'test_case')
    where l.test_case_execution_time > 1000
    
| Created By | E-Mail Address    |
| Wu DaLong  | dalong.wu@nsn.com |
"""

import logging, hashlib
from ipata.common.db import QueryUtils
from ipata.common.db.raw_sql import RawSQLResult
from django.db import models
from datetime import datetime

class Query(models.Model):
    """
        CREATE TABLE `cache_query_temp` (
            `id` integer AUTO_INCREMENT NOT NULL PRIMARY KEY,
            `name` varchar(50) NOT NULL,
            `session_key` varchar(32) NOT NULL,
            `sql` longtext NOT NULL,
            `create_date` datetime NULL,
            `last_updated_time` datetime,
            `last_access_time` datetime,
            `desc` longtext NOT NULL
        );
        ALTER TABLE cache_query_temp add unique index (session_key); 
    """
    class Meta:
        app_label = 'cache'
        db_table = 'cache_query_temp'
        
    name = models.CharField(max_length=50, )
    session_key = models.CharField(max_length=32, unique=True)
    sql = models.TextField()
    create_date = models.DateTimeField('create_date', auto_now_add=True)
    last_updated_time = models.DateTimeField('last_updated_time', auto_now_add=True)
    last_access_time = models.DateTimeField('last_access_time', auto_now_add=True)
    desc = models.TextField()
    
    @property
    def query_id(self):return self.id

from django.contrib import admin
class CacheQueryAdmin(admin.ModelAdmin):
    fields = [ 'name', 'session_key', 'sql', 
               'desc', ]
    list_display = ('name', 'session_key', )
    
register_admin = lambda :admin.site.register(Query, CacheQueryAdmin)

class BasicCache(object):
    def __init__(self, expire_time=60 * 30, fields=[], dao=None):
        """
        fields = (name, index, sql_type?)
        """
        self.table_name = "cache_%s_temp" % self.__class__.__name__.lower()
        self.cache_fields = dict(( (e[0], e) for e in fields ))
        self.sql_parser = RawSQLResult()
        self.dao = dao or QueryUtils(logging.getLogger("query.cache"))
        self.ordered_fields = fields
        self.expire_time = expire_time
        
        if not self.exists_cache_table(): self.create_cache_table()
    
    def update_query(self, query_or_sessionkey, sql=None, ignore_cache=False):
        update_result = """insert into ${cache_table_name}(query_id, ${cache_fields})
           select ${query_id}, ${cache_fields} from (${sql}) a
        """
        
        created = query = None
        if isinstance(query_or_sessionkey, basestring):
            query, created = Query.objects.get_or_create(session_key=query_or_sessionkey)
        elif isinstance(query_or_sessionkey, Query):
            query = query_or_sessionkey
        else:
            raise RuntimeError, "need a 'Query' object or session key."
        
        cached_time = datetime.now() - query.last_updated_time
        cached_time = cached_time.seconds + cached_time.days * 60 * 60 * 24
        
        if created or ignore_cache or query.cached_time > self.expire_time:
            if created:
                query.name = self.__class__.__name__
            elif query.name and query.name != self.__class__.__name__:
                raise RuntimeError, "cache name:%s!=%s, key=%s" % (query.name,
                                                                   self.__class__.__name__,
                                                                   query.session_key)

            self.clean_cache(query)
            sql_fields = self.sql_parser.parse_sql_fields(sql)
            sql_fields = [ e for e in sql_fields if e in self.cache_fields ]
            update_sql, vars = self.dao.replace_sql_parameter(update_result, 
                                                        {"cache_table_name": self.table_name,
                                                         "query_id": query.id,
                                                         "cache_fields": ",".join(sql_fields),
                                                         "sql":sql
                                                         })
            self.dao.execute_sql(update_sql, (), close_cursor=True)
            query.sql = sql
            query.last_updated_time = datetime.now()
        
        query.last_access_time = datetime.now()
        query.save()
        
        return query
    
    def clean_cache(self, query):
        delete_sql = "delete from %s where query_id=%s" % (self.table_name,
                                                           query.id)
        self.dao.execute_sql(delete_sql, (), close_cursor=True)

    def get_cache_key(self, *query):
        return hashlib.md5(";".join(query)).hexdigest()
    
    def exists_cache_table(self, ):
        try:
            self.dao.execute_sql("select 1 from %s where 1=2" % self.table_name, ())
            return True
        except Exception, e:
            if "doesn't exist" in str(e):
                return False
            else:
                raise
    
    def create_cache_table(self, ):
        """ create new cache table """
        
        create_cache_sql = """
            CREATE TABLE IF NOT EXISTS ${cache_table_name}(
                    query_id INT(11) NOT NULL,
                    ${cache_fields},
                    ${index_fields}
                ) 
                ENGINE=MyISAM,
                DEFAULT CHARACTER SET='latin1'
            """
            
            #ROW_FORMAT=FIXED
            #ENGINE=MEMORY,
            # 
        
        fields_sql = []
        index_fields = ['INDEX (query_id)']
        for name, indexed, sql_type in self.ordered_fields:
            fields_sql.append("%s %s" % (name, sql_type))
            if indexed: index_fields.append("INDEX (%s)" % name)            
        
        create_sql, vars = self.dao.replace_sql_parameter(create_cache_sql, 
                                                    {"cache_table_name": self.table_name,
                                                     "cache_fields": ",".join(fields_sql),
                                                     "index_fields": ",".join(index_fields),
                                                     })
        self.dao.execute_sql(create_sql, (), close_cursor=True)

