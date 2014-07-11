from django.db import models, connection

class SearchQuerySet(models.query.QuerySet):
     def __init__(self, fields=None, **kwargs):
         super(SearchQuerySet, self).__init__(**kwargs)
         self._search_fields = fields

     def search(self, term, fileds=None):
         meta = self.model._meta

         # Get the table name and column names from the model
         # in `table_name`.`column_name` style
         columns = [meta.get_field(name, many_to_many=False).column
             for name in (fileds or self._search_fields)]
         
         quote_name = connection.ops.quote_name
         
         full_names = ["%s.%s" %
                        (quote_name(meta.db_table),
                         quote_name(column))
                         for column in columns]

         # Create the MATCH...AGAINST expressions
         fulltext_columns = ", ".join(full_names)
         match_expr = ("MATCH(%s) AGAINST ('%s' IN BOOLEAN MODE)" %
                                (fulltext_columns, term))

         # Add the extra SELECT and WHERE options
         return self.extra(where=[match_expr, ] )


class SearchManager(models.Manager):
     def __init__(self, fields=[]):
         super(SearchManager, self).__init__()
         self._search_fields = fields
         

     def get_query_set(self):
         return SearchQuerySet(model=self.model, fields = self._search_fields)

     def search(self, query, fields=None):
         return self.get_query_set().search(query, fields)

def complie_expr_to_sql(expr):
    from ast_boolean import compile_ast_tree
    ast = compile_ast_tree(expr)
    
    call_stack = []
    
    def walker(op_node):
        if op_node.op_sign == "leaf":
            call_stack.append(str(op_node))
        elif op_node.op_sign == "and":
            right_node = call_stack.pop()
            left_node = call_stack.pop()
            call_stack.append("+%s +%s" % (left_node, right_node))
        elif op_node.op_sign == "or":
            right_node = call_stack.pop()
            left_node = call_stack.pop()
            call_stack.append("%s %s" % (left_node, right_node))
        elif op_node.op_sign == "not":
            right_node = call_stack.pop()
            call_stack.append("(-%s)" % right_node)
        elif op_node.op_sign == "tree":
            right_node = call_stack.pop()
            call_stack.append("(%s)" % right_node)
                
    ast.walk_tree(walker)
    full_index = call_stack[0]
    
    return full_index[1:-1]

if __name__ == "__main__":
    print complie_expr_to_sql("ftp&aa")

