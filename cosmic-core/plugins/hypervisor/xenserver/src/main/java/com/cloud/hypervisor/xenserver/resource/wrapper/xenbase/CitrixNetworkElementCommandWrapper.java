package com.cloud.hypervisor.xenserver.resource.wrapper.xenbase;

import com.cloud.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.cloud.hypervisor.xenserver.resource.CitrixResourceBase;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.command.NetworkElementCommand;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = NetworkElementCommand.class)
public final class CitrixNetworkElementCommandWrapper extends CommandWrapper<NetworkElementCommand, Answer, CitrixResourceBase> {

    @Override
    public Answer execute(final NetworkElementCommand command, final CitrixResourceBase citrixResourceBase) {
        final VirtualRoutingResource routingResource = citrixResourceBase.getVirtualRoutingResource();
        return routingResource.executeRequest(command);
    }
}
