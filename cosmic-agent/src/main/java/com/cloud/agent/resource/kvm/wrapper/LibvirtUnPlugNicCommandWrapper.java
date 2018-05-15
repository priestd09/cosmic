package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.agent.resource.kvm.vif.VifDriver;
import com.cloud.agent.resource.kvm.xml.LibvirtVmDef.InterfaceDef;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.ResourceWrapper;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.UnPlugNicAnswer;
import com.cloud.legacymodel.communication.command.UnPlugNicCommand;
import com.cloud.legacymodel.to.NicTO;

import java.util.List;

import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = UnPlugNicCommand.class)
public final class LibvirtUnPlugNicCommandWrapper
        extends CommandWrapper<UnPlugNicCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtUnPlugNicCommandWrapper.class);

    @Override
    public Answer execute(final UnPlugNicCommand command, final LibvirtComputingResource libvirtComputingResource) {
        final NicTO nic = command.getNic();
        final String vmName = command.getVmName();
        Domain vm = null;
        try {
            final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

            final Connect conn = libvirtUtilitiesHelper.getConnectionByVmName(vmName);
            vm = libvirtComputingResource.getDomain(conn, vmName);
            final List<InterfaceDef> pluggedNics = libvirtComputingResource.getInterfaces(conn, vmName);

            for (final InterfaceDef pluggedNic : pluggedNics) {
                if (pluggedNic.getMacAddress().equalsIgnoreCase(nic.getMac())) {
                    vm.detachDevice(pluggedNic.toString());
                    // We don't know which "traffic type" is associated with
                    // each interface at this point, so inform all vif drivers
                    for (final VifDriver vifDriver : libvirtComputingResource.getAllVifDrivers()) {
                        vifDriver.unplug(pluggedNic);
                    }
                    return new UnPlugNicAnswer(command, true, "success");
                }
            }
            return new UnPlugNicAnswer(command, true, "success");
        } catch (final LibvirtException e) {
            final String msg = " Unplug Nic failed due to " + e.toString();
            s_logger.warn(msg, e);
            return new UnPlugNicAnswer(command, false, msg);
        } finally {
            if (vm != null) {
                try {
                    vm.free();
                } catch (final LibvirtException l) {
                    s_logger.trace("Ignoring libvirt error.", l);
                }
            }
        }
    }
}
