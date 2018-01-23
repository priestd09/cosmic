package com.cloud.api.command.user.volume;

import com.cloud.api.APICommand;
import com.cloud.api.APICommandGroup;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DiskOfferingResponse;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.VolumeResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.storage.Volume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "uploadVolume", group = APICommandGroup.VolumeService, description = "Uploads a data disk.", responseObject = VolumeResponse.class, responseView = ResponseView.Restricted, entityType = {Volume.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UploadVolumeCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UploadVolumeCmd.class.getName());
    private static final String s_name = "uploadvolumeresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.FORMAT,
            type = CommandType.STRING,
            required = true,
            description = "the format for the volume. Possible values include QCOW2, OVA, and VHD.")
    private String format;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the volume")
    private String volumeName;

    @Parameter(name = ApiConstants.URL,
            type = CommandType.STRING,
            required = true,
            length = 2048,
            description = "the URL of where the volume is hosted. Possible URL include http:// and https://")
    private String url;

    @Parameter(name = ApiConstants.ZONE_ID,
            type = CommandType.UUID,
            entityType = ZoneResponse.class,
            required = true,
            description = "the ID of the zone the volume is to be hosted on")
    private Long zoneId;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "an optional domainId. If the account parameter is used, domainId must also be used.")
    private Long domainId;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an optional accountName. Must be used with domainId.")
    private String accountName;

    @Parameter(name = ApiConstants.CHECKSUM, type = CommandType.STRING, description = "the MD5 checksum value of this volume")
    private String checksum;

    @Parameter(name = ApiConstants.IMAGE_STORE_UUID, type = CommandType.STRING, description = "Image store uuid")
    private String imageStoreUuid;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Upload volume for the project")
    private Long projectId;

    @Parameter(name = ApiConstants.DISK_OFFERING_ID, required = false, type = CommandType.UUID, entityType = DiskOfferingResponse.class, description = "the ID of the disk " +
            "offering. This must be a custom sized offering since during uploadVolume volume size is unknown.")
    private Long diskOfferingId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getFormat() {
        return format;
    }

    public String getUrl() {
        return url;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getImageStoreUuid() {
        return imageStoreUuid;
    }

    public Long getDiskOfferingId() {
        return diskOfferingId;
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
            ResourceAllocationException, NetworkRuleConflictException {

        final Volume volume = _volumeService.uploadVolume(this);
        if (volume != null) {
            final VolumeResponse response = _responseGenerator.createVolumeResponse(ResponseView.Restricted, volume);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to upload volume");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public long getEntityOwnerId() {
        final Long accountId = _accountService.finalyzeAccountId(accountName, domainId, projectId, true);
        if (accountId == null) {
            return CallContext.current().getCallingAccount().getId();
        }

        return accountId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VOLUME_UPLOAD;
    }

    @Override
    public String getEventDescription() {
        return "uploading volume: " + getVolumeName() + " in the zone " + getZoneId();
    }

    public String getVolumeName() {
        return volumeName;
    }

    public Long getZoneId() {
        return zoneId;
    }
}
