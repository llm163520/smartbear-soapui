package com.eviware.soapui.impl.actions.explorer;

import com.eviware.soapui.analytics.ModuleType;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.WebViewBasedBrowserComponent;
import com.eviware.soapui.support.components.WebViewBasedBrowserComponentFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

import static com.eviware.soapui.SoapUI.getWorkspace;

public class EndpointExplorerAction extends AbstractAction {


    public EndpointExplorerAction() {
        putValue(Action.SMALL_ICON, UISupport.createImageIcon("/preferences_toolbar_icon.png"));
        putValue(Action.SHORT_DESCRIPTION, "Sets Global SoapUI Preferences");
        putValue(Action.NAME, "Preferences");
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        WebViewBasedBrowserComponent browser =
                WebViewBasedBrowserComponentFactory.createBrowserComponent
                        (false, WebViewBasedBrowserComponent.PopupStrategy.EXTERNAL_BROWSER);
        Component component = browser.getComponent();
        component.setMinimumSize(new Dimension(860, 419));

        JFrame f = new JFrame();
        f.getContentPane().add(component);
        f.setTitle("Endpoint Explorer");
        f.setSize(860, 419);
        f.setLocationRelativeTo(null);
        f.setResizable(false);
        f.setVisible(true);

        browser.navigate("file://" + "C:\\Users\\Christina.Zelenko\\Desktop\\soapOS\\soapui\\soapui\\src\\main\\resources\\com\\eviware\\soapui\\explorer\\soapui-pro-api-endpoint-explorer-starter-page.html");

        browser.addJavaScriptEventHandler("inspectorCallback", new EndpointExplorerCallback());
        browser.addJavaScriptEventHandler("moduleStarterPageCallback", new ModuleStarterPageCallback(getWorkspace(), ModuleType.SOAPUI_NG.getId()));


//        browser.loadDataWithBaseURL(null,"<script>   </script>","text/html","utf-8",null);
//        browser.executeJavaScript();
//
//        browser.addScriptContextListener(new ScriptContextAdapter() {
//            @Override
//            public void onScriptContextCreated(ScriptContextEvent event) {
//                JSValue window = browser.executeJavaScriptAndReturnValue("window");
//                window.asObject().setProperty(FunctionalModuleInspectorCallback.CALLBACK, new FunctionalModuleInspectorCallback());
//            }
//        });
//
//        browser.loadURL(helpURL.toString());
    }
}
