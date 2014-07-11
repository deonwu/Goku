import time, re
    
class Benchmark(object):
    def __init__(self, logger):
        self.logger = logger
        self.marks = []
        
    def start_mark(self, message=None):
        self.marks.append((message, time.time()))
        self.logger.debug("start %s ..." % message)
        
    def stop_mark(self, ):
        message, st = self.marks.pop()
        et = time.time() - st
        self.logger.debug("since '%s' elapsed '%s'" % (message, et))
        
