/*
 * SoapUI, Copyright (C) 2004-2018 SmartBear Software
 *
 * Licensed under the EUPL, Version 1.1 or - as soon as they will be approved by the European Commission - subsequent 
 * versions of the EUPL (the "Licence"); 
 * You may not use this work except in compliance with the Licence. 
 * You may obtain a copy of the Licence at: 
 * 
 * http://ec.europa.eu/idabc/eupl 
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is 
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either 
 * express or implied. See the Licence for the specific language governing permissions and limitations 
 * under the Licence. 
 */

package com.eviware.soapui.Util;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class SoapUITools {
    private final static Logger log = LoggerFactory.getLogger(SoapUITools.class);

    private static final boolean IS_WINDOWS = System.getProperty("os.name").contains("Windows");
    private static final boolean IS_MAC = System.getProperty("os.name").contains("Mac");
    private static final boolean IS_X86_PLATFORM = System.getProperty("sun.arch.data.model").equals("32");
    private static final boolean IS_X64_PLATFORM = System.getProperty("sun.arch.data.model").equals("64");
    private static final String OS_VERSION = System.getProperty("os.version");

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean isMac() {
        return IS_MAC;
    }

    public static boolean isLinux() {
        return !IS_WINDOWS && !IS_MAC;
    }

    public static boolean isX86Platform() {
        return IS_X86_PLATFORM;
    }

    public static boolean isX64Platform() {
        return IS_X64_PLATFORM;
    }

    public static String getOSVersion() {
        return OS_VERSION;
    }

    public static File createTemporaryDirectory() throws IOException {
        String libDirectoryName = UUID.randomUUID().toString();
        final File libDirectory = new File(System.getProperty("java.io.tmpdir"), libDirectoryName);
        if (!libDirectory.mkdir()) {
            throw new IOException("Could not create directory for unpacked JAR libraries at " + libDirectory);
        }
        deleteDirectoryOnExit(libDirectory);
        return libDirectory;
    }

    public static void deleteDirectoryOnExit(final File directory) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileUtils.deleteDirectory(directory);
                } catch (IOException e) {
                    log.warn("Could not delete temporary directory " + directory);
                }
            }
        }));
    }

    public static Path soapuiHomeDir() {
        String homePath = System.getProperty("soapui.home");
        if (homePath == null) {
            File homeFile = new File(".");
            log.warn("System property 'soapui.home' is not set! Using this directory instead: {}", homeFile);
            return homeFile.toPath();
        }
        return ensureExists(Paths.get(homePath));
    }

    public static Path ensureExists(Path path) {
        File file = path.toFile();
        boolean ok = true;
        if (!file.exists()) {
            ok = file.mkdirs();
        }
        if (!ok) {
            throw new RuntimeException("Cannot create local storage at: " + file);
        }
        return path;
    }

    public static String absolutePath(Path path) {
        return path.toFile().getAbsolutePath();
    }
}
