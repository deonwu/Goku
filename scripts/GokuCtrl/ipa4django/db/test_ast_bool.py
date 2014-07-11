import os
import unittest
from ast_boolean import compile_ast_tree 

class TestASTBool(unittest.TestCase):
    
    def test_compile_ast_tree(self):
        
        for expr, order in (("a|b|c", ['a', 'b', '[1] or [2]', 'c', '[3] or [4]']),
                            ("a|b&c", ['a', 'b', 'c', '[2] and [3]', '[1] or [4]']),
                            ("a|b|!c", ['a', 'b', '[1] or [2]', 'c', 'not [4]', '[3] or [5]']),
                            ("a|(b|c)", ['a', 'b', 'c', '[2] or [3]', 'tree [4]', '[1] or [5]']),
                            ("a|(b|c)&d", ['a', 'b', 'c', '[2] or [3]', 'tree [4]', 'd', '[5] and [6]', '[1] or [7]']),
                            ("a|(b&c)|d", ['a', 'b', 'c', '[2] and [3]', 'tree [4]', '[1] or [5]', 'd', '[6] or [7]']),
                            ("(a|b)&c|d", ['a', 'b', '[1] or [2]', 'tree [3]', 'c', '[4] and [5]', 'd', '[6] or [7]']),
                            ("a&(b)|c", ['a', 'b', 'tree [2]', '[1] and [3]', 'c', '[4] or [5]']),
                            ("a&((b))|c", ['a', 'b', 'tree [2]', 'tree [3]', '[1] and [4]', 'c', '[5] or [6]']),                                                        
                            ("a&(b|(c|d))", ['a', 'b', 'c', 'd', '[3] or [4]', 'tree [5]', '[2] or [6]', 'tree [7]', '[1] and [8]']),
                            ):
            tree = compile_ast_tree(expr)
            self.assertEqual(self.fetch_operater(tree), order)
                
    def fetch_operater(self, tree):
        list = tree.evaluated_list()
        return [ e[0] for e in list ]
    
    def test_evaluate_expr(self):
        def boolparser(expr):
            def op(list):
                list.append(expr)
                return expr == 'Y'
            return op
        
        for expr, result, step_count in (("Y|Y", True, 1),
                                         ("N|Y", True, 2),
                                         ("N|N", False, 2),
                                         ("Y&Y", True, 2),
                                         ("Y&N", False, 2),
                                         ("N&N", False, 1),
                                         ("N&Y", False, 1),
                                         ("!N", True, 1),
                                         ("!Y", False, 1),
                                         ("N|Y&N", False, 3),
                                         ("N|Y|N", True, 2),
                                         ("N|Y|!N", True, 2),
                                         ("N|!Y|!N", True, 3),
                                         ("N|(Y|N)", True, 2)
                                         ):
            tree = compile_ast_tree(expr, boolparser)
            step = []
            self.assertEqual(tree.evalute(step), result)
            self.assertEqual(len(step), step_count)
