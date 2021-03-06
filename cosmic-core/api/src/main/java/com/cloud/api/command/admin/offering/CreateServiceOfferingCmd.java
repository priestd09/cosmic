package com.cloud.api.command.admin.offering;

import com.cloud.api.APICommand;
import com.cloud.api.APICommandGroup;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ServiceOfferingResponse;
import com.cloud.legacymodel.storage.StorageProvisioningType;
import com.cloud.legacymodel.user.Account;
import com.cloud.offering.ServiceOffering;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createServiceOffering", group = APICommandGroup.ServiceOfferingService, description = "Creates a service offering.", responseObject = ServiceOfferingResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateServiceOfferingCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateServiceOfferingCmd.class.getName());
    private static final String s_name = "createserviceofferingresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.PROVISIONINGTYPE, type = CommandType.STRING, description = "provisioning type used to create volumes. Valid values are thin, sparse, fat.")
    private final String provisioningType = StorageProvisioningType.THIN.toString();
    @Parameter(name = ApiConstants.CPU_NUMBER, type = CommandType.INTEGER, required = false, description = "the CPU number of the service offering")
    private Integer cpuNumber;
    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, required = true, description = "the display text of the service offering")
    private String displayText;
    @Parameter(name = ApiConstants.MEMORY, type = CommandType.INTEGER, required = false, description = "the total memory of the service offering in MB")
    private Integer memory;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the service offering")
    private String serviceOfferingName;

    @Parameter(name = ApiConstants.OFFER_HA, type = CommandType.BOOLEAN, description = "the HA for the service offering")
    private Boolean offerHa;

    @Parameter(name = ApiConstants.LIMIT_CPU_USE, type = CommandType.BOOLEAN, description = "restrict the CPU usage to committed service offering")
    private Boolean limitCpuUse;

    @Parameter(name = ApiConstants.IS_VOLATILE,
            type = CommandType.BOOLEAN,
            description = "true if the virtual machine needs to be volatile so that on every reboot of VM, original root disk is dettached then destroyed and a fresh root disk " +
                    "is created and attached to VM")
    private Boolean isVolatile;

    @Parameter(name = ApiConstants.STORAGE_TYPE, type = CommandType.STRING, description = "the storage type of the service offering. Values are local and shared.")
    private String storageType;

    @Parameter(name = ApiConstants.TAGS, type = CommandType.STRING, description = "the tags for this service offering.")
    private String tags;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "the ID of the containing domain, null for public offerings")
    private Long domainId;

    @Parameter(name = ApiConstants.HOST_TAGS, type = CommandType.STRING, description = "the host tag for this service offering.")
    private String hostTag;

    @Parameter(name = ApiConstants.IS_SYSTEM_OFFERING, type = CommandType.BOOLEAN, description = "is this a system vm offering")
    private Boolean isSystem;

    @Parameter(name = ApiConstants.SYSTEM_VM_TYPE,
            type = CommandType.STRING,
            description = "the system VM type. Possible types are \"domainrouter\", \"consoleproxy\" and \"secondarystoragevm\".")
    private String systemVmType;

    @Parameter(name = ApiConstants.NETWORKRATE,
            type = CommandType.INTEGER,
            description = "data transfer rate in megabits per second allowed. Supported only for non-System offering and system offerings having \"domainrouter\" systemvmtype")
    private Integer networkRate;

    @Parameter(name = ApiConstants.DEPLOYMENT_PLANNER,
            type = CommandType.STRING,
            description = "The deployment planner heuristics used to deploy a VM of this offering. If null, value of global config vm.deployment.planner is used")
    private String deploymentPlanner;

    @Parameter(name = ApiConstants.SERVICE_OFFERING_DETAILS, type = CommandType.MAP, description = "details for planner, used to store specific parameters")
    private Map details;

    @Parameter(name = ApiConstants.BYTES_READ_RATE, type = CommandType.LONG, required = false, description = "bytes read rate of the disk offering")
    private Long bytesReadRate;

    @Parameter(name = ApiConstants.BYTES_WRITE_RATE, type = CommandType.LONG, required = false, description = "bytes write rate of the disk offering")
    private Long bytesWriteRate;

    @Parameter(name = ApiConstants.IOPS_READ_RATE, type = CommandType.LONG, required = false, description = "io requests read rate of the disk offering")
    private Long iopsReadRate;

    @Parameter(name = ApiConstants.IOPS_WRITE_RATE, type = CommandType.LONG, required = false, description = "io requests write rate of the disk offering")
    private Long iopsWriteRate;

    @Parameter(name = ApiConstants.CUSTOMIZED_IOPS, type = CommandType.BOOLEAN, required = false, description = "whether compute offering iops is custom or not", since = "4.4")
    private Boolean customizedIops;

    @Parameter(name = ApiConstants.MIN_IOPS, type = CommandType.LONG, required = false, description = "min iops of the compute offering", since = "4.4")
    private Long minIops;

    @Parameter(name = ApiConstants.MAX_IOPS, type = CommandType.LONG, required = false, description = "max iops of the compute offering", since = "4.4")
    private Long maxIops;

    @Parameter(name = ApiConstants.HYPERVISOR_SNAPSHOT_RESERVE,
            type = CommandType.INTEGER,
            required = false,
            description = "Hypervisor snapshot reserve space as a percent of a volume (for managed storage using Xen)",
            since = "4.4")
    private Integer hypervisorSnapshotReserve;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Integer getCpuNumber() {
        return cpuNumber;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getProvisioningType() {
        return provisioningType;
    }

    public Integer getMemory() {
        return memory;
    }

    public String getServiceOfferingName() {
        return serviceOfferingName;
    }

    public Boolean getOfferHa() {
        return offerHa == null ? Boolean.FALSE : offerHa;
    }

    public Boolean GetLimitCpuUse() {
        return limitCpuUse == null ? Boolean.FALSE : limitCpuUse;
    }

    public Boolean getVolatileVm() {
        return isVolatile == null ? Boolean.FALSE : isVolatile;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getTags() {
        return tags;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getHostTag() {
        return hostTag;
    }

    public Boolean getIsSystem() {
        return isSystem == null ? false : isSystem;
    }

    public String getSystemVmType() {
        return systemVmType;
    }

    public Integer getNetworkRate() {
        return networkRate;
    }

    public String getDeploymentPlanner() {
        return deploymentPlanner;
    }

    public boolean getCustomized() {
        return cpuNumber == null || memory == null;
    }

    public Map<String, String> getDetails() {
        Map<String, String> detailsMap = null;
        if (details != null && !details.isEmpty()) {
            detailsMap = new HashMap<>();
            final Collection<?> props = details.values();
            final Iterator<?> iter = props.iterator();
            while (iter.hasNext()) {
                final HashMap<String, String> detail = (HashMap<String, String>) iter.next();
                detailsMap.put(detail.get("key"), detail.get("value"));
            }
        }
        return detailsMap;
    }

    public Long getBytesReadRate() {
        return bytesReadRate;
    }

    public Long getBytesWriteRate() {
        return bytesWriteRate;
    }

    public Long getIopsReadRate() {
        return iopsReadRate;
    }

    public Long getIopsWriteRate() {
        return iopsWriteRate;
    }

    public Boolean isCustomizedIops() {
        return customizedIops;
    }

    public Long getMinIops() {
        return minIops;
    }

    public Long getMaxIops() {
        return maxIops;
    }

    public Integer getHypervisorSnapshotReserve() {
        return hypervisorSnapshotReserve;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final ServiceOffering result = _configService.createServiceOffering(this);
        if (result != null) {
            final ServiceOfferingResponse response = _responseGenerator.createServiceOfferingResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create service offering");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
