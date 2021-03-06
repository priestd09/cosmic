package com.cloud.legacymodel.communication.command;

import com.cloud.legacymodel.to.DataStoreTO;

public class ListVolumeCommand extends StorageCommand {

    private DataStoreTO store;
    private String secUrl;

    public ListVolumeCommand() {
    }

    public ListVolumeCommand(final DataStoreTO store, final String secUrl) {
        this.store = store;
        this.secUrl = secUrl;
    }

    @Override
    public boolean executeInSequence() {
        return true;
    }

    public String getSecUrl() {
        return secUrl;
    }

    public DataStoreTO getDataStore() {
        return store;
    }
}
