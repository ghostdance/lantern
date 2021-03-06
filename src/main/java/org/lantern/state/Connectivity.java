package org.lantern.state;

import java.util.Collection;
import java.util.Collections;

import org.codehaus.jackson.map.annotate.JsonView;
import org.lantern.GoogleTalkState;
import org.lantern.PeerProxyManager;
import org.lantern.event.ConnectedPeersEvent;
import org.lantern.event.Events;
import org.lantern.event.GoogleTalkStateEvent;
import org.lantern.event.SyncEvent;
import org.lantern.state.Model.Persistent;
import org.lantern.state.Model.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

/**
 * Class representing data about Lantern's connectivity.
 */
public class Connectivity {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private GoogleTalkState gtalk = GoogleTalkState.notConnected;
    
    private String ip = "";
    
    private boolean gtalkAuthorized = false;
    
    private boolean internet = false;
    
    private boolean invited = false;

    private PeerProxyManager peerProxyManager;
    
    public Connectivity() {
        Events.register(this);
    }

    @JsonView({Run.class})
    public GoogleTalkState getGTalk() {
        return gtalk;
    }
    
    @JsonView({Run.class})
    public Collection<Peer> getPeers() {
        return Collections.emptyList();
        /*
        if (this.peerProxyManager != null) {
            return this.peerProxyManager.getPeers();
        }
        return Collections.emptyList();
        */
    }
    
    /*
    public Collection<Peer> getPeersCurrent() {
        //return peers(LanternHub.trustedPeerProxyManager());
        return Collections.emptyList();
    }
    
    @JsonView({Run.class, Persistent.class})
    public Collection<Peer> getPeersLifetime() {
        //return peers(LanternHub.trustedPeerProxyManager());
        return Collections.emptyList();
    }
    
    public Collection<Peer> getAnonymousPeers() {
        return Collections.emptyList();
    }
    
    private Collection<Peer> peers(final PeerProxyManager ppm) {
        return ppm.getPeers().values();
    }
    */
    
    @Subscribe
    public void onConnectedPeers(final ConnectedPeersEvent cpe) {
        if (this.peerProxyManager == null) {
            this.peerProxyManager = cpe.getPeerProxyManager();
        }
    }

    @Subscribe
    public void onAuthenticationStateChanged(final GoogleTalkStateEvent ase) {
        this.gtalk = ase.getState();
        Events.asyncEventBus().post(
            new SyncEvent(SyncPath.CONNECTIVITY_GTALK, gtalk));
        //Events.asyncEventBus().post(new SyncEvent(SyncChannel.connectivity));
    }

    @JsonView({Run.class})
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @JsonView({Run.class})
    public String getGtalkOauthUrl() {
        return StaticSettings.getLocalEndpoint()+"/oauth/";
    }

    @JsonView({Run.class})
    public boolean isGtalkAuthorized() {
        return gtalkAuthorized;
    }

    public void setGtalkAuthorized(boolean gtalkAuthorized) {
        this.gtalkAuthorized = gtalkAuthorized;
    }

    @JsonView({Run.class})
    public boolean isInternet() {
        return internet;
    }

    public void setInternet(final boolean internet) {
        this.internet = internet;
    }

    @JsonView({Run.class, Persistent.class})
    public boolean isInvited() {
        return invited;
    }

    public void setInvited(boolean invited) {
        this.invited = invited;
    }
}
