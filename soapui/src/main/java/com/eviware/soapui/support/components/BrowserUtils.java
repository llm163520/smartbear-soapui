package com.eviware.soapui.support.components;

import com.eviware.soapui.Util.SoapUITools;

public class BrowserUtils {
    public static boolean canUseJxBrowser() {
        if (SoapUITools.isLinux() && SoapUITools.isX86Platform()) {
            return false;
        }

        if (SoapUITools.isLinux() && SoapUITools.getOSVersion().contains("18.04")) {
            return false;
        }

        return true;
    }
}
