package com.cloud.engine.subsystem.api.storage;

import com.cloud.legacymodel.to.DataStoreTO;
import com.cloud.model.enumeration.DataStoreRole;

public interface DataStore {
    DataStoreDriver getDriver();

    DataStoreRole getRole();

    long getId();

    String getUuid();

    String getUri();

    Scope getScope();

    String getName();

    DataObject create(DataObject obj);

    boolean delete(DataObject obj);

    DataStoreTO getTO();
}
