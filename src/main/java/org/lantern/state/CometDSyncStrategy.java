package org.lantern.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.bayeux.server.ServerSession;
import org.lantern.LanternUtils;
import org.lantern.state.Model.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;

/**
 * Strategy for syncing/pushing with the browser using cometd.
 */
@Singleton
public class CometDSyncStrategy implements SyncStrategy {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final Map<String, Long> lastUpdateTimes = 
        new ConcurrentHashMap<String, Long>();
    
    private final Executor exec = Executors.newSingleThreadExecutor(new ThreadFactory() {
        
        @Override
        public Thread newThread(final Runnable r) {
            final Thread t = new Thread(r, "Sync-Exec-Thread");
            t.setDaemon(true);
            return t;
        }
    });
    
    @Override
    public void sync(final boolean force,
        final ServerSession session, final String path, final Object value) {
        log.info("SYNCING");
        if (session == null) {
            log.info("No session...not syncing");
            return;
        }
        final long elapsed = elapsedForPath(path);

        if (!force && elapsed < 100) {
            log.info("Not pushing more than 10 times a second for path '"+path+
                "'. {} ms elapsed", elapsed);
            return;
        }

        // We send all updates over the same channel.
        final ClientSessionChannel ch = 
            session.getLocalSession().getChannel("/sync");

        lastUpdateTimes.put(path, System.currentTimeMillis());

        final SyncData data = new SyncData(path, value);
        final String json = LanternUtils.jsonify(data, Run.class);
        
        if (!path.equals(SyncPath.ROSTER.getPath())) {
            log.debug("Sending state to frontend:\n{}", json);
            log.debug("Synced object: {}", value);
        } else {
            log.debug("SYNCING ROSTER -- NOT LOGGING FULL");
            log.debug("Sending state to frontend:\n{}", json);
        }
        this.exec.execute(new Runnable() {
            @Override
            public void run() {
                ch.publish(data);
                log.debug("Sync performed");
            }
        });
    }

    private long elapsedForPath(final String path) {
        synchronized(lastUpdateTimes) {
            if (lastUpdateTimes.containsKey(path)) {
                final long lastUpdateTime = lastUpdateTimes.get(path);
                final long elapsed = System.currentTimeMillis() - lastUpdateTime;
                return elapsed;
            } else {
                return Long.MAX_VALUE;
            }
        }
    }

    /**
     * Helper class that formats data according to:
     * 
     * https://github.com/getlantern/lantern-ui/blob/master/SPECS.md
     */
    public static class SyncData {

        private final String path;
        private final Object value; 
        
        public SyncData(final SyncPath channel, final Object val) {
            this(channel.getPath(), val);
        }
        
        public SyncData(final String path, final Object val) {
            this.path = path;
            this.value = val;
        }

        public String getPath() {
            return path;
        }

        public Object getValue() {
            return value;
        }
    }
}
