package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.agent.resource.kvm.storage.KvmStoragePoolManager;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.ResourceWrapper;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.PrepareForMigrationAnswer;
import com.cloud.legacymodel.communication.command.PrepareForMigrationCommand;
import com.cloud.legacymodel.exceptions.InternalErrorException;
import com.cloud.legacymodel.to.DiskTO;
import com.cloud.legacymodel.to.NicTO;
import com.cloud.legacymodel.to.VirtualMachineTO;
import com.cloud.model.enumeration.VolumeType;

import java.net.URISyntaxException;

import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = PrepareForMigrationCommand.class)
public final class LibvirtPrepareForMigrationCommandWrapper
        extends CommandWrapper<PrepareForMigrationCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtPrepareForMigrationCommandWrapper.class);

    @Override
    public Answer execute(final PrepareForMigrationCommand command,
                          final LibvirtComputingResource libvirtComputingResource) {
        final VirtualMachineTO vm = command.getVirtualMachine();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Preparing host for migrating " + vm);
        }

        final NicTO[] nics = vm.getNics();

        boolean skipDisconnect = false;

        final KvmStoragePoolManager storagePoolMgr = libvirtComputingResource.getStoragePoolMgr();
        try {
            final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

            final Connect conn = libvirtUtilitiesHelper.getConnectionByVmName(vm.getName());
            for (final NicTO nic : nics) {
                libvirtComputingResource.getVifDriver(nic.getType()).plug(nic, null, "");
            }

            /* setup disks, e.g for iso */
            final DiskTO[] volumes = vm.getDisks();
            for (final DiskTO volume : volumes) {
                if (volume.getType() == VolumeType.ISO) {
                    libvirtComputingResource.getVolumePath(conn, volume);
                }
            }

            skipDisconnect = true;

            if (!storagePoolMgr.connectPhysicalDisksViaVmSpec(vm)) {
                return new PrepareForMigrationAnswer(command, "failed to connect physical disks to host");
            }

            return new PrepareForMigrationAnswer(command);
        } catch (final LibvirtException e) {
            return new PrepareForMigrationAnswer(command, e.toString());
        } catch (final InternalErrorException e) {
            return new PrepareForMigrationAnswer(command, e.toString());
        } catch (final URISyntaxException e) {
            return new PrepareForMigrationAnswer(command, e.toString());
        } finally {
            if (!skipDisconnect) {
                storagePoolMgr.disconnectPhysicalDisksViaVmSpec(vm);
            }
        }
    }
}
