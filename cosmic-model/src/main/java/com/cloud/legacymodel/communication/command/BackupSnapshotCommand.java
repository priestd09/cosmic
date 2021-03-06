package com.cloud.legacymodel.communication.command;

import com.cloud.legacymodel.communication.LogLevel;
import com.cloud.legacymodel.communication.LogLevel.Level;
import com.cloud.legacymodel.storage.StoragePool;
import com.cloud.legacymodel.to.StorageFilerTO;

/**
 * This currently assumes that both primary and secondary storage are mounted on the XenServer.
 */
public class BackupSnapshotCommand extends SnapshotCommand {
    StorageFilerTO pool;
    private String prevSnapshotUuid;
    private String prevBackupUuid;
    private boolean isVolumeInactive;
    private String vmName;
    private Long snapshotId;
    @LogLevel(Level.Off)
    private Long secHostId;

    protected BackupSnapshotCommand() {

    }

    /**
     * @param primaryStoragePoolNameLabel The UUID of the primary storage Pool
     * @param secondaryStoragePoolURL     This is what shows up in the UI when you click on Secondary storage.
     * @param snapshotUuid                The UUID of the snapshot which is going to be backed up
     * @param prevSnapshotUuid            The UUID of the previous snapshot for this volume. This will be destroyed on the primary storage.
     * @param prevBackupUuid              This is the UUID of the vhd file which was last backed up on secondary storage.
     * @param firstBackupUuid             This is the backup of the first ever snapshot taken by the volume.
     * @param isFirstSnapshotOfRootVolume true if this is the first snapshot of a root volume. Set the parent of the backup to null.
     * @param isVolumeInactive            True if the volume belongs to a VM that is not running or is detached.
     * @param secHostId                   This is the Id of the secondary storage.
     */
    public BackupSnapshotCommand(final String secondaryStoragePoolURL, final Long dcId, final Long accountId, final Long volumeId, final Long snapshotId, final Long secHostId,
                                 final String volumePath,
                                 final StoragePool pool, final String snapshotUuid, final String snapshotName, final String prevSnapshotUuid, final String prevBackupUuid, final
                                 boolean isVolumeInactive, final String
                                         vmName, final int wait) {
        super(pool, secondaryStoragePoolURL, snapshotUuid, snapshotName, dcId, accountId, volumeId);
        this.snapshotId = snapshotId;
        this.prevSnapshotUuid = prevSnapshotUuid;
        this.prevBackupUuid = prevBackupUuid;
        this.isVolumeInactive = isVolumeInactive;
        this.vmName = vmName;
        this.secHostId = secHostId;
        setVolumePath(volumePath);
        setWait(wait);
    }

    public String getPrevSnapshotUuid() {
        return prevSnapshotUuid;
    }

    public String getPrevBackupUuid() {
        return prevBackupUuid;
    }

    public boolean isVolumeInactive() {
        return isVolumeInactive;
    }

    public String getVmName() {
        return vmName;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public Long getSecHostId() {
        return secHostId;
    }
}
