package com.cloud.vm;

import com.cloud.legacymodel.storage.DiskOffering;
import com.cloud.model.enumeration.DiskControllerType;

public class VmWorkAttachVolume extends VmWork {
    private final Long volumeId;
    private final Long deviceId;
    private final DiskControllerType diskController;
    private final DiskOffering.DiskCacheMode diskCacheMode;

    public VmWorkAttachVolume(final long userId, final long accountId, final long vmId, final String handlerName, final Long volumeId, final Long deviceId,
                              final DiskControllerType diskController, final DiskOffering.DiskCacheMode diskCacheMode) {
        super(userId, accountId, vmId, handlerName);
        this.volumeId = volumeId;
        this.deviceId = deviceId;
        this.diskController = diskController;
        this.diskCacheMode = diskCacheMode;
    }

    public Long getVolumeId() {
        return volumeId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public DiskControllerType getDiskController() {
        return diskController;
    }

    public DiskOffering.DiskCacheMode getDiskCacheMode() {
        return diskCacheMode;
    }
}
