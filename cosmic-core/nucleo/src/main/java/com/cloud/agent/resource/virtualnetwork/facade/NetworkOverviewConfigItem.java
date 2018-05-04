package com.cloud.agent.resource.virtualnetwork.facade;

import com.cloud.agent.resource.virtualnetwork.ConfigItem;
import com.cloud.agent.resource.virtualnetwork.VRScripts;
import com.cloud.legacymodel.communication.command.NetworkElementCommand;
import com.cloud.legacymodel.communication.command.UpdateNetworkOverviewCommand;

import java.util.List;

public class NetworkOverviewConfigItem extends AbstractConfigItemFacade {

    @Override
    public List<ConfigItem> generateConfig(final NetworkElementCommand cmd) {
        return generateConfigItems(((UpdateNetworkOverviewCommand) cmd).getNetworkOverview());
    }

    @Override
    protected List<ConfigItem> generateConfigItems(final Object configuration) {
        destinationFile = VRScripts.NETWORK_OVERVIEW_CONFIG;

        return super.generateConfigItems(configuration);
    }
}
