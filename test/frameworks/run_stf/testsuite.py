#!/usr/bin/python
"""
This test suite requires at minimum a top-level work directory to
build frameworks in.  Optional parameters may also be set, if desired,
to change the default behavior.  See the file for details.
"""
import sys, time, unittest, os
from lockss_util import *

##
## Load configuration.
##
loadConfig('./testsuite.props')

from lockss_daemon import *

##
## module globals
##
log = Logger()
frameworkList = []
deleteAfterSuccess = config.getBoolean('deleteAfterSuccess', True)

##
## Super class for all LOCKSS daemon test cases.
##
class LockssTestCase(unittest.TestCase):
    """ Superclass for all STF test cases. """
    def __init__(self):
        unittest.TestCase.__init__(self)

        self.delayShutdown = config.get('delayShutdown', False)
        self.timeout = int(config.get('timeout', 60 * 60 * 8))

        ##
        ## assert that the workDir exists and is writable.
        ##
        self.workDir = config.get('workDir', './')
        if not (os.path.isdir(self.workDir) and \
                os.access(self.workDir, os.W_OK)):
            raise LockssError("Work dir %s does not exist or is not writable." \
                              % self.workDir)

    def setUp(self):
        ## Log start of test.
        log.info("====================================================================")
        log.info(self.__doc__)
        log.info("--------------------------------------------------------------------")
        
        ##
        ## Create a framework for the test.
        ##
        try:
            self.framework = Framework()
        except Exception, e:
            self.fail("Unable to continue: %s" % e)

        ## global ('static') reference to the current framework, so we
        ## can clean up after a user interruption
        global frameworkList
        frameworkList.append(self.framework)

        ##
        ## Start the framework.
        ##
        log.info("Starting framework in %s" % self.framework.frameworkDir)
        self.framework.start()
        assert self.framework.isRunning, 'Framework failed to start.'

        ##
        ## List of clients, one for each daemon in 'numDaemons'
        ##
        self.clients = self.framework.clientList

        # Block return until all clients are ready to go.
        log.info("Waiting for framework to come ready.")
        for client in self.clients:
            client.waitForDaemonReady()

        unittest.TestCase.setUp(self)


    def tearDown(self):
        # dump threads and look for deadlocks.  This will happen
        # whether or not there was a failure.
        deadlockLogs = self.framework.checkForDeadlock()
        if deadlockLogs:
            log.warn("Deadlocks detected!")
            self.fail("Failing due to deadlock detection.  Check the "
                      "following log file(s): %s" % ", ".join(deadlockLogs))
        else:
            log.info("No deadlocks detected.")

        if self.delayShutdown:
            raw_input(">>> Delaying shutdown.  Press any key to continue...")

        self.framework.stop()
        self.failIf(self.framework.isRunning,
                    'Framework did not stop.')

        unittest.TestCase.tearDown(self)

##
## Sanity check self-test cases.  Please ignore these.
##

class SucceedingTestTestCase(LockssTestCase):
    " Test case that succeeds immediately after daemons start. "
    def runTest(self):
        log.info("Succeeding immediately.")
        return

class FailingTestTestCase(LockssTestCase):
    " Test case that fails immediately after daemons start. "    
    def runTest(self):
        log.info("Failing immediately.")
        self.fail("Failed on purpose.")

class ImmediateSucceedingTestTestCase(unittest.TestCase):
    " Test case that succeeds immediately, without starting the daemons. "
    def runTest(self):
	return

class ImmediateFailingTestTestCase(unittest.TestCase):
    " Test case that fails immediately, without starting the daemons. "
    def runTest(self):
        log.info("Failing immediately.")
        self.fail("Failed on purpose.")

###########################################################################
## Test cases.  Add test cases here, as well as to the appropriate
## TestSuite-creating method below.
###########################################################################

##
## Ensure caches can recover from simple file damage.
##
class SimpleDamageTestCase(LockssTestCase):
    "Test recovery from random file damage."
    
    def runTest(self):
        # Tiny AU for simple testing.
        simAu = SimulatedAu('localA', 0, 0, 3)

        ##
        ## Create simulated AUs
        ##
        log.info("Creating simulated AUs.")
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never completed initial crawl.")
        log.info("AUs completed initial crawl.")

        ## To select a random client, uncomment this line.
        # client = self.clients[random.randint(0, len(clients) - 1)]

        ## To use a specific client, uncomment this line.
        client = self.clients[0]

        node = client.randomDamageSingleNode(simAu)
        log.info("Damaged node %s on client %s" % (node.url, client))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked repairing.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll"
        log.info("Called top level name poll")

        # expect to see the specific node marked 'damaged'
        log.info("Waiting for node %s to be marked 'damaged'." % node.url)
        assert client.waitForDamage(simAu, node, timeout=self.timeout),\
               "Never marked node %s 'damaged'" % node.url
        log.info("Marked node %s 'damaged'" % node.url)

        # expect to see the node successfully repaired.
        log.info("Waiting for successful repair of node %s." % node.url)
        assert client.waitForContentRepair(simAu, node, timeout=self.timeout),\
               "Node %s not repaired." % node.url
        log.info("Node %s repaired." % node.url)

        # expect to see the AU successfully repaired
        log.info("Waiting for successful repair of AU.")
        assert client.waitForTopLevelRepair(simAu, timeout=self.timeout),\
               "AU never repaired."
        log.info("AU successfully repaired.")


##
## Ensure the cache can recover from a simple file deletion.
## (not resulting in a ranged name poll)
##
class SimpleDeleteTestCase(LockssTestCase):
    "Test recovery from a random file deletion."
    
    def runTest(self):
        # Tiny AU for simple testing.
        simAu = SimulatedAu('localA', 0, 0, 3)

        ##
        ## Create simulated AUs
        ##
        log.info("Creating simulated AUs.")
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never completed initial crawl.")
        log.info("AUs completed initial crawl.")

        ## To select a random client, uncomment this line.
        # client = self.clients[random.randint(0, len(clients) - 1)]

        ## To use a specific client, uncomment this line.
        client = self.clients[1]

        node = client.randomDelete(simAu)
        log.info("Deleted node %s on client %s" % (node.url, client))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked repairing.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll"
        log.info("Called top level name poll")

        # expect to see the AU successfully repaired.
        log.info("Waiting for repair.")
        assert client.waitForNameRepair(simAu, timeout=self.timeout),\
               "Au not repaired."
        log.info("AU repaired.")

##
## Ensure that the cache can recover following an extra file created
## (not resulting in a ranged name poll)
##
class SimpleExtraFileTestCase(LockssTestCase):
    "Test recovery from an extra node in our cache"

    def runTest(self):
        # Tiny AU for simple testing.
        simAu = SimulatedAu('localA', 0, 0, 3)

        ##
        ## Create simulated AUs
        ##
        log.info("Creating simulated AUs.")
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never completed initial crawl.")
        log.info("AUs completed initial crawl.")

        ## To select a random client, uncomment this line.
        # client = self.clients[random.randint(0, len(clients) - 1)]

        ## To use a specific client, uncomment this line.
        client = self.clients[1]

        node = client.createNode(simAu, 'extrafile.txt')
        log.info("Created file %s on client %s" % (node.url, client))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked repairing.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll"
        log.info("Called top level name poll")

        #
        # TODO: Expect to see the node removed from the node list.
        #

        # expect to see the AU successfully repaired.
        log.info("Waiting for repair.")
        assert client.waitForTopLevelRepair(simAu, timeout=self.timeout),\
               "Au not repaired."
        log.info("AU repaired.")


##
## Ensure that the cache can recover following damage that results
## in a ranged name poll being called.
##
class RangedNamePollDeleteTestCase(LockssTestCase):
    "Test recovery from a file deletion after a ranged name poll"

    def runTest(self):
        # Long names, shallow depth, wide range for testing ranged polls
        simAu = SimulatedAu('localA', depth=0, branch=0,
                            numFiles=45, maxFileName=26)

        ##
        ## Create simulated AUs
        ##
        log.info("Creating simulated AUs.")
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never successfully completed initial crawl.")
        log.info("AUs successfully completed initial crawl.")

        ## To select a random client, uncomment this line.
        # client = self.clients[random.randint(0, len(clients) - 1)]

        ## To use a specific client, uncomment this line.
        client = self.clients[1]

        # get a file that will be in the second packet
        filename = '045abcdefghijklmnopqrstuvwxyz.txt'
        node = client.getAuNode(simAu,
                                "http://www.example.com/" + filename)
        client.deleteNode(node)
        log.info("Deleted node %s on client %s" % (node.url, client))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked damaged.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll."
        log.info("Called top level name poll")

        # expect to win a top level name poll (name poll on baseURL
        # should be lost later on in the test)
        log.info("Waiting to win top level name poll.")
        assert client.waitForWonTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never won top level name poll"
        log.info("Won top level name poll.")

        # expect to call a name poll on baes URL.
        node = client.getAuNode(simAu, simAu.baseUrl)
        log.info("Waiting to call name poll on %s" % node.url)
        assert client.waitForNamePoll(simAu, node, timeout=self.timeout),\
               "Never called name poll on %s" % node.url
        log.info("Called name poll on %s" % node.url)

        # expect to lose a name poll on baes URL.
        node = client.getAuNode(simAu, simAu.baseUrl)
        log.info("Waiting to lose name poll on %s" % node.url)
        assert client.waitForLostNamePoll(simAu, node, timeout=self.timeout),\
               "Never lost name poll on %s" % node.url
        log.info("Lost name poll on %s" % node.url)

        # expect to call a ranged name poll on baes URL.
        node = client.getAuNode(simAu, simAu.baseUrl)
        log.info("Waiting to call ranged name poll on %s" % node.url)
        assert client.waitForRangedNamePoll(simAu, node, timeout=self.timeout),\
               "Never called ranged name poll on %s" % node.url
        log.info("Called ranged name poll on %s" % node.url)

        # (Note: It turns that waiting for the ranged name poll to
        # fail is not the right thing to do here -- it may go from
        # 'Active' to 'Repaired' before the timer has a chance to
        # catch it.  So just go straight to expecting 'Repaired'

        # expect to see the AU successfully repaired.
        log.info("Waiting for successful repair.")
        assert client.waitForRangedNameRepair(simAu, timeout=self.timeout),\
               "AU not repaired by ranged name poll."
        log.info("AU repaired by ranged name poll.")


##
## Ensure that the cache can recover following an extra file being
## added to an AU large enough to trigger a ranged name poll.
##
class RangedNamePollExtraFileTestCase(LockssTestCase):
    "Test recovery from an extra file that triggers a ranged name poll"

    def runTest(self):
        # Long names, shallow depth, wide range for testing ranged polls
        simAu = SimulatedAu('localA', depth=0, branch=0,
                            numFiles=45, maxFileName=26)

        ##
        ## Create simulated AUs
        ##
        log.info("Creating simulated AUs.")
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never successfully completed initial crawl.")
        log.info("AUs successfully completed initial crawl.")

        ## To select a random client, uncomment this line.
        # client = self.clients[random.randint(0, len(clients) - 1)]

        ## To use a specific client, uncomment this line.
        client = self.clients[1]

        # Create a file that doesn't exist
        filename = '046extrafile.txt'
        node = client.createNode(simAu, filename)
        log.info("Created file %s on client %s" % (node.url, client))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked damaged.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll."
        log.info("Called top level name poll")

        # expect to win a top level name poll (name poll on baseURL
        # should be lost later on in the test)
        log.info("Waiting to win top level name poll.")
        assert client.waitForWonTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never won top level name poll"
        log.info("Won top level name poll.")

        # expect to call a name poll on base URL.
        baseUrlNode = client.getAuNode(simAu, simAu.baseUrl)
        log.info("Waiting to call name poll on %s" % baseUrlNode.url)
        assert client.waitForNamePoll(simAu, baseUrlNode, timeout=self.timeout),\
               "Never called name poll on %s" % baseUrlNode.url
        log.info("Called name poll on %s" % node.url)

        # expect to lose a name poll on baes URL.
        log.info("Waiting to lose name poll on %s" % baseUrlNode.url)
        assert client.waitForLostNamePoll(simAu, baseUrlNode, timeout=self.timeout),\
               "Never lost name poll on %s" % baseUrlNode.url
        log.info("Lost name poll on %s" % baseUrlNode.url)

        # expect to call a ranged name poll on base URL.
        node = client.getAuNode(simAu, simAu.baseUrl)
        log.info("Waiting to call ranged name poll on %s" % node.url)
        assert client.waitForRangedNamePoll(simAu, node, timeout=self.timeout),\
               "Never called ranged name poll on %s" % node.url
        log.info("Called ranged name poll on %s" % node.url)

        #
        # TODO: Expect to see the node removed from the node list.
        #

        # (Note: It turns that waiting for the ranged name poll to
        # fail is not the right thing to do here -- it may go from
        # 'Active' to 'Repaired' before the timer has a chance to
        # catch it.  So just go straight to expecting 'Repaired'

        # expect to see the AU successfully repaired.
        log.info("Waiting for successful repair.")
        assert client.waitForTopLevelRepair(simAu, timeout=self.timeout),\
               "AU never repaired."
        log.info("AU successfully repaired.")

##
## Create a randomly sized AU (with reasonable limits on maximum depth
## and breadth of the tree structure), and cause damage.  Wait for
## all damaged nodes to be repaired.
##

class RandomizedDamageTestCase(LockssTestCase):
    "Test recovery from random file damage in a randomly sized AU."

    def runTest(self):
	random.seed(time.time())
        depth = random.randint(0, 2)
        branch = random.randint(0, 2)
        numFiles = random.randint(3, 20)
        maxFileName = 26
        log.info("Creating simulated AUs: depth = %s; branch = %s; "
            "numFiles = %s; maxFileName = %s" %
            (depth, branch, numFiles, maxFileName))

        ##
        ## Create simulated AUs
        ##
        simAu = SimulatedAu(root='localA', depth=depth, branch=branch,
                            numFiles=numFiles, maxFileName=maxFileName)
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never completed initial crawl.")
        log.info("AUs completed initial crawl.")

        ## Pick a client at random
        client = self.clients[random.randint(0, len(self.clients) - 1)]

        log.info("Causing random damage...")
        nodeList = client.randomDamageRandomNodes(simAu, 1, 5)

        log.info("Damaged the following nodes on client %s:\n        %s" %
            (client, '\n        '.join([str(n) for n in nodeList])))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked repairing.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll"
        log.info("Called top level name poll")

        # TODO:
        # Need methods that watch a LIST of nodes, not just one
        # node.  For now, this is good enough, but improve
        # as soon as possible.
        node = nodeList[random.randint(0, len(nodeList) - 1)]

        # expect to see a specific node marked 'damaged'
        log.info("Waiting for node %s to be marked 'damaged'." % node.url)
        assert client.waitForDamage(simAu, node, timeout=self.timeout),\
               "Never marked node %s 'damaged'" % node.url
        log.info("Marked node %s 'damaged'" % node.url)

        # expect to see the node successfully repaired.
        log.info("Waiting for successful repair of node %s." % node.url)
        assert client.waitForContentRepair(simAu, node, timeout=self.timeout),\
               "Node %s not repaired." % node.url
        log.info("Node %s repaired." % node.url)

        # expect to see the AU successfully repaired.
        log.info("Waiting for successful repair of AU.")
        assert client.waitForTopLevelRepair(simAu, timeout=self.timeout),\
               "AU never repaired."
        log.info("AU repaired.")

##
## Create a randomly sized AU (with reasonable limits on maximum depth
## and breadth of the tree structure), and randomly remove files.  Wait
## for a successful repair
##

class RandomizedDeleteTestCase(LockssTestCase):
    "Test recovery from random file deletion in a randomly sized AU."

    def runTest(self):
	random.seed(time.time())
        depth = random.randint(0, 2)
        branch = random.randint(0, 2)
        numFiles = random.randint(3, 20)
        maxFileName = 26
        log.info("Creating simulated AUs: depth = %s; branch = %s; "
            "numFiles = %s; maxFileName = %s" %
            (depth, branch, numFiles, maxFileName))

        ##
        ## Create simulated AUs
        ##
        simAu = SimulatedAu(root='localA', depth=depth, branch=branch,
                            numFiles=numFiles, maxFileName=maxFileName)
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never completed initial crawl.")
        log.info("AUs completed initial crawl.")

        ## Pick a client at random
        client = self.clients[random.randint(0, len(self.clients) - 1)]

        log.info("Deleting random files...")
        nodeList = client.randomDeleteRandomNodes(simAu)

        log.info("Deleted the following nodes on client %s:\n        %s" %
            (client, '\n        '.join([str(n) for n in nodeList])))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked repairing.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll"
        log.info("Called top level name poll")

        # TODO: Definitely want more steps between these two.  Might
        # want to branch on whether it causes a ranged name poll or
        # not, too.

        # expect to see the AU successfully repaired.
        log.info("Waiting for successful repair of AU.")
        assert client.waitForTopLevelRepair(simAu, timeout=self.timeout),\
               "AU never repaired."
        log.info("AU repaired.")

##
## Create a randomly sized AU (with reasonable limits on maximum depth
## and breadth of the tree structure), and add extra files.  Wait for
## all damaged nodes to be repaired.
##
class RandomizedExtraFileTestCase(LockssTestCase):
    """ Test recovery from random node creation in a randomly sized AU. """

    def runTest(self):    
	random.seed(time.time())
        depth = random.randint(0, 2)
        branch = random.randint(0, 2)
        numFiles = random.randint(3, 20)
        maxFileName = 26
        log.info("Creating simulated AUs: depth = %s; branch = %s; "
            "numFiles = %s; maxFileName = %s" %
            (depth, branch, numFiles, maxFileName))

        ##
        ## Create simulated AUs
        ##
        simAu = SimulatedAu(root='localA', depth=depth, branch=branch,
                            numFiles=numFiles, maxFileName=maxFileName)
        for client in self.clients:
            client.createAu(simAu)

        ##
        ## Assert that the AUs have been crawled.
        ##
        log.info("Waiting for simulated AUs to crawl.")
        for client in self.clients:
            if not (client.waitForSuccessfulCrawl(simAu)):
                self.fail("AUs never completed initial crawl.")
        log.info("AUs completed initial crawl.")

        ## Pick a client at random
        client = self.clients[random.randint(0, len(self.clients) - 1)]

        log.info("Creating random nodes...")
        nodeList = client.randomCreateRandomNodes(simAu, 1, 5)

        log.info("Created the following nodes on client %s:\n        %s" %
                 (client, '\n        '.join([str(n) for n in nodeList])))

        # Request a tree walk (deactivate and reactivate AU)
        log.info("Requesting tree walk.")
        client.requestTreeWalk(simAu)

        # expect to see a top level content poll
        log.info("Waiting for top level content poll.")
        assert client.waitForTopLevelContentPoll(simAu, timeout=self.timeout),\
               "Never called top level content poll"
        log.info("Called top level content poll.")

        # expect to see the top level node marked repairing.
        log.info("Waiting for lockssau to be marked 'damaged'.")
        assert client.waitForTopLevelDamage(simAu, timeout=self.timeout),\
               "Client never marked lockssau 'damaged'."
        log.info("Marked lockssau 'damaged'.")

        # expect to see top level name poll
        log.info("Waiting for top level name poll.")
        assert client.waitForTopLevelNamePoll(simAu, timeout=self.timeout),\
               "Never called top level name poll"
        log.info("Called top level name poll")

        #
        # TODO: Expect to see the nodes removed from the node list.
        #

        # expect to see the AU successfully repaired.
        log.info("Waiting for repair.")
        assert client.waitForTopLevelRepair(simAu, timeout=self.timeout),\
               "Au not repaired."
        log.info("AU repaired.")

###########################################################################
### Functions that build and return test suites.  These can be
### called by name when running this test script.
###########################################################################

def all():
    suite = unittest.TestSuite()
    suite.addTest(simpleTests())
    suite.addTest(rangedTests())
    suite.addTest(randomTests())
    return suite

def simpleTests():
    suite = unittest.TestSuite()
    suite.addTest(SimpleDamageTestCase())
    suite.addTest(SimpleDeleteTestCase())
    suite.addTest(SimpleExtraFileTestCase())
    return suite

def rangedTests():
    suite = unittest.TestSuite()
    suite.addTest(RangedNamePollDeleteTestCase())
    suite.addTest(RangedNamePollExtraFileTestCase())
    return suite

def randomTests():
    suite = unittest.TestSuite()
    suite.addTest(RandomizedDamageTestCase())
    suite.addTest(RandomizedDeleteTestCase())
    suite.addTest(RandomizedExtraFileTestCase())
    return suite

def succeedingTests():
    suite = unittest.TestSuite()
    suite.addTest(SucceedingTestTestCase())
    return suite

def failingTests():
    suite = unittest.TestSuite()
    suite.addTest(FailingTestTestCase())
    return suite

def immediateSucceedingTests():
    suite = unittest.TestSuite()
    suite.addTest(ImmediateSucceedingTestTestCase())
    return suite

def immediateFailingTests():
    suite = unittest.TestSuite()
    suite.addTest(ImmediateFailingTestTestCase())
    return suite

###########################################################################
### Main entry point for this test suite.
###########################################################################

if __name__ == "__main__":
    try:
        unittest.main()
    except SystemExit, e:
        # unittest.main() is very unfortunate here.  It does a
        # sys.exit (which raises SystemExit), instead of letting you
        # clean up after it in the try: block. The SystemExit
        # exception has one attribute, 'code', which is either True if
        # an error occured while running the tests, or False if the
        # tests ran successfully.
        for fw in frameworkList:
            if fw.isRunning: fw.stop()

        if e.code:
            sys.exit(1)
        else:
            if deleteAfterSuccess:
                for fw in frameworkList:
                    fw.clean()

    except KeyboardInterrupt:
        for fw in frameworkList:
            if fw.isRunning: fw.stop()
            
    except Exception, e:
        # Unhandled exception occured.
        log.error("%s" % e)
        sys.exit(1)
        
