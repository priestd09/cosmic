package com.cloud.api.query.dao;

import com.cloud.api.query.vo.DiskOfferingJoinVO;
import com.cloud.api.response.DiskOfferingResponse;
import com.cloud.legacymodel.storage.DiskOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.utils.db.Attribute;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DiskOfferingJoinDaoImpl extends GenericDaoBase<DiskOfferingJoinVO, Long> implements DiskOfferingJoinDao {
    public static final Logger s_logger = LoggerFactory.getLogger(DiskOfferingJoinDaoImpl.class);

    private final SearchBuilder<DiskOfferingJoinVO> dofIdSearch;
    private final Attribute _typeAttr;

    protected DiskOfferingJoinDaoImpl() {

        dofIdSearch = createSearchBuilder();
        dofIdSearch.and("id", dofIdSearch.entity().getId(), SearchCriteria.Op.EQ);
        dofIdSearch.done();

        _typeAttr = _allAttributes.get("type");

        _count = "select count(distinct id) from disk_offering_view WHERE ";
    }

    @Override
    public DiskOfferingResponse newDiskOfferingResponse(final DiskOfferingJoinVO offering) {

        final DiskOfferingResponse diskOfferingResponse = new DiskOfferingResponse();
        diskOfferingResponse.setId(offering.getUuid());
        diskOfferingResponse.setName(offering.getName());
        diskOfferingResponse.setDisplayText(offering.getDisplayText());
        diskOfferingResponse.setProvisioningType(offering.getProvisioningType().toString());
        diskOfferingResponse.setCreated(offering.getCreated());
        diskOfferingResponse.setDiskSize(offering.getDiskSize() / (1024 * 1024 * 1024));
        diskOfferingResponse.setMinIops(offering.getMinIops());
        diskOfferingResponse.setMaxIops(offering.getMaxIops());

        diskOfferingResponse.setDomain(offering.getDomainName());
        diskOfferingResponse.setDomainId(offering.getDomainUuid());
        diskOfferingResponse.setDisplayOffering(offering.isDisplayOffering());

        diskOfferingResponse.setTags(offering.getTags());
        diskOfferingResponse.setCustomized(offering.isCustomized());
        diskOfferingResponse.setCustomizedIops(offering.isCustomizedIops());
        diskOfferingResponse.setHypervisorSnapshotReserve(offering.getHypervisorSnapshotReserve());
        diskOfferingResponse.setStorageType(offering.isUseLocalStorage() ? ServiceOffering.StorageType.local.toString() : ServiceOffering.StorageType.shared.toString());
        diskOfferingResponse.setBytesReadRate(offering.getBytesReadRate());
        diskOfferingResponse.setBytesWriteRate(offering.getBytesWriteRate());
        diskOfferingResponse.setIopsReadRate(offering.getIopsReadRate());
        diskOfferingResponse.setIopsWriteRate(offering.getIopsWriteRate());
        diskOfferingResponse.setCacheMode(offering.getCacheMode());
        diskOfferingResponse.setObjectName("diskoffering");

        return diskOfferingResponse;
    }

    @Override
    public DiskOfferingJoinVO newDiskOfferingView(final DiskOffering offering) {
        final SearchCriteria<DiskOfferingJoinVO> sc = dofIdSearch.create();
        sc.setParameters("id", offering.getId());
        final List<DiskOfferingJoinVO> offerings = searchIncludingRemoved(sc, null, null, false);
        assert offerings != null && offerings.size() == 1 : "No disk offering found for offering id " + offering.getId();
        return offerings.get(0);
    }
}
