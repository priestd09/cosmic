package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.agent.resource.kvm.storage.KvmPhysicalDisk;
import com.cloud.agent.resource.kvm.storage.KvmStoragePool;
import com.cloud.agent.resource.kvm.storage.KvmStoragePoolManager;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.ResourceWrapper;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.PrimaryStorageDownloadAnswer;
import com.cloud.legacymodel.communication.command.PrimaryStorageDownloadCommand;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;

import java.util.List;
import java.util.UUID;

@ResourceWrapper(handles = PrimaryStorageDownloadCommand.class)
public final class LibvirtPrimaryStorageDownloadCommandWrapper
        extends CommandWrapper<PrimaryStorageDownloadCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(final PrimaryStorageDownloadCommand command,
                          final LibvirtComputingResource libvirtComputingResource) {
        final String tmplturl = command.getUrl();
        final int index = tmplturl.lastIndexOf("/");
        final String mountpoint = tmplturl.substring(0, index);
        String tmpltname = null;

        if (index < tmplturl.length() - 1) {
            tmpltname = tmplturl.substring(index + 1);
        }

        KvmPhysicalDisk tmplVol = null;
        KvmStoragePool secondaryPool = null;
        final KvmStoragePoolManager storagePoolMgr = libvirtComputingResource.getStoragePoolMgr();
        try {
            secondaryPool = storagePoolMgr.getStoragePoolByUri(mountpoint);

            /* Get template vol */
            if (tmpltname == null) {
                secondaryPool.refresh();
                final List<KvmPhysicalDisk> disks = secondaryPool.listPhysicalDisks();
                if (disks == null || disks.isEmpty()) {
                    return new PrimaryStorageDownloadAnswer("Failed to get volumes from pool: " + secondaryPool.getUuid());
                }
                for (final KvmPhysicalDisk disk : disks) {
                    if (disk.getName().endsWith("qcow2")) {
                        tmplVol = disk;
                        break;
                    }
                }
                if (tmplVol == null) {
                    return new PrimaryStorageDownloadAnswer("Failed to get template from pool: " + secondaryPool.getUuid());
                }
            } else {
                tmplVol = secondaryPool.getPhysicalDisk(tmpltname);
            }

            /* Copy volume to primary storage */
            final KvmStoragePool primaryPool = storagePoolMgr.getStoragePool(command.getPool().getType(),
                    command.getPoolUuid());

            final KvmPhysicalDisk primaryVol = storagePoolMgr.copyPhysicalDisk(tmplVol, UUID.randomUUID().toString(),
                    primaryPool, 0);

            return new PrimaryStorageDownloadAnswer(primaryVol.getName(), primaryVol.getSize());
        } catch (final CloudRuntimeException e) {
            return new PrimaryStorageDownloadAnswer(e.toString());
        } finally {
            if (secondaryPool != null) {
                storagePoolMgr.deleteStoragePool(secondaryPool.getType(), secondaryPool.getUuid());
            }
        }
    }
}
