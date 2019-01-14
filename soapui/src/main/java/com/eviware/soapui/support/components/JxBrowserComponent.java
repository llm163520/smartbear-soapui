package com.eviware.soapui.support.components;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.rest.actions.oauth.BrowserListener;
import com.eviware.soapui.support.MessageSupport;
import com.eviware.soapui.support.Tools;
import com.google.common.io.Files;
import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.BrowserContext;
import com.teamdev.jxbrowser.chromium.BrowserContextParams;
import com.teamdev.jxbrowser.chromium.BrowserException;
import com.teamdev.jxbrowser.chromium.CertificateErrorParams;
import com.teamdev.jxbrowser.chromium.ContextMenuHandler;
import com.teamdev.jxbrowser.chromium.ContextMenuParams;
import com.teamdev.jxbrowser.chromium.EditorCommand;
import com.teamdev.jxbrowser.chromium.JSContext;
import com.teamdev.jxbrowser.chromium.JSValue;
import com.teamdev.jxbrowser.chromium.LoadHandler;
import com.teamdev.jxbrowser.chromium.LoadParams;
import com.teamdev.jxbrowser.chromium.PopupContainer;
import com.teamdev.jxbrowser.chromium.PopupHandler;
import com.teamdev.jxbrowser.chromium.PopupParams;
import com.teamdev.jxbrowser.chromium.ProtocolService;
import com.teamdev.jxbrowser.chromium.StorageType;
import com.teamdev.jxbrowser.chromium.URLResponse;
import com.teamdev.jxbrowser.chromium.events.ConsoleEvent;
import com.teamdev.jxbrowser.chromium.events.FailLoadingEvent;
import com.teamdev.jxbrowser.chromium.events.FinishLoadingEvent;
import com.teamdev.jxbrowser.chromium.events.LoadAdapter;
import com.teamdev.jxbrowser.chromium.events.ScriptContextAdapter;
import com.teamdev.jxbrowser.chromium.events.ScriptContextEvent;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.teamdev.jxbrowser.chromium.BrowserType.LIGHTWEIGHT;

public class JxBrowserComponent implements WebViewBasedBrowserComponent {
    public static final String DEFAULT_ERROR_PAGE = "<html><body><h1>The page could not be loaded</h1></body></html>";
    private static final MessageSupport messages = MessageSupport.getMessages(JxBrowserComponent.class);
    private static final Logger log = LoggerFactory.getLogger(JxBrowserComponent.class);
    private static BrowserContext customBrowserContext;
    private final boolean withContextMenu;
    private Browser browser;
    private JPanel panel = new JPanel(new BorderLayout());
    private BrowserView browserView;
    private java.util.List<BrowserListener> listeners = new ArrayList<>();
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private PopupStrategy popupStrategy;
    private JxBrowserToolbar toolbar;
    private String lastLocation;
    private String fallBackPage;
    private boolean showingErrorPage;
    private List<ConsoleEvent> jsErrors = Collections.synchronizedList(new ArrayList<>());

    JxBrowserComponent(boolean addNavigationBar, PopupStrategy popupStrategy) {
        this(addNavigationBar, false, popupStrategy);
    }

    JxBrowserComponent(boolean addNavigationBar, boolean withContextMenu, PopupStrategy popupStrategy) {
        this.popupStrategy = popupStrategy;
        this.withContextMenu = withContextMenu;
        initializeBrowser(addNavigationBar);
    }

    private static synchronized BrowserContext getBrowserContext() {
        if (customBrowserContext == null) {
            File tempDir = Files.createTempDir();
            String browserDataDirectory = tempDir.getAbsolutePath();
            BrowserContextParams params = new BrowserContextParams(browserDataDirectory);
            params.setStorageType(StorageType.MEMORY);
            Tools.deleteDirectoryOnExit(tempDir);
            customBrowserContext = new BrowserContext(params);
        }
        return customBrowserContext;
    }

    private void initializeBrowser(boolean addNavigationBar) {
        try {
            browser = new Browser(LIGHTWEIGHT, getBrowserContext());
        } catch (BrowserException unexpectedError) {
            throw unexpectedError;
        }

        browser.getContext().getSpellCheckerService().setEnabled(false);
        browserView = new BrowserView(browser);
        browser.getCacheStorage().clearCache();
        browser.setPopupHandler(new JxBrowserPopupHandler());
        browser.setLoadHandler(new LoadHandler() {
            @Override
            public boolean onLoad(LoadParams loadParams) {
                notifyLocationChange(loadParams.getURL());
                return false;
            }

            @Override
            public boolean onCertificateError(CertificateErrorParams certificateErrorParams) {
                return false;
            }
        });

        browser.addLoadListener(new LoadAdapter() {
            @Override
            public void onFinishLoadingFrame(FinishLoadingEvent event) {
                notifyContentChange(browser.getHTML());
            }

            @Override
            public void onFailLoadingFrame(FailLoadingEvent event) {
                if (!event.getValidatedURL().equals(fallBackPage)) {
                    log.info(messages.get("JxBrowserComponent.ShowFallbackPage", event.getValidatedURL(), fallBackPage));
                    navigate(fallBackPage);
                } else if (!showingErrorPage) {
                    showingErrorPage = true;
                    try {
                        setContent(DEFAULT_ERROR_PAGE);
                    } finally {
                        showingErrorPage = false;
                    }
                }
            }
        });
        browser.addTitleListener((titleEvent) -> {
            notifyTitleChange(titleEvent.getTitle());
        });
        browser.addConsoleListener(consoleEvent -> {
            if (consoleEvent.getLevel() == ConsoleEvent.Level.ERROR) {
                JSContext jsContext = browser.getJSContext();
                if (jsContext != null && !jsContext.isDisposed()) {
                    jsErrors.add(consoleEvent);
                }
            }
        });

        BrowserContext browserContext = browser.getContext();
        ProtocolService protocolService = browserContext.getProtocolService();
        protocolService.setProtocolHandler("jar", request -> {
            try {
                URLResponse response = new URLResponse();
                URL path = new URL(request.getURL());
                InputStream inputStream = path.openStream();
                DataInputStream stream = new DataInputStream(inputStream);
                byte[] data = new byte[stream.available()];
                stream.readFully(data);
                response.setData(data);
                String mimeType = getMimeType(path.toString());
                response.getHeaders().setHeader("Content-Type", mimeType);
                return response;
            } catch (Exception ignored) {
            }
            return null;
        });

        if (withContextMenu) {
            browser.setContextMenuHandler(new ContextMenu());
        }

        if (addNavigationBar) {
            toolbar = new JxBrowserToolbar(browserView);
            panel.add(toolbar, BorderLayout.NORTH);
        }
        panel.add(browserView, BorderLayout.CENTER);
    }

    private void notifyLocationChange(String newLocation) {
        lastLocation = newLocation;
        for (BrowserListener listener : listeners) {
            listener.locationChanged(newLocation);
        }
    }

    private void notifyContentChange(String newContent) {
        if (!browser.isDisposed()) {
            for (BrowserListener listener : listeners) {
                listener.contentChanged(newContent);
            }
        }
    }

    private void notifyTitleChange(String newTitle) {
        if (!browser.isDisposed()) {
            for (BrowserListener listener : listeners) {
                listener.titleChanged(newTitle);
            }
        }
    }

    @Override
    public Component getComponent() {
        return panel;
    }

    @Override
    public void navigate(String url) {
        navigate(url, null);
    }

    public void navigate(String url, String backupUrl) {
        if (SoapUI.isBrowserDisabled()) {
            return;
        }
        setFallBackPage(backupUrl);

        loadUrl(url);
    }

    private void loadUrl(final String url) {
        browser.loadURL(url);
    }

    @Override
    public void setContent(String contentAsString) {
        if (SoapUI.isBrowserDisabled()) {
            return;
        }

        browser.loadHTML(contentAsString);
        pcs.firePropertyChange("content", null, contentAsString);
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        pcs.addPropertyChangeListener(pcl);
    }


    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        pcs.removePropertyChangeListener(pcl);
    }

    @Override
    public void setContent(String contentAsString, String contentType) {
        browser.loadHTML(contentAsString);
    }

    public void setFallBackPage(String fallBackPage) {
        this.fallBackPage = fallBackPage;
    }

    @Override
    public void close(boolean cascade) {
        browserView.setVisible(false);
        browser.getCookieStorage().deleteAll();
        browser.dispose();
        for (BrowserListener listener : listeners) {
            listener.browserClosed();
        }
    }

    @Override
    public void addBrowserStateListener(BrowserListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeBrowserStateListener(BrowserListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void executeJavaScript(String script) {
        try {
            jsErrors.clear();
            browser.executeJavaScript(script);
        } catch (Exception e) {
            log.error("Error while executing js code", e);
            notifyScriptExecution(script, e);
            return;
        }

        JSContext jsContext = browser.getJSContext();
        if (jsContext != null) {
            jsContext.getDisposeListeners().clear();
            jsContext.addDisposeListener(disposeEvent -> {
                Exception exception = null;
                if (!jsErrors.isEmpty()) {
                    StringBuffer buffer = new StringBuffer();
                    jsErrors.stream().forEach(consoleEvent -> buffer.append(consoleEvent.getMessage()));
                    exception = new BrowserException(buffer.toString());
                }
                notifyScriptExecution(script, exception);
            });
        }
    }

    private void notifyScriptExecution(String script, Exception e) {
        for (BrowserListener listener : listeners) {
            listener.javaScriptExecuted(script, lastLocation, e);
        }
    }

    @Override
    public void addJavaScriptEventHandler(String memberName, Object eventHandler) {
        //due to jx browser documentation when onScriptContextCreated is fired:
        //web page is loaded completely, but JavaScript code on it hasn't been executed yet.
        browser.addScriptContextListener(new ScriptContextAdapter() {
            @Override
            public void onScriptContextCreated(ScriptContextEvent event) {
                JSValue window = browser.executeJavaScriptAndReturnValue("window");
                window.asObject().setProperty(memberName, eventHandler);
            }
        });
    }

    private static String getMimeType(String path) {
        if (path.endsWith(".html")) {
            return "text/html";
        }
        if (path.endsWith(".css")) {
            return "text/css";
        }
        if (path.endsWith(".js")) {
            return "text/javascript";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "text/html";
    }

    private class JxBrowserPopupHandler implements PopupHandler {

        @Override
        public PopupContainer handlePopup(PopupParams popupParams) {
            switch (popupStrategy) {
                case INTERNAL_BROWSER_NEW_WINDOW:
                    return (popupBrowser, initialBounds) -> SwingUtilities.invokeLater(() -> {
                        BrowserView popupView = new BrowserView(popupBrowser);
                        popupView.setPreferredSize(initialBounds.getSize());

                        final JFrame frame = new JFrame(messages.get("JxBrowserComponent.Caption"));
                        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                        frame.add(popupView, BorderLayout.CENTER);
                        frame.setIconImages(SoapUI.getFrameIcons());
                        frame.pack();
                        frame.setLocation(initialBounds.getLocation());
                        frame.setMinimumSize(new Dimension(800, 600));
                        frame.setVisible(true);
                        frame.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e) {
                                popupBrowser.dispose();
                            }
                        });
                        popupBrowser.addDisposeListener(event -> frame.setVisible(false));
                    });
                case EXTERNAL_BROWSER:
                    Tools.openURL(popupParams.getURL());
                    return null;
                case DISABLED:
                    return null;
                case INTERNAL_BROWSER_REUSE_WINDOW:
                    browser.loadURL(popupParams.getURL());
                default:
                    break;
            }
            return null;
        }
    }

    private class ContextMenu extends JPopupMenu implements ContextMenuHandler {

        private final Action cut = new AbstractAction("Cut") {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser.executeCommand(EditorCommand.CUT);
            }
        };
        private final Action copy = new AbstractAction("Copy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser.executeCommand(EditorCommand.COPY);
            }
        };
        private final Action paste = new AbstractAction("Paste") {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser.executeCommand(EditorCommand.PASTE);
            }
        };
        private final Action delete = new AbstractAction("Delete") {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser.executeCommand(EditorCommand.DELETE);
            }
        };

        ContextMenu() {
            add(cut);
            add(copy);
            add(paste);
            add(delete);
        }

        @Override
        public void showContextMenu(ContextMenuParams contextMenuParams) {
            boolean hasSelection = com.eviware.soapui.support.StringUtils.hasContent(contextMenuParams.getSelectionText());
            cut.setEnabled(hasSelection);
            copy.setEnabled(hasSelection);
            delete.setEnabled(hasSelection);
            this.show(browserView, contextMenuParams.getLocation().x, contextMenuParams.getLocation().y);
        }
    }
}
