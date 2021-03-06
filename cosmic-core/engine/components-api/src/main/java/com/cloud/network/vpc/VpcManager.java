package com.cloud.network.vpc;

import com.cloud.legacymodel.acl.ControlledEntity.ACLType;
import com.cloud.legacymodel.exceptions.ConcurrentOperationException;
import com.cloud.legacymodel.exceptions.InsufficientAddressCapacityException;
import com.cloud.legacymodel.exceptions.InsufficientCapacityException;
import com.cloud.legacymodel.exceptions.ResourceAllocationException;
import com.cloud.legacymodel.exceptions.ResourceUnavailableException;
import com.cloud.legacymodel.network.Network;
import com.cloud.legacymodel.network.Network.Provider;
import com.cloud.legacymodel.network.Network.Service;
import com.cloud.legacymodel.network.vpc.PrivateGateway;
import com.cloud.legacymodel.network.vpc.Vpc;
import com.cloud.legacymodel.user.Account;
import com.cloud.model.enumeration.HypervisorType;
import com.cloud.network.IpAddress;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.addr.PublicIp;
import com.cloud.offering.NetworkOffering;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VpcManager {
    /**
     * Returns all the Guest networks that are part of VPC
     *
     * @param vpcId
     * @return
     */
    public List<? extends Network> getVpcNetworks(long vpcId);

    /**
     * Returns all existing VPCs for a given account
     *
     * @param accountId
     * @return
     */
    List<? extends Vpc> getVpcsForAccount(long accountId);

    /**
     * Destroys the VPC
     *
     * @param vpc
     * @param caller       TODO
     * @param callerUserId TODO
     * @return
     * @throws ConcurrentOperationException
     * @throws ResourceUnavailableException
     */
    boolean destroyVpc(Vpc vpc, Account caller, Long callerUserId) throws ConcurrentOperationException, ResourceUnavailableException;

    /**
     * Returns true if the IP is allocated to the VPC; false otherwise
     *
     * @param ip
     * @return
     */
    boolean isIpAllocatedToVpc(IpAddress ip);

    /**
     * Disassociates the public IP address from VPC
     *
     * @param ipId
     * @param networkId
     */
    void unassignIPFromVpcNetwork(long ipId, long networkId);

    /**
     * Creates guest network in the VPC
     *
     * @param ntwkOffId
     * @param name
     * @param displayText
     * @param gateway
     * @param cidr
     * @param vlanId
     * @param networkDomain
     * @param owner
     * @param domainId
     * @param pNtwk
     * @param zoneId
     * @param aclType
     * @param subdomainAccess
     * @param vpcId
     * @param caller
     * @param displayNetworkEnabled
     * @param ipExclusionList
     * @return
     * @throws ConcurrentOperationException
     * @throws InsufficientCapacityException
     * @throws ResourceAllocationException
     */
    Network
    createVpcGuestNetwork(long ntwkOffId, String name, String displayText, String gateway, String cidr, String vlanId, String networkDomain, Account owner,
                          Long domainId, PhysicalNetwork pNtwk, long zoneId, ACLType aclType, Boolean subdomainAccess, long vpcId, Long aclId, Account caller,
                          Boolean displayNetworkEnabled, String dns1, String dns2, final String ipExclusionList)

            throws ConcurrentOperationException, InsufficientCapacityException, ResourceAllocationException;

    /**
     * Assigns source nat public IP address to VPC
     *
     * @param owner
     * @param vpc
     * @return public IP address object
     * @throws InsufficientAddressCapacityException
     * @throws ConcurrentOperationException
     */
    PublicIp assignSourceNatIpAddressToVpc(Account owner, Vpc vpc) throws InsufficientAddressCapacityException, ConcurrentOperationException;

    /**
     * Validates network offering to find if it can be used for network creation in VPC
     *
     * @param guestNtwkOff
     * @param supportedSvcs TODO
     */
    void validateNtwkOffForVpc(NetworkOffering guestNtwkOff, List<Service> supportedSvcs);

    /**
     * @return list of hypervisors that are supported by VPC
     */
    List<HypervisorType> getSupportedVpcHypervisors();

    /**
     * Lists all the services and providers that the current VPC suppots
     *
     * @param vpcOffId
     * @return map of Service to Provider(s) map
     */
    Map<Service, Set<Provider>> getVpcOffSvcProvidersMap(long vpcOffId);

    /**
     * Returns VPC that is ready to be used
     *
     * @param vpcId
     * @return VPC object
     */
    public Vpc getActiveVpc(long vpcId);

    /**
     * Performs network offering validation to determine if it can be used for network upgrade inside the VPC
     *
     * @param networkId
     * @param newNtwkOffId
     * @param newCidr
     * @param newNetworkDomain
     * @param vpc
     * @param gateway
     * @param networkOwner     TODO
     */
    void
    validateNtwkOffForNtwkInVpc(Long networkId, long newNtwkOffId, String newCidr, String newNetworkDomain, Vpc vpc, String gateway, Account networkOwner, Long aclId);

    List<PrivateGateway> getVpcPrivateGateways(long vpcId);
}
