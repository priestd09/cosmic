package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.agent.resource.kvm.xml.LibvirtVmDef.InterfaceDef;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.ResourceWrapper;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.command.PvlanSetupCommand;
import com.cloud.utils.script.Script;

import java.util.List;

import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = PvlanSetupCommand.class)
public final class LibvirtPvlanSetupCommandWrapper
        extends CommandWrapper<PvlanSetupCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtPvlanSetupCommandWrapper.class);

    @Override
    public Answer execute(final PvlanSetupCommand command, final LibvirtComputingResource libvirtComputingResource) {
        final String primaryPvlan = command.getPrimary();
        final String isolatedPvlan = command.getIsolated();
        final String op = command.getOp();
        final String dhcpName = command.getDhcpName();
        final String dhcpMac = command.getDhcpMac();
        final String dhcpIp = command.getDhcpIp();
        final String vmMac = command.getVmMac();
        boolean add = true;

        String opr = "-A";
        if (op.equals("delete")) {
            opr = "-D";
            add = false;
        }

        String result = null;
        try {
            final String guestBridgeName = libvirtComputingResource.getGuestBridgeName();
            final int timeout = libvirtComputingResource.getScriptsTimeout();

            if (command.getType() == PvlanSetupCommand.Type.DHCP) {
                final String ovsPvlanDhcpHostPath = libvirtComputingResource.getOvsPvlanDhcpHostPath();
                final Script script = new Script(ovsPvlanDhcpHostPath, timeout, s_logger);

                if (add) {
                    final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();
                    final Connect conn = libvirtUtilitiesHelper.getConnectionByVmName(dhcpName);

                    final List<InterfaceDef> ifaces = libvirtComputingResource.getInterfaces(conn, dhcpName);
                    final InterfaceDef guestNic = ifaces.get(0);
                    script.add(opr, "-b", guestBridgeName, "-p", primaryPvlan, "-i", isolatedPvlan, "-n", dhcpName, "-d", dhcpIp,
                            "-m", dhcpMac, "-I",
                            guestNic.getDevName());
                } else {
                    script.add(opr, "-b", guestBridgeName, "-p", primaryPvlan, "-i", isolatedPvlan, "-n", dhcpName, "-d", dhcpIp,
                            "-m", dhcpMac);
                }

                result = script.execute();

                if (result != null) {
                    s_logger.warn("Failed to program pvlan for dhcp server with mac " + dhcpMac);
                    return new Answer(command, false, result);
                } else {
                    s_logger.info("Programmed pvlan for dhcp server with mac " + dhcpMac);
                }
            } else if (command.getType() == PvlanSetupCommand.Type.VM) {
                final String ovsPvlanVmPath = libvirtComputingResource.getOvsPvlanVmPath();

                final Script script = new Script(ovsPvlanVmPath, timeout, s_logger);
                script.add(opr, "-b", guestBridgeName, "-p", primaryPvlan, "-i", isolatedPvlan, "-v", vmMac);
                result = script.execute();

                if (result != null) {
                    s_logger.warn("Failed to program pvlan for vm with mac " + vmMac);
                    return new Answer(command, false, result);
                } else {
                    s_logger.info("Programmed pvlan for vm with mac " + vmMac);
                }
            }
        } catch (final LibvirtException e) {
            s_logger.error("Error whislt executing OVS Setup command! ==> " + e.getMessage());
            return new Answer(command, false, e.getMessage());
        }
        return new Answer(command, true, result);
    }
}
