package org.lantern;

import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.littleshoot.proxy.HttpFilter;
import org.littleshoot.proxy.HttpRequestFilter;
import org.littleshoot.proxy.HttpResponseFilters;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PlainTestRelayHttpProxyServer extends StatsTrackingDefaultHttpProxyServer {

    @Inject
    public PlainTestRelayHttpProxyServer(final HttpRequestFilter requestFilter,
        final ClientSocketChannelFactory clientChannelFactory, 
        final Timer timer,
        final ServerSocketChannelFactory serverChannelFactory, 
        final LanternKeyStoreManager ksm,
        final Stats stats) {
        super(LanternUtils.PLAINTEXT_LOCALHOST_PROXY_PORT,
            new HttpResponseFilters() {
                @Override
                public HttpFilter getFilter(String arg0) {
                    return null;
                }
            }, null, requestFilter, clientChannelFactory, timer, 
            serverChannelFactory, ksm, stats);
    }
}
