package com.cloud.legacymodel.to;

import com.cloud.legacymodel.InternalIdentity;
import com.cloud.model.enumeration.StoragePoolType;
import com.cloud.model.enumeration.VolumeType;

public class VolumeTO implements InternalIdentity {
    private long id;
    private String name;
    private String mountPoint;
    private String path;
    private long size;
    private VolumeType type;
    private StoragePoolType storagePoolType;
    private String storagePoolUuid;
    private long deviceId;
    private String chainInfo;
    private String guestOsType;
    private Long bytesReadRate;
    private Long bytesWriteRate;
    private Long iopsReadRate;
    private Long iopsWriteRate;
    private String cacheMode;
    private Long chainSize;

    protected VolumeTO() {
    }

    public VolumeTO(final long id, final VolumeType type, final StoragePoolType poolType, final String poolUuid, final String name, final String mountPoint, final String path,
                    final long size, final String chainInfo) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.size = size;
        this.type = type;
        this.storagePoolType = poolType;
        this.storagePoolUuid = poolUuid;
        this.mountPoint = mountPoint;
        this.chainInfo = chainInfo;
    }

    public VolumeTO(final long id, final VolumeType type, final StoragePoolType poolType, final String poolUuid, final String name, final String mountPoint, final String path,
                    final long size, final String chainInfo,
                    final String guestOsType) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.size = size;
        this.type = type;
        this.storagePoolType = poolType;
        this.storagePoolUuid = poolUuid;
        this.mountPoint = mountPoint;
        this.chainInfo = chainInfo;
        this.guestOsType = guestOsType;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final long id) {
        this.deviceId = id;
    }

    @Override
    public long getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public VolumeType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public StoragePoolType getPoolType() {
        return storagePoolType;
    }

    public String getPoolUuid() {
        return storagePoolUuid;
    }

    public String getChainInfo() {
        return chainInfo;
    }

    public void setChainInfo(final String chainInfo) {
        this.chainInfo = chainInfo;
    }

    public String getOsType() {
        return guestOsType;
    }

    @Override
    public String toString() {
        return new StringBuilder("Vol[").append(id).append("|").append(type).append("|").append(path).append("|").append(size).append("]").toString();
    }

    public Long getBytesReadRate() {
        return bytesReadRate;
    }

    public void setBytesReadRate(final Long bytesReadRate) {
        this.bytesReadRate = bytesReadRate;
    }

    public Long getBytesWriteRate() {
        return bytesWriteRate;
    }

    public void setBytesWriteRate(final Long bytesWriteRate) {
        this.bytesWriteRate = bytesWriteRate;
    }

    public Long getIopsReadRate() {
        return iopsReadRate;
    }

    public void setIopsReadRate(final Long iopsReadRate) {
        this.iopsReadRate = iopsReadRate;
    }

    public Long getIopsWriteRate() {
        return iopsWriteRate;
    }

    public void setIopsWriteRate(final Long iopsWriteRate) {
        this.iopsWriteRate = iopsWriteRate;
    }

    public String getCacheMode() {
        return cacheMode;
    }

    public void setCacheMode(final String cacheMode) {
        this.cacheMode = cacheMode;
    }

    public Long getChainSize() {
        return chainSize;
    }

    public void setChainSize(final Long chainSize) {
        this.chainSize = chainSize;
    }
}
