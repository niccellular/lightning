
package com.atakmap.android.lightning.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import com.atakmap.android.lightning.LightningMapComponent;
import com.atakmap.coremap.log.Log;

public class LightningLifecycle extends AbstractPlugin implements IPlugin {

    public LightningLifecycle(IServiceController serviceController) {
        super(serviceController, new LightningTool(serviceController.getService(PluginContextProvider.class).getPluginContext()), new LightningMapComponent());
    }
}