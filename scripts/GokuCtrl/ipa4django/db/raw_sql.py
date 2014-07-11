"""
this module supports to parse query fields from SQL. it's used to build named 
attributes object for SQL query.

exmaple:

    sql = "select status, count(*) as c from test group by status" 
    cursor.execute(sql, ())
    results = RawSQLResult(sql).fetch_from_cursor(cursor) #return a list of row object.
    cursor.close()
    
    print results[0].status #the 'status' is parsed from sql
    print results[0].c

    print results[1].status
    print results[1].c
    
| Created By | E-Mail Address    |
| Wu DaLong  | dalong.wu@nsn.com |
"""

import time, re
from misc import Benchmark

class SQLRow(object):
    def __init__(self, field_names, field_values,):
        self.field_names = field_names
        for i in range(min(len(field_values), len(field_names))):
            setattr(self, field_names[i], field_values[i])
            
    def __getitem__(self, name):
        if name in self.field_names:
            return getattr(self, name)
        else:
            return None

class RawSQLResult(object):
    factor = re.compile('([().,]|[\w_]+)')
    
    def __init__(self, sql=None):
        self.field_names = sql and self.parse_sql_fields(sql) or []
    
    def parse_sql_fields(self, sql, ):
        term_list = ( e.group(1) for e in self.factor.finditer(sql) )
        fields_list = []
        cur_last_term = []
        term = None
        
        def occur_filed_name(cur_last_term, fields_list):
            if cur_last_term and cur_last_term[-1] != '*':
                if cur_last_term[-1] not in fields_list:
                    fields_list.append(cur_last_term.pop())
                else:
                    raise RuntimeError, "duplicated field '%s' in sql '%s'" % (cur_last_term[-1],
                                                                               sql)
            elif cur_last_term and cur_last_term[-1] == '*':
                raise RuntimeError, "alias is required for fucntion or sub query."
            else:
                raise RuntimeError, "not found fields near by '%s' in (%s)." % (term, fields_list)
        
        for term in term_list:
            if term == "(": #start a sub query or function
                cur_last_term.append(term) # = None
                continue
            elif term == ")":
                if cur_last_term and cur_last_term[-1] == "(":
                    cur_last_term.pop()
                    if not cur_last_term or cur_last_term[-1] != "(": #ends sub query
                        cur_last_term.append("*") #
                    continue
                else:
                    raise RuntimeError, "miss match ')'"
                
            elif cur_last_term and cur_last_term[-1] == "(": # in sub query
                continue
            
            #cur_last_term.append(term)
            if term.lower() == 'from':
                occur_filed_name(cur_last_term, fields_list)
                cur_last_term = [] #waiting new field
                break
            elif term == ',': 
                occur_filed_name(cur_last_term, fields_list)
                cur_last_term = [] #waiting new field
            else:
                cur_last_term.append(term)
                
        return fields_list
    
    def fetch_from_cursor(self, cursor):
        return [ SQLRow(self.field_names, e) for e in cursor.fetchall() ]
    
class QueryUtils(object):
    def __init__(self, logger=None, benchmark=None):
        self.logger = logger
        self.benchmark = benchmark
        if self.benchmark is None and logger:
            self.benchmark = Benchmark(self.logger)
        
    def debug(self, msg):
        self.logger and self.logger.debug(msg)
    
    def start_mark(self, message):
        self.benchmark and self.benchmark.start_mark(message)
    
    def stop_mark(self, ):
        self.benchmark and self.benchmark.stop_mark()
    
    def execute_sql(self, sql, args, close_cursor=True, fetch_row=False):
        self.debug("execute_sql:%s, args:%s" % (sql, args))
        
        select_result = None
        if isinstance(args, dict):
            sql, args = self.replace_sql_parameter(sql, args)
            args = ()
            self.debug("parsed params:%s, args:%s" % (sql, args))
        
        try:
            from django.db import connection, transaction
            self.start_mark("execute_sql")
            
            cursor = connection.cursor()
            # Data modifying operation - commit required
            cursor.execute(sql, args)
            transaction.commit_unless_managed()
            if fetch_row and sql.lower().strip().startswith("select"):
                rs = RawSQLResult(sql)
                select_result = rs.fetch_from_cursor(cursor)
                            
            if close_cursor: cursor.close()
            self.stop_mark()
        except Exception, e:
            if self.logger is not None:
                self.logger.exception(e)
            raise e
        
        if fetch_row: 
            return select_result
        else:
            return cursor
    
    def replace_sql_parameter(self, sql_query, vars={}):
        var_values = []
        def vars_repl(m):
            var_name = m.group(1)
            if not vars.has_key(var_name):
                raise RuntimeError, "Not found ${%s} in vars parameter!" % var_name
            v = vars[var_name]
            v = isinstance(v, basestring) and v or str(v)
            var_values.append(v)
            return v
        
        sql_query = re.sub(r"\$\{(\w+)\}", vars_repl, sql_query)
        
        return (sql_query, var_values)
    
    def query_filter_to_sql(self, table_name, query_filter):
        """
        convert a query filter to SQL, the query filter is a Q object of django or a 
        dictionary.
        """
        from django.db.models import Q
        from types import TupleType
        if isinstance(query_filter, dict):
            query_filter = Q(**query_filter)
        elif isinstance(query_filter, basestring):
            return query_filter
        
        named_op = {"eq":"=", "gt":">", "lt":"<", "ne":"!=", 
                    "gte":">=", "lte":"<=",
                    "like": "like",
                    "in": "in" }
        
        def as_sql(q):
            cnd = []
            for e in q.children:
                if isinstance(e, TupleType):
                    f, v = e
                    f, op = "__" in f and f.split("__", 1) or (f, "eq")
                    if op != 'in' and isinstance(v, basestring): v = "'%s'" % v
                    if table_name:
                        cnd.append("%s.%s %s %s" % (table_name, f, 
                                                     named_op.get(op, "="),
                                                     v))
                    else:
                        cnd.append("%s %s %s" % (f, named_op.get(op, "="), v))
                        
                elif isinstance(e, Q):
                    cnd.append("(%s)" % as_sql(e))
                    
            return (" %s " % q.connector).join(cnd)
         
        return as_sql(query_filter) or "1=1"
    
    def tag_expr_to_django_query(self, expr, tag_parser=None):
        from ast_boolean import compile_ast_tree
        ast = compile_ast_tree(expr)
        from django.db.models import Q
        def django_parser(t):
            k, v = t.split('-', 1)
            return Q(**{k:v})
        
        tag_parser = tag_parser or django_parser
        
        call_stack = []
        def walker(op_node):
            if op_node.op_sign == "leaf":
                call_stack.append(tag_parser(str(op_node)))
            elif op_node.op_sign == "and":
                right_node = call_stack.pop()
                left_node = call_stack.pop()
                call_stack.append(left_node & right_node)
            elif op_node.op_sign == "or":
                right_node = call_stack.pop()
                left_node = call_stack.pop()
                call_stack.append(left_node | right_node)
            elif op_node.op_sign == "not":
                right_node = call_stack.pop()
                call_stack.append(~right_node)
            elif op_node.op_sign == "tree":
                pass
                    
        ast.walk_tree(walker)
        return call_stack[0]
    
#=================UNIT TEST=================
        
import unittest
class TestRawSQL(unittest.TestCase):
    
    def test_parse_sql_fields(self):
        fn = RawSQLResult().parse_sql_fields
        
        self.assertEquals(fn("select a, b from c"), ["a", "b"])
        self.assertEquals(fn("select a as name, b.name2 from c"), ["name", "name2"])
        self.assertEquals(fn("select count(*) as name, b.name2 as b from c"), ["name", "b"])
        self.assertEquals(fn("select count(select * from x) as name, b.name2 as b from c"), ["name", "b"])
        self.assertEquals(fn("select a from c"), ["a", ])
        self.assertEquals(fn("select a as na_me from c"), ["na_me", ])
        self.assertEquals(fn("select distinct a from c"), ["a", ])
        
        self.assertRaises(fn, ("select count(select * from x) from c", ), {},
                          RuntimeError,
                          "alias is required for fucntion or sub query.")
        
        self.assertRaises(fn, ("select a, from c", ), {},
                          RuntimeError,
                          "not found fields near by 'from' in (['a']).")
        
        self.assertRaises(fn, ("select a, count(*) as a from c", ), {},
                          RuntimeError,
                          "duplicated field 'a' in sql 'select a, count(*) as a from c'")        

    def assertRaises(self,
                     func,
                     args,
                     kwargs,
                     expected_exception,
                     expected_message):
        if args is None:
            args = ()
        if kwargs is None:
            kwargs = {}

        try:
            func(*args, **kwargs)
        except expected_exception, err:
            actual_message = str(err)
            self.assert_(expected_message == actual_message,
                             """\
expected exception message:
'''%s'''
actual exception message:
'''%s'''
""" % (expected_message, actual_message))
        else:
            self.fail("""expected exception %(expected_exception)s not raised
called %(func)r
with args %(args)r
and kwargs %(kwargs)r
""" % locals ())

