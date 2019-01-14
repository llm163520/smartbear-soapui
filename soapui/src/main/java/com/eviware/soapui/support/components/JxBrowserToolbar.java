package com.eviware.soapui.support.components;

import com.eviware.soapui.support.MessageSupport;
import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.events.FinishLoadingEvent;
import com.teamdev.jxbrowser.chromium.events.LoadAdapter;
import com.teamdev.jxbrowser.chromium.events.ProvisionalLoadingEvent;
import com.teamdev.jxbrowser.chromium.events.StartLoadingEvent;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

public class JxBrowserToolbar extends JPanel {
    private static final MessageSupport messages = MessageSupport.getMessages(JxBrowserToolbar.class);
    private static final String DEFAULT_URL = "about:blank";
    private final JTextField addressBar;
    private final BrowserView browserView;
    private JButton backwardButton;
    private JButton forwardButton;
    private JButton refreshButton;
    private JButton stopButton;

    public JxBrowserToolbar(BrowserView browserView) {
        this.browserView = browserView;
        addressBar = createAddressBar();
        setLayout(new GridBagLayout());
        add(createActionsPane(),
                new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                        GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        add(addressBar, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0,
                GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(4, 0, 4, 5), 0, 0));
    }

    private static JButton createBackwardButton(final Browser browser) {
        return createButton(messages.get("JxBrowserToolbar.BackButton"), new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                browser.goBack();
            }
        });
    }

    private static JButton createForwardButton(final Browser browser) {
        return createButton(messages.get("JxBrowserToolbar.ForwardButton"), new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                browser.goForward();
            }
        });
    }

    private static JButton createRefreshButton(final Browser browser) {
        return createButton(messages.get("JxBrowserToolbar.RefreshButton"), new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                browser.reload();
            }
        });
    }

    private static JButton createStopButton(final Browser browser) {
        return createButton(messages.get("JxBrowserToolbar.StopButton"), new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                browser.stop();
            }
        });
    }

    private static JButton createButton(String caption, Action action) {
        ActionButton button = new ActionButton(caption, action);
        return button;
    }

    private JPanel createActionsPane() {
        backwardButton = createBackwardButton(browserView.getBrowser());
        forwardButton = createForwardButton(browserView.getBrowser());
        refreshButton = createRefreshButton(browserView.getBrowser());
        stopButton = createStopButton(browserView.getBrowser());

        JPanel actionsPanel = new JPanel();
        actionsPanel.add(backwardButton);
        actionsPanel.add(forwardButton);
        actionsPanel.add(refreshButton);
        actionsPanel.add(stopButton);
        return actionsPanel;
    }

    private JTextField createAddressBar() {
        final JTextField result = new JTextField(DEFAULT_URL);
        result.addActionListener(e -> browserView.getBrowser().loadURL(result.getText()));

        browserView.getBrowser().addLoadListener(new LoadAdapter() {
            @Override
            public void onStartLoadingFrame(StartLoadingEvent event) {
                if (event.isMainFrame()) {
                    SwingUtilities.invokeLater(() -> {
                        refreshButton.setEnabled(false);
                        stopButton.setEnabled(true);
                    });
                }
            }

            @Override
            public void onProvisionalLoadingFrame(final ProvisionalLoadingEvent event) {
                if (event.isMainFrame()) {
                    SwingUtilities.invokeLater(() -> {
                        result.setText(event.getURL());
                        result.setCaretPosition(result.getText().length());

                        Browser browser = event.getBrowser();
                        forwardButton.setEnabled(browser.canGoForward());
                        backwardButton.setEnabled(browser.canGoBack());
                    });
                }
            }

            @Override
            public void onFinishLoadingFrame(final FinishLoadingEvent event) {
                if (event.isMainFrame()) {
                    SwingUtilities.invokeLater(() -> {
                        refreshButton.setEnabled(true);
                        stopButton.setEnabled(false);
                    });
                }
            }
        });
        return result;
    }

    private boolean isFocusRequired() {
        String url = addressBar.getText();
        return url.isEmpty() || url.equals(DEFAULT_URL);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            if (isFocusRequired()) {
                addressBar.requestFocus();
                addressBar.selectAll();
            }
        });
    }

    private static class ActionButton extends JButton {
        private ActionButton(String hint, Action action) {
            super(action);
            setContentAreaFilled(false);
            setBorder(BorderFactory.createEmptyBorder());
            setBorderPainted(false);
            setRolloverEnabled(true);
            setToolTipText(hint);
            setText(null);
            setFocusable(false);
            setDefaultCapable(false);
        }
    }
}
