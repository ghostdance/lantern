package org.lantern;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.lantern.state.StaticSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.ShlObj;

public class ChromeRunner {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Point location;
    
    private volatile Process process;
    private final int screenWidth;
    private final int screenHeight;
    
    public ChromeRunner(final int screenWidth, final int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.location = 
            LanternUtils.getScreenCenter(screenWidth, screenHeight);
    }


    private String determineExecutable() throws IOException {
        final String path = determineExecutablePath();
        final File file = new File(path);
        if (!file.isFile()) {
            throw new IOException("Could not find chrome at:" + path);
        } else if (!file.canExecute()) {
            throw new IOException("Chrome not executable at:" + path);
        }
        return path;
    }

    private String determineExecutablePath() {
        if (SystemUtils.IS_OS_MAC_OSX) {
            return "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        } else if (SystemUtils.IS_OS_LINUX) {
            final String path1 = "/usr/bin/google-chrome";
            final File opt1 = new File(path1);
            if (opt1.isFile() && opt1.canExecute()) return path1;
            return "/usr/bin/chromium-browser";
        } else if (SystemUtils.IS_OS_WINDOWS) {
            return findWindowsExe();
        }
        /*
         * Should be something like:
         * 
         * Windows XP    %HOMEPATH%\Local Settings\Application Data\Google\Chrome\Application\chrome.exe
         * Windows Vista    C:\Users\%USERNAME%\AppData\Local\Google\Chrome\Application\chrome.exe
         */
        throw new UnsupportedOperationException("This is an experimental feature!");
    }
    
    private String findWindowsExe() {//final String... opts) {
        final Map<String, Integer> opts = new HashMap<String, Integer>();
        opts.put("APPDATA", ShlObj.CSIDL_APPDATA);
        opts.put("LOCALAPPDATA", ShlObj.CSIDL_LOCAL_APPDATA);
        opts.put("PROGRAMFILES", ShlObj.CSIDL_PROGRAM_FILES);
        opts.put("ProgramW6432", ShlObj.CSIDL_PROGRAM_FILESX86);
        final String chromePath = "/Google/Chrome/Application/chrome.exe";
        final Collection<String> paths = new HashSet<String>();
        for (final Entry<String, Integer> entry : opts.entrySet()) {
            final String base;
            final String envBase = System.getenv(entry.getKey());
            if (StringUtils.isBlank(envBase)) {
                base = Shell32Util.getFolderPath(entry.getValue().intValue());
            } else {
                base = envBase;
            }
            if (StringUtils.isBlank(base)) {
                log.error("Could not resolve env variable: {}", base);
                continue;
            }
            final String path = base + chromePath;
            paths.add(path);
            final File candidate = new File(path);
            if (candidate.isFile() && candidate.canExecute()) {
                log.debug("Running with path: {}", path);
                return path;
            }
        }
        throw new UnsupportedOperationException(
            "Could not find Chrome on Windows!! Searched paths:\n"+paths);
    }

    public void open() throws IOException {
        open(StaticSettings.getApiPort());
    }
    
    public void open(final int port) throws IOException {

        if (this.process != null) {
            try {
                final int exitValue = this.process.exitValue();
                log.info("Got exit value from former process: ", exitValue);
            } catch (final IllegalThreadStateException e) {
                // This indicates the existing process is still running.
                log.info("Ignoring open call since process is still running");
                return;
            }
        }
        final String endpoint = StaticSettings.getLocalEndpoint(port);
        log.debug("Opening browser to: {}", endpoint);
        final List<String> commands = new ArrayList<String>();
        final String executable = determineExecutable();
        commands.add(executable);
        commands.add("--user-data-dir="+LanternConstants.CONFIG_DIR.getAbsolutePath());
        commands.add("--window-size="+screenWidth+","+screenHeight);
        commands.add("--window-position="+location.x+","+location.y);
        commands.add("--app="+endpoint);

        final ProcessBuilder processBuilder = new ProcessBuilder(commands);
        
        // Note we don't call waitFor on the process to avoid blocking the
        // calling thread and because we don't care too much about the return
        // value.
        this.process = processBuilder.start();
        
        new Analyzer(process.getInputStream());
        new Analyzer(process.getErrorStream());
    }
    
    public void close() {
        log.info("Closing Chrome browser...process is: {}", this.process);
        if (this.process != null) {
            log.info("Really closing Chrome browser...");
            this.process.destroy();
            this.process = null;
        }
    }

    private class Analyzer implements Runnable {

        final InputStream is;

        public Analyzer(final InputStream is) {
            this.is = is;
            final Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void run() {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(this.is, 
                    LanternConstants.UTF8));

                String line = "";
                while((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (final IOException e) {
                log.info("Exception reading external process", e);
            } finally {
                IOUtils.closeQuietly(br);
            }
            
        }
    }
}
