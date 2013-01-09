package org.lantern;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.lastbamboo.common.portmapping.PortMapListener;
import org.lastbamboo.common.portmapping.PortMappingProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NatPmpTest {
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Test 
    public void testNatPmp() throws Exception {
        final NatPmpImpl pmp =
            new NatPmpImpl(TestUtils.getStatsTracker());
        final AtomicInteger ai = new AtomicInteger(-1);
        final AtomicBoolean error = new AtomicBoolean();
        final PortMapListener portMapListener = new PortMapListener() {
            
            @Override
            public void onPortMapError() {
                error.set(true);
                synchronized(ai) {
                    ai.notifyAll();
                }
            }
            
            @Override
            public void onPortMap(final int port) {
                ai.set(port);
                synchronized(ai) {
                    ai.notifyAll();
                }
            }
        };
        final int index = 
            pmp.addNatPmpMapping(PortMappingProtocol.TCP, 5678, 1341, 
                portMapListener);
        assertTrue(index != -1);
        synchronized(ai) {
            if (ai.get() == -1) {
                ai.wait(10000);
            }
        }

        if (!error.get()) {
            final int mapped = ai.get();
            assertTrue("Expected a mapped port", mapped > 1024);
        } else {
            log.debug("Network does not support NAT-PMP so we're not testing it.");
        }
    }
}
