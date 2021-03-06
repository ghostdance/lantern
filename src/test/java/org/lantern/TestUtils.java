package org.lantern;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.security.auth.login.CredentialException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.XMPPConnection;
import org.lantern.http.JettyLauncher;
import org.lantern.http.OauthUtils;
import org.lantern.privacy.EncryptedFileService;
import org.lantern.privacy.LocalCipherProvider;
import org.lantern.state.Model;
import org.lantern.state.ModelIo;
import org.lantern.state.ModelService;
import org.lantern.state.ModelUtils;
import org.lantern.state.Settings;
import org.littleshoot.commom.xmpp.GoogleOAuth2Credentials;
import org.littleshoot.commom.xmpp.XmppUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class TestUtils {

    private static final File privatePropsFile = 
        LanternConstants.TEST_PROPS;
    
    private static final Properties privateProps = new Properties();

    private static DefaultXmppHandler xmppHandler;

    private static LanternSocketsUtil socketsUtil;

    private static LanternKeyStoreManager ksm;

    private static LanternXmppUtil lanternXmppUtil;

    private static Model model;
    
    private static LocalCipherProvider localCipherProvider;
    private static EncryptedFileService encryptedFileService;

    private static JettyLauncher jettyLauncher;
    
    private static MessageService messageService;

    private static Stats statsTracker;
    
    private static Roster roster;

    private static ModelService modelService;
    
    private static AnonymousPeerProxyManager anon;
    
    private static Proxifier proxifier;
    
    private static ModelIo modelIo;
    
    private static ModelUtils modelUtils;

    private static boolean loaded;

    private static DefaultProxyTracker proxyTracker;

    static {
        InputStream is = null;
        try {
            is = new FileInputStream(privatePropsFile);
            privateProps.load(is);
        } catch (final IOException e) {
            System.err.println("NO PRIVATE PROPS FILE AT "+
                privatePropsFile.getAbsolutePath());
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(is);
        }
        
        if (StringUtils.isBlank(getRefreshToken()) ||
            StringUtils.isBlank(getAccessToken())) {
            throw new Error("Tokens not in "+privatePropsFile);
        }
        //load();
    }
    
    private static void load() {
        loaded = true;
        final Injector injector = Guice.createInjector(new LanternModule());
        
        xmppHandler = injector.getInstance(DefaultXmppHandler.class);
        socketsUtil = injector.getInstance(LanternSocketsUtil.class);
        ksm = injector.getInstance(LanternKeyStoreManager.class);
        lanternXmppUtil = injector.getInstance(LanternXmppUtil.class);
        localCipherProvider = injector.getInstance(LocalCipherProvider.class);
        encryptedFileService = injector.getInstance(EncryptedFileService.class);
        model = injector.getInstance(Model.class);
        jettyLauncher = injector.getInstance(JettyLauncher.class);
        messageService = injector.getInstance(MessageService.class);
        statsTracker = injector.getInstance(Stats.class);
        roster = injector.getInstance(Roster.class);
        modelService = injector.getInstance(ModelService.class);
        anon = injector.getInstance(AnonymousPeerProxyManager.class);
        proxifier = injector.getInstance(Proxifier.class);
        modelUtils = injector.getInstance(ModelUtils.class);
        modelIo = injector.getInstance(ModelIo.class);
        proxyTracker = injector.getInstance(DefaultProxyTracker.class);
        
        final Settings set = model.getSettings();
        set.setAccessToken(getAccessToken());
        set.setRefreshToken(getRefreshToken());
        set.setUseGoogleOAuth2(true);
        xmppHandler.start();
    }
    
    public static XMPPConnection xmppConnection() throws CredentialException, 
        IOException {
        final GoogleOAuth2Credentials creds = TestUtils.getGoogleOauthCreds();
        final int attempts = 2;
        
        final XMPPConnection conn = 
            XmppUtils.persistentXmppConnection(creds, attempts, 
                "talk.google.com", 5222, "gmail.com", null);
        return conn;
    }
    
    public static GoogleOAuth2Credentials getGoogleOauthCreds() {
        final Details secrets;
        try {
            secrets = OauthUtils.loadClientSecrets().getInstalled();
        } catch (final IOException e) {
            throw new Error("Could not load client secrets?", e);
        }
        final String clientId = secrets.getClientId();
        final String clientSecret = secrets.getClientSecret();
        
        return new GoogleOAuth2Credentials("anon@getlantern.org",
            clientId, clientSecret, 
            getAccessToken(), getRefreshToken(), 
            "gmail.");
    }

    public static String getRefreshToken() {
        return privateProps.getProperty("refresh_token");
    }

    public static String getAccessToken() {
        return privateProps.getProperty("access_token");
    }
    
    public static String getUserName() {
        return privateProps.getProperty("username");
    }

    public static JettyLauncher getJettyLauncher() {
        if (!loaded) load();
        return jettyLauncher;
    }
    
    public static DefaultXmppHandler getXmppHandler() {
        if (!loaded) load();
        return xmppHandler;
    }

    public static LanternSocketsUtil getSocketsUtil() {
        if (!loaded) load();
        return socketsUtil;
    }

    public static LanternKeyStoreManager getKsm() {
        if (!loaded) load();
        return ksm;
    }

    public static LanternXmppUtil getLanternXmppUtil() {
        if (!loaded) load();
        return lanternXmppUtil;
    }

    public static Model getModel() {
        if (!loaded) load();
        return model;
    }
    
    public static LocalCipherProvider getLocalCipherProvider() {
        if (!loaded) load();
        return localCipherProvider;
    }

    public static EncryptedFileService getEncryptedFileService() {
        if (!loaded) load();
        return encryptedFileService;
    }

    public static MessageService getMessageService() {
        if (!loaded) load();
        return messageService;
    }

    public static Stats getStatsTracker() {
        if (!loaded) load();
        return statsTracker;
    }
    
    public static Roster getRoster() {
        if (!loaded) load();
        return roster;
    }

    public static ModelService getModelService() {
        if (!loaded) load();
        return modelService;
    }

    public static AnonymousPeerProxyManager getAnon() {
        if (!loaded) load();
        return anon;
    }
    
    public static Proxifier getProxifier() {
        if (!loaded) load();
        return proxifier;
    }

    public static ModelIo getModelIo() {
        if (!loaded) load();
        return modelIo;
    }

    public static ModelUtils getModelUtils() {
        if (!loaded) load();
        return modelUtils;
    }

    public static ProxyTracker getProxyTracker() {
        if (!loaded) load();
        return proxyTracker;
    }
}
