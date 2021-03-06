package com.cloud.legacymodel.communication.answer;

import com.cloud.legacymodel.communication.command.ModifyStoragePoolCommand;
import com.cloud.legacymodel.storage.StoragePoolInfo;
import com.cloud.legacymodel.storage.TemplateProp;

import java.util.Map;

public class ModifyStoragePoolAnswer extends Answer {
    StoragePoolInfo poolInfo;
    Map<String, TemplateProp> templateInfo;
    String localDatastoreName = null;

    protected ModifyStoragePoolAnswer() {
    }

    public ModifyStoragePoolAnswer(final ModifyStoragePoolCommand cmd, final long capacityBytes, final long availableBytes, final Map<String, TemplateProp> tInfo) {
        super(cmd);
        this.result = true;
        this.poolInfo =
                new StoragePoolInfo(null, cmd.getPool().getHost(), cmd.getPool().getPath(), cmd.getLocalPath(), cmd.getPool().getType(), capacityBytes, availableBytes);

        this.templateInfo = tInfo;
    }

    public StoragePoolInfo getPoolInfo() {
        return poolInfo;
    }

    public void setPoolInfo(final StoragePoolInfo poolInfo) {
        this.poolInfo = poolInfo;
    }

    public Map<String, TemplateProp> getTemplateInfo() {
        return templateInfo;
    }

    public void setTemplateInfo(final Map<String, TemplateProp> templateInfo) {
        this.templateInfo = templateInfo;
    }

    public String getLocalDatastoreName() {
        return localDatastoreName;
    }

    public void setLocalDatastoreName(final String localDatastoreName) {
        this.localDatastoreName = localDatastoreName;
    }
}
