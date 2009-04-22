#!/usr/bin/env python
'''$Id: pylorus.py,v 1.3 2009-04-22 15:33:37 mrbax Exp $
Compares AU content hashes between servers, two local and one remote.
Servers are randomly selected from available pools for each AU.
The process is divided into stages to improve parallel efficiency.
Reads configuration from $0.conf, with command-line overrides.'''

import ConfigParser
import cStringIO
import getpass
import logging
import optparse
import os
import random
import subprocess
import sys
import tempfile

program_directory = os.path.dirname( sys.argv[ 0 ] )
sys.path.append( os.path.realpath( os.path.join( program_directory, '../lib' ) ) )
import lockss_daemon


# Constants
PYLORUS = 'Pylorus'
REVISION = '$Revision: 1.3 $'.split()[ 1 ]
DEFAULT_UI_PORT = 8081
DEFAULT_V3_PORT = 8801
CRAWL_CHECK_TIMEOUT = 10
REMOTE_CRAWL_RETRY_TOTAL = 3
DEFAULT_AGREEMENT_THRESHOLD = 95


class Leaving_Pipeline( Exception ):
    '''Thrown to bypass further processing of an AU'''
    pass


class Content:
    '''Content processes itself through the pipeline'''

    status_strings = (
        'IN PROGRESS',
        'ADD FAILURE',
        'CRAWL FAILURE',
        'HASH FAILURE',
        'HASH MISMATCH',
        'HASH MATCH'
    )

    class Status:
        pass

    def __init__( self, AU, local_clients, remote_clients ):
        self.AU = AU
        self.local_clients = local_clients[ : ]
        self.remote_clients = remote_clients[ : ]
        self.clients = self.local_clients
        self.remote_crawl_retries = REMOTE_CRAWL_RETRY_TOTAL
        self.crawl_failures = []
        self.hash_records = []
        self.status = ( Content.Status.IN_PROGRESS, )
        self.stage = self.check
    
    def strip_comments( self, commented_lines ):
        '''Remove potentially time-varying comments from a hash file'''
        commented_file = cStringIO.StringIO( commented_lines )
        uncommented_file = cStringIO.StringIO()
        for line in commented_file:
            if not line.startswith( '#' ):
                uncommented_file.write( line ) 
        return uncommented_file.getvalue()

    def diff( self, hash_1, hash_2 ):
        '''Returns the difference between two hashes found using diff and grep'''
        hash_1_file = open( tempfile.mkstemp()[ 1 ], 'wb' )
        hash_1_file.write( hash_1 )
        hash_1_file.close()
        hash_2_file = open( tempfile.mkstemp()[ 1 ], 'wb' )
        hash_2_file.write( hash_2 )
        hash_2_file.close()
        diff_output = subprocess.Popen( [ 'diff', '-U0', hash_1_file.name, hash_2_file.name ], stdout = subprocess.PIPE )
        grep_output = subprocess.Popen( [ 'grep', '^[+-][^+-]' ], stdin = diff_output.stdout, stdout = subprocess.PIPE ).communicate()[ 0 ]
        os.remove( hash_1_file.name )
        os.remove( hash_2_file.name )
        return grep_output.strip()

    def check( self ):
        '''Does the server already have this AU?'''
        self.client = self.clients.pop( random.randrange( len( self.clients ) ) )
        self.server_name = client_ID( self.client )
        logging.info( 'Checking for AU "%s" on server %s' % ( self.AU, self.server_name ) )
        self.pre_existent = self.client.hasAu( self.AU )
        if self.pre_existent:
            logging.debug( 'Found AU "%s" on server %s' % ( self.AU, self.server_name ) )
            self.stage = self.crawl
        else:
            logging.debug( 'Did not find AU "%s" on server %s' % ( self.AU, self.server_name ) )
            self.stage = self.add
        
    def add( self ):
        '''Add this AU to the server'''
        logging.info( 'Adding AU "%s" to server %s' % ( self.AU, self.server_name ) )
        try:
            self.client.createAu( self.AU )
        except lockss_daemon.LockssError, exception:
            logging.error( exception )
            logging.warn( 'Failed to add AU "%s" to server %s' % ( self.AU, self.server_name ) )
            self.status = ( Content.Status.ADD_FAILURE, )
            raise Leaving_Pipeline
        logging.debug( 'Added AU "%s" to server %s' % ( self.AU, self.server_name ) )
        self.stage = self.crawl
        
    def remove( self ):
        '''Remove this AU from the server'''
        if not self.pre_existent:
            logging.info( 'Removing AU "%s" from server %s' % ( self.AU, self.server_name ) )
            self.client.deleteAu( self.AU )
            logging.debug( 'Removed AU "%s" from server %s' % ( self.AU, self.server_name ) )
    
    def crawl( self ):
        '''Crawl this AU on the server'''
        logging.info( 'Waiting for crawl of AU "%s" on server %s' % ( self.AU, self.server_name ) )
        try:
            crawl_succeeded = self.client.waitForSuccessfulCrawl( self.AU, CRAWL_CHECK_TIMEOUT )
        except lockss_daemon.LockssError, exception:
            logging.error( exception )
            logging.warn( 'Failed to crawl AU "%s" on server %s' % ( self.AU, self.server_name ) )
            self.crawl_failures.append( self.client )
            if self.clients == self.remote_clients:
                self.remote_crawl_retries -= 1
                if self.remote_clients and self.remote_crawl_retries:
                    self.stage = self.check
                    return
            # Local or repeated remote crawl failures are fatal
            #self.remove()  # Leave AU on server for inspection
            self.status = ( Content.Status.CRAWL_FAILURE, )
            raise Leaving_Pipeline
        if crawl_succeeded:
            logging.debug( 'Completed crawl of AU "%s" on server %s' % ( self.AU, self.server_name ) )
            self.stage = self.hash
    
    def hash( self ):
        '''Hash this AU on the server'''
        logging.info( 'Waiting for hash of AU "%s"' % self.AU )
        hash_file = self.client.getAuHashFile( self.AU )
        self.remove()
        if not hash_file:
            logging.warn( 'Hash failure for AU "%s"' % self.AU )
            self.status = ( Content.Status.HASH_FAILURE, )
            raise Leaving_Pipeline # Only if no remote clients left?
        logging.debug( 'Received hash of AU "%s"' % self.AU )
        self.hash_records.append( [ self.client, self.strip_comments( hash_file ) ] )
        if len( self.hash_records ) == 1:
            self.stage = self.check
        else:
            self.stage = self.compare

    def compare( self ):
        '''Compare server hashes of this AU'''
        local_comparison = len( self.hash_records ) == 2
        location = 'Local' if local_comparison else 'Remote'
        clients, hashes = zip( *self.hash_records )
        difference = self.diff( hashes[ 0 ], hashes[ -1 ] )
        agreement = 100 - 100*difference.count( '\n' )/( hashes[ 0 ].count( '\n' ) + hashes[ -1 ].count( '\n' ) )
        self.hash_records[ -1 ] += difference, agreement
        if agreement >= options.agreement:
            logging.info( location + ' hash file match for AU "%s"' % self.AU )
            if local_comparison and self.remote_clients:
                self.clients = self.remote_clients
                self.stage = self.check
                return
            else:
                self.status = ( Content.Status.HASH_MATCH, self.hash_records )
        else:
            logging.warn( location + ' hash file mismatch for AU "%s"' % self.AU )
            self.status = ( Content.Status.HASH_MISMATCH, self.hash_records )
        raise Leaving_Pipeline
    
    def output( self, heading = True ):
        print
        if heading:
            print Content.status_strings[ self.status[ 0 ] ] + ':\n'
        print self.AU.auId
        if self.status[ 0 ] in ( Content.Status.ADD_FAILURE, Content.Status.HASH_FAILURE ):
            print '\t', client_ID( self.client )
        elif self.status[ 0 ] is Content.Status.CRAWL_FAILURE:
            for client in self.crawl_failures:
                print '\t', client_ID( client )
        elif self.status[ 0 ] in ( Content.Status.HASH_MISMATCH, Content.Status.HASH_MATCH ):
            reference_client = self.status[ 1 ][ 0 ][ 0 ]
            for client, hash, difference, agreement in self.status[ 1 ][ 1 : ]:
                print '\t%i%% agreement between %s and %s' % ( agreement, client_ID( reference_client ), client_ID( client ) )
                if difference:
                    print '\t\t', difference.replace( '\n', '\n\t\t' )
        if heading:
            print

    for index, name in enumerate( status_strings ):
        setattr( Status, name.upper().replace( ' ', '_' ), index )


def client_ID( client ):
    '''Standardized notation'''
    return client.hostname + ':' + str( client.port )


def self_test_startup():
    '''Sets up a framework for simulated AU testing'''

    global framework
    framework = lockss_daemon.Framework( 4 )

    peer_list = ';'.join( [ client.getPeerId() for client in framework.clientList ] )
    for client in framework.clientList:
        framework.appendLocalConfig( { 'org.lockss.auconfig.allowEditDefaultOnlyParams': 'true',
                                       'org.lockss.id.initialV3PeerList': peer_list,
                                       'org.lockss.localV3Identity': client.getPeerId(),
                                       'org.lockss.poll.v3.enableV3Poller':'true',
                                       'org.lockss.poll.v3.enableV3Voter': 'true' },
                                     client )

    logging.info( 'Starting framework in %s', framework.frameworkDir )
    framework.start()
    assert framework.isRunning, 'Framework failed to start'

    logging.info( 'Waiting for framework to become ready' )
    framework.waitForFrameworkReady()

    AUs = [ lockss_daemon.AU( 'a|particularly|InvalidPlugin|&base_url~http%3A%2F%2Fwww%2Eexample%2Ecom%2F' ) ]

    simulated_AU_roots = ( 'URLs_missing', 'Major_differences', 'Minor_differences', 'Identical' )
    AUs += [ lockss_daemon.SimulatedAu( simulated_AU_roots[ i ], depth = 0, branch = 0, numFiles = 10, binFileSize = 1024, fileTypes = [ lockss_daemon.FILE_TYPE_TEXT, lockss_daemon.FILE_TYPE_BIN ], protocolVersion = 3 ) for i in range( len( simulated_AU_roots ) ) ]

    crawl_failure_AU, badly_damaged_AU, slightly_damaged_AU = AUs[ 1 : 4 ]
    client = framework.clientList[ 0 ]

    logging.info( "Pre-loading AU with missing URL's on server " + client_ID( client ) )
    client.createAu( crawl_failure_AU )
    client.waitAu( crawl_failure_AU )
    crawl_failure_AU.numFiles = 12

    logging.info( 'Pre-loading AU with major differences on server ' + client_ID( client ) )
    client.createAu( badly_damaged_AU )
    client.waitAu( badly_damaged_AU )
    client.waitForSuccessfulCrawl( badly_damaged_AU )
    client.randomDamageRandomNodes( badly_damaged_AU, 2, 2 )

    logging.info( 'Pre-loading AU with minor differences on server ' + client_ID( client ) )
    client.createAu( slightly_damaged_AU )
    client.waitAu( slightly_damaged_AU )
    client.waitForSuccessfulCrawl( slightly_damaged_AU )
    client.randomDamageSingleNode( slightly_damaged_AU )
    #assert not client.compareNode( node, simAu, victim, self.nonVictim ), "Failed to damage AU"
    #log.info( "Damaged node %s on client %s" % ( node.url, victim ) )

    return AUs, framework.clientList[ : 2 ], framework.clientList[ 2 : ]


def self_test_shutdown():
    '''Housekeeping'''
    #return
    if 'framework' in globals():
        if framework.isRunning:
            logging.info( 'Stopping framework' )
            framework.stop()
        logging.info( 'Cleaning up framework' )
        framework.clean()


option_defaults = { 'configuration': os.path.normpath( os.path.join( program_directory, os.path.splitext( os.path.basename( sys.argv[ 0 ] ) )[ 0 ] + '.conf' ) ), 'username': 'test', 'password': 'test', 'local_servers': [], 'remote_servers': [], 'agreement': DEFAULT_AGREEMENT_THRESHOLD, 'immediate': False, 'AU_IDs': [], 'test': False }

option_parser = optparse.OptionParser( usage = "usage: %prog [options] [AU's]", version = '%prog $Revision: 1.3 $', description = 'LOCKSS content gateway tester' )
option_parser.set_defaults( **option_defaults )
option_parser.add_option( '-c', '--configuration', help = 'read configuration from CONFIGURATION [%default]' )
option_parser.add_option( '-u', '--username', help = 'LOCKSS server username [%default]' )
option_parser.add_option( '-p', '--password', help = 'LOCKSS server password [%default]' )
option_parser.add_option( '-l', '--local', action = 'append', dest = 'local_servers', help = 'use local server LOCAL_SERVER (host:port)', metavar = 'LOCAL_SERVER' )
option_parser.add_option( '-r', '--remote', action = 'append', dest = 'remote_servers', help = 'use remote server REMOTE_SERVER (host:port)', metavar = 'REMOTE_SERVER' )
option_parser.add_option( '-a', '--agreement', help = 'threshold agreement percentage [%default]' )
option_parser.add_option( '-i', '--immediate', action = 'store_true', help = 'output results as soon as they are known' )
option_parser.add_option( '-A', '--AU_ID', action = 'append', dest = 'AU_IDs', help = 'test content of AU_ID', metavar = 'AU_ID' )
option_parser.add_option( '-t', '--test', action = 'store_true', help = 'run self test' )
( options, AUs ) = option_parser.parse_args()

configuration = ConfigParser.RawConfigParser()
configuration.read( options.configuration )

# Merge configuration file settings subject to command-line override
for key in option_defaults:
    if configuration.has_option( PYLORUS, key ) and not getattr( options, key ):
        if type( getattr( options, key ) ) is list:
            setattr( options, key, configuration.get( PYLORUS, key ).strip().split( '\n' ) )
        else:
            setattr( options, key, configuration.get( PYLORUS, key ) )

if options.username == '':
    print 'Username:',
    options.username = raw_input()

if options.password == '':
    options.password = getpass.getpass()

logging.basicConfig( datefmt='%T', format='%(asctime)s.%(msecs)03d: %(levelname)s: %(message)s', level = logging.INFO )
 
try:
    if options.test:
        AUs, local_clients, remote_clients = self_test_startup()
    else:
        local_clients = []
        for server in options.local_servers:
            hostname, UI_port = server.split( ':' ) if ':' in server else ( server, DEFAULT_UI_PORT )
            local_clients.append( lockss_daemon.Client( hostname, UI_port, DEFAULT_V3_PORT, options.username, options.password ) )
        assert len( local_clients ) >= 2, 'Requires at least two local servers'
        remote_clients = []
        for server in options.remote_servers:
            hostname, UI_port = server.split( ':' ) if ':' in server else ( server, DEFAULT_UI_PORT )
            remote_clients.append( lockss_daemon.Client( hostname, UI_port, DEFAULT_V3_PORT, options.username, options.password ) )
        AUs = [ lockss_daemon.AU( AU_ID ) for AU_ID in options.AU_IDs ]

    logging.info( 'Waiting for local servers' )
    for client in local_clients:
        client.waitForDaemonReady()

    logging.info( 'Waiting for remote servers' )
    for client in remote_clients:
        client.waitForDaemonReady()

    pipeline = [ Content( AU, local_clients, remote_clients ) for AU in AUs ]
    processed_content = []
    initial_length = len( pipeline )
    cycle = 0

    # The main loop
    while pipeline:
        logging.info( 'Cycle %i: pipeline contains %i%% of initial content' % ( cycle, 100*len( pipeline )/initial_length ) )
        next_pipeline = []
        for content in pipeline:
            try:
                content.stage() # It's a kind of magic...
                next_pipeline.append( content )
            except Leaving_Pipeline:
                if options.immediate:
                    content.output()
                else:
                    processed_content.append( content )
            except Exception, exception:
                # Unhandled exception
                logging.critical( exception )
                raise
        pipeline = next_pipeline
        cycle += 1

finally:
    if options.test:
        self_test_shutdown()

logging.info( 'Finished' )

if processed_content:

    results = [ [] for status_string in Content.status_strings ]
    for content in processed_content:
        results[ content.status[ 0 ] ].append( content )

    plural_endings = { 'E': 'S', 'H': 'ES' }
    for index, AUs in enumerate( results ):
        if AUs:
            heading = Content.status_strings[ index ]
            print '\n\n' + heading + plural_endings[ heading[ -1 ] ] + ':'
            for AU in AUs:
                AU.output( False )
