#!/usr/bin/env python

# $Id$

__copyright__ = '''\
Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

__version__ = '0.1.1'

import getpass
import optparse
import sys

from daemonstatusservice import DaemonStatusServiceOptions, datetime_from_ms, \
  get_platform_configuration, is_daemon_ready, query_aus

class _EvaluateClosedGroupOptions(object):
  def __init__(self, parser, opts, args):
    super(_EvaluateClosedGroupOptions, self).__init__()
    if len(args) != 1: parser.error('Exactly one AUID is required')
    self.__auid = args[0]
    self.__hosts = opts.host
    for f in opts.hosts: self.__hosts.extend(_file_lines(f))
    if len(self.__hosts) < 2: parser.error('At least two hosts are required')
    self.__u = opts.username or raw_input('UI username: ')
    self.__p = opts.password or getpass.getpass('UI password: ')
  def get_auid(self): return self.__auid
  def get_hosts(self): return self.__hosts
  def set_auth(self, dssopts): dssopts.set_auth(self.__u, self.__p)
  @staticmethod
  def make_parser():
    parser = optparse.OptionParser(version=__version__, usage='%prog [OPTIONS] AUID')
    parser.add_option('--host', action='append', default=list(), help='adds host:port pair to the list of hosts')
    parser.add_option('--hosts', action='append', default=list(), metavar='HFILE', help='adds host:port pairs from HFILE to the list of hosts')
    parser.add_option('--password', metavar='PASS', help='UI password')
    parser.add_option('--username', metavar='USER', help='UI username')
    return parser
   
def _do_evaluate(options):
  print 'AUID: %s' % (options.get_auid(),)
  quit = False
  hosts = sorted(options.get_hosts())
  # Make actionable objects
  host_dssopts = dict()
  query = 'SELECT * WHERE auId = "%s"' % (options.get_auid(),)
  for host in hosts:
    dssopts = DaemonStatusServiceOptions()
    dssopts.set_host(host)
    options.set_auth(dssopts)
    dssopts.set_query(query)
    host_dssopts[host] = dssopts
  # Check that all hosts are up and ready
  for host in hosts:
    if not is_daemon_ready(host_dssopts[host]):
      sys.exit('Error: %s is not up and ready' % (host,))
  # Get peer identities
  host_peerid = dict()
  peerid_host = dict()
  for host in hosts:
    peerid = get_platform_configuration(host_dssopts[host]).V3Identity
    host_peerid[host] = peerid
    peerid_host[peerid] = host
  # Get AU data
  host_audata = dict()
  for host in hosts:
    r = query_aus(host_dssopts[host])
    if r is None or len(r) == 0:
      quit = True
      print 'AU not found on %s' % (host,)
      continue
    host_audata[host] = r[0]
  if quit: sys.exit(1)
  # Check basic AU data
  lrlcc = None
  lrlcp = None
  for host in hosts:
    audata = host_audata[host]
    lcc = audata.LastCompletedCrawl
    if lcc is None or lcc == 0:
      quit = True
      print '%s: AU has never crawled successfully (created %s)' % (host, datetime_from_ms(audata.CreationTime))
      continue
    if lrlcc is None or lrlcc > lcc: lrlcc = lcc
    ss = audata.SubstanceState
    if ss is None or ss != 'Yes':
      quit = True
      print '%s: AU does not have substance' % (host,)
      continue
    lcp = audata.LastCompletedPoll
    if lcp is None or lcp == 0:
      quit = True
      print '%s: AU has never polled successfully (created %s)' % (host, datetime_from_ms(audata.CreationTime))
      continue
    if lrlcp is None or lrlcp > lcp: lrlcp = lcp
  if quit: sys.exit(1)
  print 'Least recent last completed crawl: %s' % (datetime_from_ms(lrlcc),)
  print 'Least recent last completed poll: %s' % (datetime_from_ms(lrlcp),)
  # Compute agreement matrix
  agmat = dict()
  for host1 in hosts:
    d1 = dict()
    for host2 in hosts:
      if host1 == host2: continue
      d1[host_peerid[host2]] = None
    pa = host_audata[host1].PeerAgreements
    for pae in pa:
      if pae.PeerId not in peerid_host: continue # Peer ID outside the closed group
      for ae in pae.Agreements.Entry:
        if not (ae.Key == 'POR' or ae.Key == 'SYMMETRIC_POR'): continue # Not a POR-type agreement
        agcur = d1[pae.PeerId]
        agnew = (ae.Value.PercentAgreementTimestamp, ae.Value.PercentAgreement)
        if agcur is None or agcur < agnew: d1[pae.PeerId] = agnew
    agmat[host_peerid[host1]] = d1
  # Direct agreement
  cand = 0
  mrmin = None
  mrhost = None
  for host1 in hosts:
    ag1 = filter(lambda x: x[1] == 1.0, agmat[host_peerid[host1]].values())
    if len(ag1) == len(hosts) - 1:
      cand = cand + 1
      min1 = min([x[0] for x in ag1])
      print '%s agrees 100%% with all others since %s' % (host1, datetime_from_ms(min1))
      if mrmin is None or mrmin < min1: mrmin, mrhost = min1, host1
  if mrmin is not None:
    print 'Winner: %s agrees 100%% with all others since %s' % (mrhost, datetime_from_ms(mrmin))
    print 'PASS%d: %s' % (cand, options.get_auid())
  else:
    print 'FAIL: %s' % (options.get_auid(),)

def _file_lines(filestr):
  ret = [line.strip() for line in open(filestr).readlines() if not (line.isspace() or line.startswith('#'))]
  if len(ret) == 0: sys.exit('Error: %s contains no meaningful lines' % (filestr,))
  return ret

if __name__ == '__main__':
  parser = _EvaluateClosedGroupOptions.make_parser()
  (opts, args) = parser.parse_args()
  options = _EvaluateClosedGroupOptions(parser, opts, args)
  _do_evaluate(options)
