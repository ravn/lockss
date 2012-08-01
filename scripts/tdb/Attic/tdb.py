#!/usr/bin/env python

# $Id: tdb.py,v 1.15 2012-08-01 18:55:54 thib_gc Exp $

__copyright__ = '''\

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.
'''

__version__ = '0.4.1'

import re

class MapError(Exception):
  pass

class Map(object):

  def __init__(self, parent=None):
    if parent is not None and not isinstance(parent, Map): raise MapError('parent must be of type Map')
    object.__init__(self)
    self._map = {}
    self._parent = parent

  def get(self, key):
    if key is None: raise MapError('key must not be None')
    typ = type(key)
    if typ == str or typ == unicode: key = (key,)
    if type(key) != tuple: raise MapError('key must be a tuple or a string')
    if len(key) == 0: raise MapError('key must not be the empty tuple')
    return self._internal_get(key)

  def _internal_get(self, key):
    ret = self._map.get(key)
    if ret is None and self._parent is not None: ret = self._parent._internal_get(key)
    return ret

  def get_prefix(self, prefix):
    if prefix is None: raise MapError('prefix must not be None')
    typ = type(prefix)
    if typ == str or typ == unicode: prefix = (prefix,)
    if type(prefix) != tuple: raise MapError('prefix must be a tuple or a string')
    if len(prefix) == 0: raise MapError('prefix must not be the empty tuple')
    return self._internal_get_prefix(prefix)

  def _internal_get_prefix(self, prefix):
    if self._parent is not None: ret = self._parent._internal_get_prefix(prefix)
    else: ret = dict()
    lp = len(prefix)
    for k, v in self._map.iteritems():
      if len(k) == lp + 1 and k[0:-1] == prefix: ret[k[-1]] = v
    return ret

  def set(self, key, value):
    if key is None: raise MapError('key must not be None')
    typ = type(key)
    if typ == str or typ == unicode: key = (key,)
    if type(key) != tuple: raise MapError('key must be a tuple or a string')
    if len(key) == 0: raise MapError('key must not be the empty tuple')
    if key in self._map: raise MapError('key is already defined')
    self._internal_set(key, value)

  def _internal_set(self, key, value):
    self._map[key] = value

class Publisher(Map):
    '''A TDB Publisher object.'''
    
    NAME = ('name',)
    
    def __init__(self):
        '''Constructor.'''
        super(Publisher, self).__init__()
    
    def name(self): return self._internal_get(Publisher.NAME)

class Title(Map):
  '''A TDB Title object.'''

  EISBN = ('eisbn',)
  EISSN = ('eissn',)
  ISBN = ('isbn',)
  ISSN = ('issn',)
  ISSNL = ('issnl',)
  NAME = ('name',)
  PUBLISHER = ('publisher',)
  TYPE = ('type',)
  class Type:
    BOOK = 'book'
    JOURNAL = 'journal'
    DEFAULT = JOURNAL

  def __init__(self):
    '''Constructor.'''
    super(Title, self).__init__()
    
  def set_publisher(self, publisher): self._internal_set(Title.PUBLISHER, publisher)

  def name(self): return self._internal_get(Title.NAME)
  def eisbn(self): return self._internal_get(Title.EISBN)
  def eissn(self): return self._internal_get(Title.EISSN)
  def isbn(self): return self._internal_get(Title.ISBN)
  def issn(self): return self._internal_get(Title.ISSN)
  def issnl(self): return self._internal_get(Title.ISSNL)
  def publisher(self): return self._internal_get(Title.PUBLISHER)
  def type(self): return self._internal_get(Title.TYPE) or Title.Type.DEFAULT

class AU(Map):
  '''A TDB AU object.'''

  RE_AUIDCHAR = re.compile(r'^[-_*A-Za-z0-9]$')

  class Status:
    DOES_NOT_EXIST = 'doesNotExist'
    DO_NOT_PROCESS = 'doNotProcess'
    EXISTS = 'exists'
    EXPECTED = 'expected'
    MANIFEST = 'manifest'
    WANTED = 'wanted'
    CRAWLING = 'crawling'
    TESTING = 'testing'
    NOT_READY = 'notReady'
    READY = 'ready'
    RELEASING = 'releasing'
    RELEASED = 'released'
    DOWN = 'down'
    SUPERSEDED = 'superseded'
    ZAPPED = 'zapped'

  ATTR = ('attr',)
  EDITION = ('edition',)
  EISBN = ('eisbn',)
  ISBN = ('isbn',)
  NAME = ('name',)
  NONDEFPARAM = ('nondefparam',)
  PARAM = ('param',)
  PLUGIN = ('plugin',)
  PLUGIN_PREFIX = ('pluginPrefix',)
  PLUGIN_SUFFIX = ('pluginSuffix',)
  PROXY = ('proxy',)
  RIGHTS = ('rights',)
  STATUS = ('status',)
  TITLE = ('title',)
  YEAR = ('year',)
  VOLUME = ('volume',)
    
  def __init__(self, next=None):
    '''Constructor.'''
    super(AU, self).__init__(next)
    
  def set_title(self, title): self._internal_set(AU.TITLE, title)

  def attr(self, attr): return self._internal_get((AU.ATTR[0], attr))
  def attrs(self): return self._internal_get_prefix(AU.ATTR)
  def auid(self): return AU.compute_auid(self.plugin(), self.params())
  def edition(self): return self._internal_get(AU.EDITION)
  def eisbn(self): return self._internal_get(AU.EISBN)
  def isbn(self): return self._internal_get(AU.ISBN)
  def name(self): return self._internal_get(AU.NAME)
  def nondefparam(self, nondefparam): return self._internal_get((AU.NONDEFPARAM[0], nondefparam))
  def nondefparams(self): return self._internal_get_prefix(AU.NONDEFPARAM)
  def param(self, param): return self._internal_get((AU.PARAM[0], param))
  def params(self): return self._internal_get_prefix(AU.PARAM)
  def plugin(self): return self._internal_get(AU.PLUGIN) or (self.get(AU.PLUGIN_PREFIX) + self.get(AU.PLUGIN_SUFFIX))
  def proxy(self):
    val = self._internal_get(AU.PROXY)
    if val is None or len(val) == 0: return None
    else: return val
  def rights(self): return self._internal_get(AU.RIGHTS)
  def status(self): return self._internal_get(AU.STATUS)
  def title(self): return self._internal_get(AU.TITLE)
  def year(self): return self._internal_get(AU.YEAR)
  def volume(self): return self._internal_get(AU.VOLUME)

  @staticmethod
  def auid_encode(c):
    if AU.RE_AUIDCHAR.match(c): return c
    if c == ' ': return '+'
    return '%' + ('0' if ord(c) < 16 else '') + hex(ord(c))[2:].upper()

  @staticmethod
  def compute_auid(plugin, params):
    keys = params.keys()
    keys.sort() # in Java: by iterating over a TreeSet
    return plugin.replace('.', '|') + '&' + '&'.join(['~'.join([''.join(map(AU.auid_encode, [c for c in s])) for s in [k, params[k]]]) for k in keys])

class Tdb(object):

    def __init__(self):
        '''Constructor.'''
        self.__publishers = []
        self.__titles = []
        self.__aus = []

    def add_publisher(self, publisher): self.__publishers.append(publisher)
    def add_title(self, title): self.__titles.append(title)
    def add_au(self, au): self.__aus.append(au)

    def publishers(self): return self.__publishers[:]
    def titles(self): return self.__titles[:]
    def aus(self): return self.__aus[:]
