package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.ResourceWrapper;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.GetVmDiskStatsAnswer;
import com.cloud.legacymodel.communication.command.GetVmDiskStatsCommand;
import com.cloud.legacymodel.storage.VmDiskStatsEntry;

import java.util.HashMap;
import java.util.List;

import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = GetVmDiskStatsCommand.class)
public final class LibvirtGetVmDiskStatsCommandWrapper
        extends CommandWrapper<GetVmDiskStatsCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtGetVmDiskStatsCommandWrapper.class);

    @Override
    public Answer execute(final GetVmDiskStatsCommand command, final LibvirtComputingResource libvirtComputingResource) {
        final List<String> vmNames = command.getVmNames();
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

        try {
            final HashMap<String, List<VmDiskStatsEntry>> vmDiskStatsNameMap = new HashMap<>();
            final Connect conn = libvirtUtilitiesHelper.getConnection();
            for (final String vmName : vmNames) {
                try {
                    final List<VmDiskStatsEntry> statEntry = libvirtComputingResource.getVmDiskStat(conn, vmName);
                    if (statEntry == null) {
                        continue;
                    }

                    vmDiskStatsNameMap.put(vmName, statEntry);
                } catch (final LibvirtException e) {
                    s_logger.warn("Can't get vm disk stats: " + e.toString() + ", continue");
                }
            }
            return new GetVmDiskStatsAnswer(command, "", command.getHostName(), vmDiskStatsNameMap);
        } catch (final LibvirtException e) {
            s_logger.debug("Can't get vm disk stats: " + e.toString());
            return new GetVmDiskStatsAnswer(command, null, null, null);
        }
    }
}
