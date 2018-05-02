package com.cloud.api.response;

import com.cloud.api.ApiConstants;
import com.cloud.api.BaseResponse;
import com.cloud.api.EntityReference;
import com.cloud.legacymodel.network.vpc.VpcGateway;
import com.cloud.serializer.Param;

import com.google.gson.annotations.SerializedName;

@EntityReference(value = VpcGateway.class)
public class PrivateGatewayResponse extends BaseResponse implements ControlledEntityResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the private gateway")
    private String id;

    @SerializedName(ApiConstants.IP_ADDRESS)
    @Param(description = "the private gateway's ip address")
    private String address;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "zone id of the private gateway")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the zone the private gateway belongs to")
    private String zoneName;

    @SerializedName(ApiConstants.VLAN)
    @Param(description = "the network implementation uri for the private gateway")
    private String broadcastUri;

    @SerializedName(ApiConstants.VPC_ID)
    @Param(description = "VPC the private gateaway belongs to")
    private String vpcId;

    @SerializedName(ApiConstants.NETWORK_ID)
    @Param(description = "the network id")
    private String networkId;

    @SerializedName(ApiConstants.NETWORK_NAME)
    @Param(description = "the network name")
    private String networkName;

    @SerializedName(ApiConstants.CIDR)
    @Param(description = "the CIDR of the private network")
    private String cidr;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account associated with the private gateway")
    private String accountName;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the project id of the private gateway")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the project name of the private gateway")
    private String projectName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain associated with the private gateway")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the domain associated with the private gateway")
    private String domainName;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "State of the gateway, can be Creating, Ready, Deleting")
    private String state;

    @SerializedName(ApiConstants.SOURCE_NAT_SUPPORTED)
    @Param(description = "Souce Nat enable status")
    private Boolean sourceNat;

    @SerializedName(ApiConstants.ACL_ID)
    @Param(description = "ACL Id set for private gateway")
    private String aclId;

    @Override
    public String getObjectId() {
        return this.id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public void setZoneId(final String zoneId) {
        this.zoneId = zoneId;
    }

    public void setBroadcastUri(final String broadcastUri) {
        this.broadcastUri = broadcastUri;
    }

    public void setZoneName(final String zoneName) {
        this.zoneName = zoneName;
    }

    public void setVpcId(final String vpcId) {
        this.vpcId = vpcId;
    }

    public void setNetworkId(final String networkId) {
        this.networkId = networkId;
    }

    public void setNetworkName(final String networkName) {
        this.networkName = networkName;
    }

    public void setCidr(final String cidr) {
        this.cidr = cidr;
    }

    public void setAddress(final String address) {
        this.address = address;
    }

    @Override
    public void setAccountName(final String accountName) {
        this.accountName = accountName;
    }

    @Override
    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(final String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void setDomainId(final String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(final String domainName) {
        this.domainName = domainName;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setSourceNat(final Boolean sourceNat) {
        this.sourceNat = sourceNat;
    }

    public void setAclId(final String aclId) {
        this.aclId = aclId;
    }
}
