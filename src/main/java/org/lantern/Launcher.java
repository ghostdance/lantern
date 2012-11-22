package org.lantern;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.security.auth.login.CredentialException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggingEvent;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.widgets.Display;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.Timer;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.lantern.exceptional4j.ExceptionalAppender;
import org.lantern.exceptional4j.ExceptionalAppenderCallback;
import org.lantern.privacy.InvalidKeyException;
import org.lantern.privacy.LocalCipherProvider;
import org.lastbamboo.common.offer.answer.IceConfig;
import org.lastbamboo.common.stun.client.StunServerRepository;
import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpFilter;
import org.littleshoot.proxy.HttpRequestFilter;
import org.littleshoot.proxy.HttpResponseFilters;
import org.littleshoot.proxy.PublicIpsOnlyRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ThreadFactoryBuilder;


/**
 * Launches a new Lantern HTTP proxy.
 */
public class Launcher {

    private static Logger LOG;
    private static boolean lanternStarted = false;
    private static LanternHttpProxyServer localProxy;
    private static DefaultHttpProxyServer plainTextAnsererRelayProxy;
    
    
    /**
     * Starts the proxy from the command line.
     * 
     * @param args Any command line arguments.
     */
    public static void main(final String... args) {
        Thread.currentThread().setName("Lantern-Main-Thread");
        //Connection.DEBUG_ENABLED = true;
        configureLogger();
        LOG = LoggerFactory.getLogger(Launcher.class);
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread t, final Throwable e) {
                handleError(e, false);
            }
        });
        
        final Runnable runner = new Runnable() {

            @Override
            public void run() {
                try {
                    launch(args);
                } catch (final Throwable t) {
                    handleError(t, true);
                }
            }
        };
        final Thread main = new Thread(runner, "Lantern-Launch-Thread");
        //main.start();
        runner.run();
        if (SystemUtils.IS_OS_LINUX) {
            synchronized (runner) {
                try {
                    runner.wait();
                } catch (final InterruptedException e) {
                    LOG.info("Interrupted", e);
                }
            }
        }
    }

    // the following are command line options 
    private static final String OPTION_DISABLE_UI = "disable-ui";
    private static final String OPTION_HELP = "help";
    private static final String OPTION_LAUNCHD = "launchd";
    private static final String OPTION_PUBLIC_API = "public-api";
    private static final String OPTION_API_PORT = "api-port";
    private static final String OPTION_SERVER_PORT = "server-port";
    private static final String OPTION_DISABLE_KEYCHAIN = "disable-keychain";
    private static final String OPTION_PASSWORD_FILE = "password-file";
    private static final String OPTION_TRUSTED_PEERS = "disable-trusted-peers";
    private static final String OPTION_ANON_PEERS ="disable-anon-peers";
    private static final String OPTION_LAE = "disable-lae";
    private static final String OPTION_CENTRAL = "disable-central";
    private static final String OPTION_UDP = "disable-udp";
    private static final String OPTION_TCP = "disable-tcp";
    private static final String OPTION_USER = "user";
    private static final String OPTION_PASS = "pass";
    private static final String OPTION_GET = "force-get";
    private static final String OPTION_GIVE = "force-give";
    private static final String OPTION_NO_CACHE = "no-cache";
    private static final String OPTION_VERSION = "version";
    private static final String OPTION_NEW_UI = "new-ui";
    private static final String OPTION_OAUTH2_CLIENT_SECRETS_FILE = "oauth2-client-secrets-file";
    private static final String OPTION_OAUTH2_USER_CREDENTIALS_FILE = "oauth2-user-credentials-file";

    private static void launch(final String... args) {
        LOG.info("Starting Lantern...");
        configureCipherSuites();
        
        // first apply any command line settings
        final Options options = new Options();
        options.addOption(null, OPTION_DISABLE_UI, false,
            "run without a graphical user interface.");
        options.addOption(null, OPTION_API_PORT, true,
            "the port to run the API server on.");
        options.addOption(null, OPTION_SERVER_PORT, true,
            "the port to run the give mode proxy server on.");
        options.addOption(null, OPTION_PUBLIC_API, false,
            "make the API server publicly accessible on non-localhost.");
        options.addOption(null, OPTION_HELP, false,
            "display command line help");
        options.addOption(null, OPTION_LAUNCHD, false,
            "running from launchd - not normally called from command line");
        options.addOption(null, OPTION_DISABLE_KEYCHAIN, false, 
            "disable use of system keychain and ask for local password");
        options.addOption(null, OPTION_PASSWORD_FILE, true, 
            "read local password from the file specified");
        options.addOption(null, OPTION_TRUSTED_PEERS, false,
            "disable use of trusted peer-to-peer connections for proxies.");
        options.addOption(null, OPTION_ANON_PEERS, false,
            "disable use of anonymous peer-to-peer connections for proxies.");
        options.addOption(null, OPTION_LAE, false,
            "disable use of app engine proxies.");
        options.addOption(null, OPTION_CENTRAL, false,
            "disable use of centralized proxies.");
        options.addOption(null, OPTION_UDP, false,
            "disable UDP for peer-to-peer connections.");
        options.addOption(null, OPTION_TCP, false,
            "disable TCP for peer-to-peer connections.");
        options.addOption(null, OPTION_USER, true,
            "Google user name -- WARNING INSECURE - ONLY USE THIS FOR TESTING!");
        options.addOption(null, OPTION_PASS, true,
            "Google password -- WARNING INSECURE - ONLY USE THIS FOR TESTING!");
        options.addOption(null, OPTION_GET, false, "Force running in get mode");
        options.addOption(null, OPTION_GIVE, false, "Force running in give mode");
        options.addOption(null, OPTION_NO_CACHE, false,
            "Don't allow caching of static files in the dashboard");
        options.addOption(null, OPTION_VERSION, false, 
            "Print the Lantern version");
        options.addOption(null, OPTION_NEW_UI, false,
            "Use the new UI under the 'ui' directory");
        options.addOption(null, OPTION_OAUTH2_CLIENT_SECRETS_FILE, true,
            "read Google OAuth2 client secrets from the file specified");
        options.addOption(null, OPTION_OAUTH2_USER_CREDENTIALS_FILE, true,
            "read Google OAuth2 user credentials from the file specified");

        final CommandLineParser parser = new PosixParser();
        final CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
            if (cmd.getArgs().length > 0) {
                throw new UnrecognizedOptionException("Extra arguments were provided");
            }
        }
        catch (final ParseException e) {
            printHelp(options, e.getMessage()+" args: "+Arrays.asList(args));
            return;
        }
        
        if (cmd.hasOption(OPTION_HELP)) {
            printHelp(options, null);
            return;
        } else if (cmd.hasOption(OPTION_VERSION)) {
            printVersion();
            return;
        }
        final Settings set = LanternHub.settings();
        set.setUseTrustedPeers(parseOptionDefaultTrue(cmd, OPTION_TRUSTED_PEERS));
        set.setUseAnonymousPeers(parseOptionDefaultTrue(cmd, OPTION_ANON_PEERS));
        set.setUseLaeProxies(parseOptionDefaultTrue(cmd, OPTION_LAE));
        set.setUseCentralProxies(parseOptionDefaultTrue(cmd, OPTION_CENTRAL));
        
        IceConfig.setTcp(parseOptionDefaultTrue(cmd, OPTION_TCP));
        IceConfig.setUdp(parseOptionDefaultTrue(cmd, OPTION_UDP));
        
        if (cmd.hasOption(OPTION_USER)) {
            set.setCommandLineEmail(cmd.getOptionValue(OPTION_USER));
        }
        if (cmd.hasOption(OPTION_PASS)) {
            set.setCommandLinePassword(cmd.getOptionValue(OPTION_PASS));
        }
        if (cmd.hasOption(OPTION_DISABLE_UI)) {
            LOG.info("Disabling UI");
            set.setUiEnabled(false);
        }
        else {
            set.setUiEnabled(true);
        }
        
        /* option to disable use of keychains in local privacy */
        if (cmd.hasOption(OPTION_DISABLE_KEYCHAIN)) {
            LOG.info("Disabling use of system keychains");
            set.setKeychainEnabled(false);
        }
        else {
            set.setKeychainEnabled(true);
        }
        
        if (cmd.hasOption(OPTION_PASSWORD_FILE)) {
            loadLocalPasswordFile(cmd.getOptionValue(OPTION_PASSWORD_FILE));
        }

        if (cmd.hasOption(OPTION_OAUTH2_CLIENT_SECRETS_FILE)) {
            loadOAuth2ClientSecretsFile(cmd.getOptionValue(OPTION_OAUTH2_CLIENT_SECRETS_FILE));
        }

        if (cmd.hasOption(OPTION_OAUTH2_USER_CREDENTIALS_FILE)) {
            loadOAuth2UserCredentialsFile(cmd.getOptionValue(OPTION_OAUTH2_USER_CREDENTIALS_FILE));
        }

        if (cmd.hasOption(OPTION_PUBLIC_API)) {
            set.setBindToLocalhost(false);
        }
        if (cmd.hasOption(OPTION_API_PORT)) {
            final String apiPortStr =
                cmd.getOptionValue(OPTION_API_PORT);
            LOG.info("Using command-line API port: "+apiPortStr);
            final int apiPort = Integer.parseInt(apiPortStr);
            set.setApiPort(apiPort);
        } else {
            LOG.info("Using random API port...");
            set.setApiPort(LanternUtils.randomPort());
        }
        LOG.info("Running API on port: {}", set.getApiPort());

        if (cmd.hasOption(OPTION_SERVER_PORT)) {
            final String serverPortStr =
                cmd.getOptionValue(OPTION_SERVER_PORT);
            LOG.info("Using command-line proxy port: "+serverPortStr);
            final int serverPort = Integer.parseInt(serverPortStr);
            set.setServerPort(serverPort);
        } else {
            LOG.info("Using random give mode proxy port...");
            set.setServerPort(LanternUtils.randomPort());
        }
        LOG.info("Running give mode proxy on port: {}", set.getServerPort());

        if (cmd.hasOption(OPTION_LAUNCHD)) {
            LOG.info("Running from launchd or launchd set on command line");
            set.setLaunchd(true);
        } else {
            set.setLaunchd(false);
        }
        
        final Display display;
        if (set.isUiEnabled()) {
            // We initialize this super early in case there are any errors 
            // during startup we have to display to the user.
            Display.setAppName("Lantern");
            display = LanternHub.display();
            // Also, We need the system tray to listen for events early on.
            LanternHub.systemTray().createTray();
        }
        else {
            display = null;
        }
        
        // TODO: Just load all the command line options after the settings to
        // avoid stupid state issues.
        loadSettings();
        
        if (cmd.hasOption(OPTION_GIVE)) {
            LanternHub.settings().setGetMode(false);
        } else if (cmd.hasOption(OPTION_GET)) {
            LanternHub.settings().setGetMode(true);
        }
        
        //LanternHub.settings().setCache(!LanternUtils.isDevMode());
        //if (cmd.hasOption(OPTION_NO_CACHE)) {
            LanternHub.settings().setCache(false);
        //}
        if (cmd.hasOption(OPTION_NEW_UI)) {
            LanternHub.settings().setUiDir("ui");
        }
        
        gnomeAutoStart();
        
        // Use our stored STUN servers if available.
        final Collection<String> stunServers = 
            LanternHub.settings().getStunServers();
        if (stunServers != null && !stunServers.isEmpty()) {
            LOG.info("Using stored STUN servers: {}", stunServers);
            StunServerRepository.setStunServers(toSocketAddresses(stunServers));
        }
        if (LanternUtils.hasNetworkConnection()) {
            LOG.info("Got internet...");
            launchWithOrWithoutUi();
        } else {
            // If we're running on startup, it's quite likely we just haven't
            // connected to the internet yet. Let's wait for an internet
            // connection and then start Lantern.
            if (LanternHub.settings().isLaunchd() || !LanternHub.settings().isUiEnabled()) {
                LOG.info("Waiting for internet connection...");
                LanternUtils.waitForInternet();
                launchWithOrWithoutUi();
            }
            // If setup is complete and we're not running on startup, open
            // the dashboard.
            else if (LanternHub.settings().isInitialSetupComplete()) {
                LanternHub.jettyLauncher().openBrowserWhenReady();
                // Wait for an internet connection before starting the XMPP
                // connection.
                LOG.info("Waiting for internet connection...");
                LanternUtils.waitForInternet();
                launchWithOrWithoutUi();
            } else {
                // If we haven't configured Lantern and don't have an internet
                // connection, the problem is that we can't verify the user's
                // user name and password when they try to login, so we just
                // let them know we can't start Lantern until they have a 
                // connection.
                // TODO: i18n
                final String msg = 
                    "We're sorry, but you cannot configure Lantern without " +
                    "an active connection to the internet. Please try again " +
                    "when you have an internet connection.";
                LanternHub.dashboard().showMessage("No Internet", msg);
                System.exit(0);
            }
        }

        
        // This is necessary to keep the tray/menu item up in the case
        // where we're not launching a browser.
        if (display != null) {
            while (!display.isDisposed ()) {
                if (!display.readAndDispatch ()) display.sleep ();
            }
        }
    }
    
    private static void gnomeAutoStart() {
        // Before setup we should just do the default, which is to run on
        // startup. The user can configure this differently at any point 
        // hereafter.
        if (SystemUtils.IS_OS_LINUX && 
            !LanternConstants.GNOME_AUTOSTART.isFile()) {
            final File lanternDesktop;
            final File candidate1 = 
                new File(LanternConstants.GNOME_AUTOSTART.getName());
            final File candidate2 = 
                new File("install/linux", LanternConstants.GNOME_AUTOSTART.getName());
            if (candidate1.isFile()) {
                lanternDesktop = candidate1;
            } else if (candidate2.isFile()){
                lanternDesktop = candidate2;
            } else {
                LOG.error("Could not find lantern.desktop file");
                return;
            }
            try {
                final File parent = LanternConstants.GNOME_AUTOSTART.getParentFile();
                if (!parent.isDirectory()) {
                    if (!parent.mkdirs()) {
                        LOG.error("Could not make dir for gnome autostart: "+parent);
                        return;
                    }
                }
                FileUtils.copyFileToDirectory(lanternDesktop, parent);
                
                LOG.info("Copied {} to {}", lanternDesktop, parent);
            } catch (final IOException e) {
                LOG.error("Could not configure gnome autostart", e);
            }
        }
    }

    private static void configureCipherSuites() {
        Security.addProvider(new BouncyCastleProvider());
        if (!LanternUtils.isUnlimitedKeyStrength()) {
            if (!SystemUtils.IS_OS_WINDOWS_VISTA) {
                LOG.error("No policy files on non-Vista machine!!");
            }
            LOG.info("Reverting to weaker ciphers on Vista");
            IceConfig.setCipherSuites(new String[] {
                //"TLS_DHE_RSA_WITH_AES_128_CBC_SHA"
                "TLS_ECDHE_RSA_WITH_RC4_128_SHA"
            });
        } else {
            // Note the following just sets what cipher suite the server 
            // side selects. DHE is for perfect forward secrecy.
            
            // We include 128 because we never have enough permissions to 
            // copy the unlimited strength policy files on Vista, so we have 
            // to revert back to 128.
            IceConfig.setCipherSuites(new String[] {
                //"TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
                //"TLS_DHE_RSA_WITH_AES_128_CBC_SHA"
                //"TLS_RSA_WITH_RC4_128_SHA"
                "TLS_ECDHE_RSA_WITH_RC4_128_SHA"
            });
        }
    }
    
    
    private static Collection<InetSocketAddress> toSocketAddresses(
        final Collection<String> stunServers) {
        final Collection<InetSocketAddress> isas = 
            new HashSet<InetSocketAddress>();
        for (final String server : stunServers) {
            final String host = StringUtils.substringBefore(server, ":");
            final String port = StringUtils.substringAfter(server, ":");
            isas.add(new InetSocketAddress(host, Integer.parseInt(port)));
        }
        return isas;
    }

    private static boolean parseOptionDefaultTrue(final CommandLine cmd, 
        final String option) {
        if (cmd.hasOption(option)) {
            LOG.info("Found option: "+option);
            return false;
        }
        
        // DEFAULTS TO TRUE!!
        return true;
    }
    
    private static boolean parseOptionDefaultFalse(final CommandLine cmd, 
        final String option) {
        if (cmd.hasOption(option)) {
            LOG.info("Found option: "+option);
            return false;
        }
        
        return false;
    }

    // TODO: We want to make it such that once this method returns the
    // settings are always fully loaded or the entire app has aborted, 
    // including prompting the user for password.
    private static void loadSettings() {
        LanternHub.resetSettings(true);
        if (LanternHub.settings().getSettings().getState() == SettingsState.State.CORRUPTED) {
            try {
                // current behavior is automatic reset of all local data / ciphers
                // immediately.  This behavior could be deferred until later or handled
                // in some other way.
                LOG.warn("Destroying corrupt settings...");
                LanternHub.destructiveFullReset();
            }
            catch (IOException e) {
                LOG.error("Failed to reset corrupt settings: {}", e);
                System.exit(1);
            }
            // still corrupt?
            if (LanternHub.settings().getSettings().getState() == SettingsState.State.CORRUPTED) {
                LOG.error("Failed to reset corrupt settings.");
                System.exit(1);
            }
            else {
                LOG.info("Settings have been reset.");
            }
        }
        
        // if there is no UI and the settings are locked, we need to grab the password on 
        // the command line or else quit.
        if (!LanternHub.settings().isUiEnabled() && 
            LanternHub.settings().getSettings().getState() == SettingsState.State.LOCKED) {
            if (!askToUnlockSettingsCLI()) {
                LOG.error("Unable to unlock settings.");
                System.exit(1);
            }
        }
        
        LOG.info("Settings state is {}", LanternHub.settings().getSettings().getState());
    }
    
    private static boolean askToUnlockSettingsCLI() {
        if (!LanternHub.localCipherProvider().requiresAdditionalUserInput()) {
            LOG.info("Local cipher does not require a password.");
            return true;
        }
        while(true) {
            char [] pw = null; 
            try {
                pw = readSettingsPasswordCLI();
                return unlockSettingsWithPassword(pw);
            }
            catch (final InvalidKeyException e) {
                System.out.println("Password was incorrect, try again."); // XXX i18n
            }
            catch (final GeneralSecurityException e) {
                LOG.error("Error unlocking settings: {}", e);
            }
            catch (final IOException e) {
                LOG.error("Erorr unlocking settings: {}", e);
            }
            finally {
                LanternUtils.zeroFill(pw);
            }
        }
    }
    
    private static char [] readSettingsPasswordCLI() throws IOException {
        if (LanternHub.settings().isLocalPasswordInitialized() == false) {
            while (true) {
                // XXX i18n
                System.out.print("Please enter a password to protect your local data:");
                System.out.flush();
                final char [] pw1 = LanternUtils.readPasswordCLI();
                if (pw1.length == 0) {
                    System.out.println("password cannot be blank, please try again.");
                    System.out.flush();
                    continue;
                }
                System.out.print("Please enter password again:");
                System.out.flush();
                final char [] pw2 = LanternUtils.readPasswordCLI();
                if (Arrays.equals(pw1, pw2)) {
                    // zero out pw2
                    LanternUtils.zeroFill(pw2);
                    return pw1;
                }
                else {
                    LanternUtils.zeroFill(pw1);
                    LanternUtils.zeroFill(pw2);
                    System.out.println("passwords did not match, please try again.");
                    System.out.flush();
                }
            }
        }
        else {
            System.out.print("Please enter your lantern password:");
            System.out.flush();
            return LanternUtils.readPasswordCLI();
        }
    }
    
    
    private static boolean unlockSettingsWithPassword(final char [] password)
        throws GeneralSecurityException, IOException {
        final boolean init = !LanternHub.settings().isLocalPasswordInitialized();
        LanternHub.localCipherProvider().feedUserInput(password, init);
        LanternHub.resetSettings(true);
        final SettingsState.State ss = LanternHub.settings().getSettings().getState();
        if (ss != SettingsState.State.SET) {
            LOG.error("Settings did not unlock, state is {}", ss);
            return false;
        }
        return true;
    }
    
    private static void loadLocalPasswordFile(final String pwFilename) {
        final LocalCipherProvider lcp = LanternHub.localCipherProvider();
        if (!lcp.requiresAdditionalUserInput()) {
            LOG.error("Settings do not require a password to unlock.");
            System.exit(1);
        }

        if (StringUtils.isBlank(pwFilename)) {
            LOG.error("No filename specified to --{}", OPTION_PASSWORD_FILE);
            System.exit(1);
        }
        final File pwFile = new File(pwFilename);
        if (!(pwFile.exists() && pwFile.canRead())) {
            LOG.error("Unable to read password from {}", pwFilename);
            System.exit(1);
        }

        LOG.info("Reading local password from file \"{}\"", pwFilename);
        try {
            final String pw = FileUtils.readLines(pwFile, "US-ASCII").get(0);
            final boolean init = !LanternHub.settings().isLocalPasswordInitialized();
            lcp.feedUserInput(pw.toCharArray(), init);
        }
        catch (final IndexOutOfBoundsException e) {
            LOG.error("Password in file \"{}\" was incorrect", pwFilename);
            System.exit(1);
        }
        catch (final InvalidKeyException e) {
            LOG.error("Password in file \"{}\" was incorrect", pwFilename);
            System.exit(1);
        }
        catch (final GeneralSecurityException e) {
            LOG.error("Failed to initialize using password in file \"{}\": {}", pwFilename, e);
            System.exit(1);
        }
        catch (final IOException e) {
            LOG.error("Failed to initialize using password in file \"{}\": {}", pwFilename, e);
            System.exit(1);
        }
    }

    private static void loadOAuth2ClientSecretsFile(final String filename) {

        if (StringUtils.isBlank(filename)) {
            LOG.error("No filename specified to --{}", OPTION_OAUTH2_CLIENT_SECRETS_FILE);
            System.exit(1);
        }
        final File file = new File(filename);
        if (!(file.exists() && file.canRead())) {
            LOG.error("Unable to read client secrets from {}", filename);
            System.exit(1);
        }
        LOG.info("Reading client secrets from file \"{}\"", filename);
        try {
            final String json = FileUtils.readFileToString(file, "US-ASCII");
            JSONObject obj = (JSONObject)JSONValue.parse(json);
            JSONObject ins = (JSONObject)obj.get("installed");
            final String clientID = (String)ins.get("client_id");
            final String clientSecret = (String)ins.get("client_secret");
            if (clientID == null || clientSecret == null) {
                LOG.error("Failed to parse client secrets file \"{}\"", filename);
                System.exit(1);
            } else {
                LanternHub.settings().setClientID(clientID);
                LanternHub.settings().setClientSecret(clientSecret);
            }
        } catch (final IOException e) {
            LOG.error("Failed to read file \"{}\"", filename);
            System.exit(1);
        }
    }

    private static void loadOAuth2UserCredentialsFile(final String filename) {
        if (StringUtils.isBlank(filename)) {
            LOG.error("No filename specified to --{}", OPTION_OAUTH2_USER_CREDENTIALS_FILE);
            System.exit(1);
        }
        final File file = new File(filename);
        if (!(file.exists() && file.canRead())) {
            LOG.error("Unable to read user credentials from {}", filename);
            System.exit(1);
        }
        LOG.info("Reading user credentials from file \"{}\"", filename);
        try {
            final String json = FileUtils.readFileToString(file, "US-ASCII");
            JSONObject obj = (JSONObject)JSONValue.parse(json);
            final String username = (String)obj.get("username");
            final String accessToken = (String)obj.get("access_token");
            final String refreshToken = (String)obj.get("refresh_token");
            // Access token is not strictly necessary, so we allow it to be
            // null.
            if (username == null
                || refreshToken == null) {
                LOG.error("Failed to parse user credentials file \"{}\"", filename);
                System.exit(1);
            } else {
                LanternHub.settings().setCommandLineEmail(username);
                LanternHub.settings().setAccessToken(accessToken);
                LanternHub.settings().setRefreshToken(refreshToken);
                LanternHub.settings().setUseGoogleOAuth2(true);
            }
        } catch (final IOException e) {
            LOG.error("Failed to read file \"{}\"", filename);
            System.exit(1);
        }
    }

    private static void launchWithOrWithoutUi() {
        if (!LanternHub.settings().isUiEnabled()) {
            // We only run headless on Linux for now.
            LOG.info("Running Lantern with no display...");
            launchLantern();
            LanternHub.jettyLauncher();
            return;
        }

        LOG.debug("Is launchd: {}", LanternHub.settings().isLaunchd());
        launchLantern();
        if (!LanternHub.settings().isLaunchd() || 
            !LanternHub.settings().isInitialSetupComplete()) {
            LanternHub.jettyLauncher().openBrowserWhenReady();
        }
    }

    public static void launchLantern() {
        LOG.debug("Launching Lantern...");
        ThreadRenamingRunnable.setThreadNameDeterminer(
            ThreadNameDeterminer.CURRENT);
        
        final HttpRequestFilter publicOnlyRequestFilter = 
            new PublicIpsOnlyRequestFilter();
        
        final Timer timer = new HashedWheelTimer();
        
        final ServerSocketChannelFactory serverChannelFactory = 
            new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder().setNameFormat(
                        "Lantern-Netty-Server-Boss-Thread-%d").setDaemon(true).build()),
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder().setNameFormat(
                        "Lantern-Netty-Server-Worker-Thread-%d").setDaemon(true).build()));
        final ClientSocketChannelFactory clientChannelFactory = 
            new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder().setNameFormat(
                        "Lantern-Netty-Client-Boss-Thread-%d").setDaemon(true).build()),
                Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder().setNameFormat(
                        "Lantern-Netty-Client-Worker-Thread-%d").setDaemon(true).build()));
        
        final ChannelGroup channelGroup = 
            new DefaultChannelGroup("Local-HTTP-Proxy-Server");
        
        LanternHub.setNettyTimer(timer);
        LanternHub.setServerChannelFactory(serverChannelFactory);
        LanternHub.setClientChannelFactory(clientChannelFactory);
        LanternHub.setChannelGroup(channelGroup);
        
        
        // Note that just passing in the keystore manager triggers this to 
        // become an SSL proxy server.
        final int staticRandomPort = LanternHub.settings().getServerPort();
        final StatsTrackingDefaultHttpProxyServer sslProxy =
            new StatsTrackingDefaultHttpProxyServer(staticRandomPort,
            new HttpResponseFilters() {
                @Override
                public HttpFilter getFilter(String arg0) {
                    return null;
                }
            }, null, publicOnlyRequestFilter, clientChannelFactory, timer, 
            serverChannelFactory);
        LOG.debug("SSL port is {}", staticRandomPort);
        //final org.littleshoot.proxy.HttpProxyServer sslProxy = 
        //    new DefaultHttpProxyServer(LanternHub.randomSslPort());
        sslProxy.start(false, false);
         
        // The reason this exists is complicated. It's for the case when the
        // offerer gets an incoming connection from the answerer, and then
        // only on the answerer side. The answerer "client" socket relays
        // its data to the local proxy.
        // See http://cdn.getlantern.org/IMAG0210.jpg
        Launcher.plainTextAnsererRelayProxy = 
            new DefaultHttpProxyServer(
                LanternUtils.PLAINTEXT_LOCALHOST_PROXY_PORT,
                publicOnlyRequestFilter, clientChannelFactory, timer, 
                serverChannelFactory);
        plainTextAnsererRelayProxy.start(true, false);
        
        LOG.info("About to start Lantern server on port: "+
            LanternConstants.LANTERN_LOCALHOST_HTTP_PORT);


        /*
        // Delegate all calls to the current hub cookie tracker.
        final CookieTracker hubTracker = new CookieTracker() {

            @Override
            public void setCookies(Collection<Cookie> cookies, HttpRequest context) {
                LanternHub.cookieTracker().setCookies(cookies, context);
            }

            @Override
            public boolean wouldSendCookie(final Cookie cookie, final URI toRequestUri) {
                return LanternHub.cookieTracker().wouldSendCookie(cookie, toRequestUri);
            }

            @Override
            public boolean wouldSendCookie(final Cookie cookie, final URI toRequestUri, final boolean requireValueMatch) {
                return LanternHub.cookieTracker().wouldSendCookie(cookie, toRequestUri, requireValueMatch);
            }

            @Override
            public CookieFilter asOutboundCookieFilter(final HttpRequest request, final boolean requireValueMatch) throws URISyntaxException {
                return LanternHub.cookieTracker().asOutboundCookieFilter(request, requireValueMatch);
            }
        };

        final SetCookieObserver cookieObserver = new WhitelistSetCookieObserver(hubTracker);
        final CookieFilter.Factory cookieFilterFactory = new DefaultCookieFilterFactory(hubTracker);
        */
        Launcher.localProxy = 
            new LanternHttpProxyServer(
                LanternConstants.LANTERN_LOCALHOST_HTTP_PORT, 
                //null, sslRandomPort,
                null, null, serverChannelFactory, 
                clientChannelFactory, timer, channelGroup);
        localProxy.start();
        
        new AutoConnector(); 

        try {
            LanternHub.configurator().copyFireFoxExtension();
        } catch (final IOException e) {
            LOG.error("Could not copy extension", e);
        }
        
        lanternStarted = true;
    }

    /**
     * The autoconnector tries to auto-connect the first time that it observes 
     * that the settings have reached the SET state.
     */
    private static class AutoConnector {
        
        private boolean done = false;
        
        public AutoConnector() {
            checkAutoConnect();
            if (!done) {
                LanternHub.register(this);
            }
        }
        
        @Subscribe
        public void onStateChange(final SettingsStateEvent sse) {
            checkAutoConnect();
        }
        
        private void checkAutoConnect() {
            if (done) {
                return;
            }
            if (LanternHub.settings().getSettings().getState() != SettingsState.State.SET) {
                LOG.info("not testing auto-connect, settings are not ready.");
                return;
            }
            
            // only test once.
            done = true;
            
            LOG.info("Settings loaded, testing auto-connect behavior");
            // This won't connect in the case where the user hasn't entered 
            // their user name and password and the user is running with a UI.
            // Otherwise, it will connect.
            if (LanternHub.settings().isConnectOnLaunch() &&
                (LanternUtils.isConfigured() || !LanternHub.settings().isUiEnabled())) {
                final Runnable runner = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            LanternHub.xmppHandler().connect();
                        } catch (final IOException e) {
                            LOG.info("Could not login", e);
                        } catch (final CredentialException e) {
                            LOG.info("Bad credentials");
                        } catch (final NotInClosedBetaException e) {
                            LOG.warn("Not in closed beta!!");
                        }
                    }
                };
                final Thread t = 
                    new Thread(runner, "Auto-Connect-From-Settings-Ready");
                t.setDaemon(true);
                t.start();
            } else {
                LOG.info("Not auto-logging in with settings:\n{}",
                    LanternHub.settings());
            }
        }
    }
    
    private static void printHelp(Options options, String errorMessage) {
        if (errorMessage != null) {
            LOG.error(errorMessage);
            System.err.println(errorMessage);
        }
    
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("lantern", options);
    }
    
    private static void printVersion() {
        System.out.println("Lantern version "+LanternConstants.VERSION);
    }
    
    private static void configureLogger() {
        final String propsPath = "src/main/resources/log4j.properties";
        final File props = new File(propsPath);
        if (props.isFile()) {
            System.out.println("Running from main line");
            PropertyConfigurator.configure(propsPath);
        } else {
            System.out.println("Not on main line...");
            configureProductionLogger();
        }
    }
    
    private static void configureProductionLogger() {
        final File logDir = LanternConstants.LOG_DIR;
        final File logFile = new File(logDir, "java.log");
        final Properties props = new Properties();
        try {
            final String logPath = logFile.getCanonicalPath();
            props.put("log4j.appender.RollingTextFile.File", logPath);
            props.put("log4j.rootLogger", "warn, RollingTextFile");
            props.put("log4j.appender.RollingTextFile",
                    "org.apache.log4j.RollingFileAppender");
            props.put("log4j.appender.RollingTextFile.MaxFileSize", "1MB");
            props.put("log4j.appender.RollingTextFile.MaxBackupIndex", "1");
            props.put("log4j.appender.RollingTextFile.layout",
                    "org.apache.log4j.PatternLayout");
            props.put(
                    "log4j.appender.RollingTextFile.layout.ConversionPattern",
                    "%-6r %d{ISO8601} %-5p [%t] %c{2}.%M (%F:%L) - %m%n");

            // This throws and swallows a FileNotFoundException, but it
            // doesn't matter. Just weird.
            PropertyConfigurator.configure(props);
            System.out.println("Set logger file to: " + logPath);
            final ExceptionalAppenderCallback callback = 
                new ExceptionalAppenderCallback() {

                    @Override
                    public boolean addData(final JSONObject json, 
                        final LoggingEvent le) {
                        if (!LanternHub.settings().isAnalytics()) {
                            // Don't report anything if the user doesn't have
                            // it turned on.
                            return false;
                        }
                        json.put("version", LanternConstants.VERSION);
                        return true;
                    }
            };
            final Appender bugAppender = new ExceptionalAppender(
               LanternConstants.GET_EXCEPTIONAL_API_KEY, callback);
            BasicConfigurator.configure(bugAppender);
        } catch (final IOException e) {
            System.out.println("Exception setting log4j props with file: "
                    + logFile);
            e.printStackTrace();
        }
    }
    
    private static void handleError(final Throwable t, final boolean exit) {
        LOG.error("Uncaught exception: "+t.getMessage(), t);
        if (t instanceof SWTError || t.getMessage().contains("SWTError")) {
            System.out.println(
                "To run without a UI, run lantern with the --" + 
                OPTION_DISABLE_UI +
                " command line argument");
        } 
        else if (!lanternStarted && LanternHub.settings().isUiEnabled()) {
            LOG.info("Showing error to user...");
            LanternHub.dashboard().showMessage("Startup Error",
               "We're sorry, but there was an error starting Lantern " +
               "described as '"+t.getMessage()+"'.");
        }
        if (exit) {
            LOG.info("Exiting Lantern");
            System.exit(1);
        }
    }

    public static void stop() {
        LanternHub.jettyLauncher().stop();
        LanternHub.xmppHandler().stop();
        if (Launcher.plainTextAnsererRelayProxy != null) {
            Launcher.plainTextAnsererRelayProxy.stop();
        }
        if (Launcher.localProxy != null) {
            Launcher.localProxy.stop();
        }
    }
}
