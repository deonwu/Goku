""" this module is a tool to evaluate boolean expression. it parse the expression as a AST (Abstract Syntax Tree).
the tree is evaluated from left to right. the process of evaluating were stopped that the result is evident.
 like: left value of 'and' is false, then it stop to evaluate right expression.

    the expressions can be linked with the following boolean operators:
    - '|' or  
    - '&' and
    - '!' not
    the prior level: "!" > "&" > "|". 

exmaple:
    ast_tree = compile_ast_tree("a&(b|c)")
    result = ast_tree.evalute()
    operate_list = ast_tree.evaluated_list()
    
| Created By | E-Mail Address        |
| Wu DaLong  | dalong.wu.ext@nsn.com |
"""
import re

class AST_NODE(object):
    def __init__(self, op_sign, prior_level, opdata_count = 2):
        self.op_sign = op_sign
        self.prior = prior_level
        self.child = []
        self.opdata_count = opdata_count
        self.eval_order = 0
        
    def walk_tree(self, op):
        for e in self.child:
            e.walk_tree(op)
        op(self)
    
    def __str__(self):
        if self.opdata_count == 1:
            return self.op_sign + " [%s]" % self.child[0].eval_order
        elif self.opdata_count == 2:
            return "[%s] %s [%s]" % (self.child[0].eval_order, self.op_sign, self.child[1].eval_order)
        else:
            return self.op_sign
        
class AST_Tree(AST_NODE):
    def __init__(self):
        AST_NODE.__init__(self, "tree", 100, 1)
        self.root = None
        self.child.append(None)
        
    def evalute(self, *args):
        """ it evaluate the AST. 
        """
        return self.root.evalute(*args)
        
    def insert_node(self, leaf):
        if self.root == None:
            self.root = leaf
        elif self.root.prior >= leaf.prior:
            leaf.insert_node(self.root)
            self.root = leaf
        else:
            self.root.insert_node(leaf)
        self.child[0] = self.root
        
    def evaluated_list(self):
        """ get a list of process of evaluated the tree. the item of list is a tuple that is included "operation, prior,
         argument count, order".
         
         example:
            ast_tree = compile_ast_tree("a&(b|c)")
            operate_list = ast_tree.evaluated_list()
            operate_list = [('a', 4, 0, '[1]'),
                            ('b', 4, 0, '[2]'), 
                            ('c', 4, 0, '[3]'), 
                            ('[2] or [3]', 1, 2, '[4]'), 
                            ('[1] and [4]', 2, 2, '[5]')]
        """
        
        list = []
        def walker(op_node):
            i = len(list) + 1
            op_node.eval_order = i
            list.append((str(op_node),
                        op_node.prior,
                        op_node.opdata_count, '[%s]' % i))
        self.root.walk_tree(walker)
        
        return list

class AST_Leaf(AST_NODE):
    def __init__(self, op):
        AST_NODE.__init__(self, "leaf", 5, 0)
        self.op = op
        
    def evalute(self, *args):
        return self.op(*args)
    def insert_node(self, leaf):
        raise RuntimeError, "operator expect, but a leaf item:'%s'" % str(self.op)
    def __str__(self):
        return str(self.op)

class AST_OP(AST_NODE):
    def __init__(self, op_sign, prior_level, opdata_count = 2):
        AST_NODE.__init__(self, op_sign, prior_level, opdata_count)
        
    def insert_node(self, node):
        if len(self.child) < self.opdata_count:
            self.child.append(node)
        elif self.child[-1].prior >= node.prior:
            data = self.child.pop()
            node.insert_node(data)
            self.child.append(node)
        else:
            self.child[-1].insert_node(node)
            
class AST_EVALUTOR(AST_OP):
    def __init__(self, evalutor, *args):
        if evalutor: self._evalutor = evalutor
        super(AST_EVALUTOR, self).__init__(*args)

    def evalute(self, *args):
        left, right = self.child[0].evalute, self.child[1].evalute
        return self._evalutor(self.op_sign, left, right, *args)
    
    def _evalutor(self, op, left, right, *args):
        raise RuntimeError, "abstract method."
        
class OP_OR(AST_EVALUTOR):
    def __init__(self, evalutor):
        super(OP_OR, self).__init__(evalutor, "or", 1)

    def _evalutor(self, op, left, right, *args):
        return left(*args) or right(*args)
        
class OP_AND(AST_EVALUTOR):
    def __init__(self, evalutor):
        super(OP_AND, self).__init__(evalutor, "and", 2)

    def _evalutor(self, op, left, right, *args):
        return left(*args) and right(*args)
    
class OP_NOT(AST_EVALUTOR):
    def __init__(self, evalutor):
        super(OP_NOT, self).__init__(evalutor, "not", 3, 1)
        
    def evalute(self, *args):
        left = self.child[0].evalute
        return self._evalutor(self.op_sign, left, None, *args)         

    def _evalutor(self, op, left, dummy, *args):
        return not left(*args)
    
factor = re.compile('([()|&!]|[^()|&!]+)')
def compile_ast_tree(expr, parser = None, evalutor=None):
    # process the keyword arguments and put them into operationlist
    parser = parser == None and DummyParser or parser
    items = ( e.group(1) for e in factor.finditer(expr) )
    stack = [AST_Tree()]
    for item in items:
        if item == "|":
            stack[-1].insert_node(OP_OR(evalutor))
        elif item == "&":
            stack[-1].insert_node(OP_AND(evalutor))
        elif item == "!":
            stack[-1].insert_node(OP_NOT(evalutor))
        elif item == "(":
            stack.append(AST_Tree())
        elif item == ")":
            tree = stack.pop()
            if len(stack) < 1:
                raise RuntimeError, "expression ')' more than '('"
            stack[-1].insert_node(tree)
        elif item.strip() != "":
            stack[-1].insert_node(AST_Leaf(parser(item.strip())))
    
    if len(stack) != 1:
        raise RuntimeError, "expression lost ')'"
    return stack[0]

class DummyParser():
    def __init__(self, expr):
        self.op = expr
    def __call__(self, *args):
        #return bool(self.op)
        return self.op
    def __str__(self):
        return self.op
