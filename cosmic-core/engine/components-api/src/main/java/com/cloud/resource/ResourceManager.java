package com.cloud.resource;

import com.cloud.common.request.ResourceListener;
import com.cloud.common.resource.ServerResource;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.PodCluster;
import com.cloud.gpu.HostGpuGroupsVO;
import com.cloud.host.HostVO;
import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.legacymodel.communication.command.StartupRoutingCommand;
import com.cloud.legacymodel.dc.Host;
import com.cloud.legacymodel.dc.HostStats;
import com.cloud.legacymodel.dc.HostStatus;
import com.cloud.legacymodel.exceptions.AgentUnavailableException;
import com.cloud.legacymodel.exceptions.NoTransitionException;
import com.cloud.legacymodel.exceptions.UnableDeleteHostException;
import com.cloud.legacymodel.resource.ResourceState;
import com.cloud.legacymodel.resource.ResourceState.Event;
import com.cloud.legacymodel.to.GPUDeviceTO;
import com.cloud.legacymodel.vm.VgpuTypesInfo;
import com.cloud.model.Zone;
import com.cloud.model.enumeration.HostType;
import com.cloud.model.enumeration.HypervisorType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ResourceManager manages how physical resources are organized within the
 * CloudStack. It also manages the life cycle of the physical resources.
 */
public interface ResourceManager extends ResourceService {
    /**
     * Register a listener for different types of resource life cycle events.
     * There can only be one type of listener per type of host.
     *
     * @param Event    type see ResourceListener.java, allow combination of multiple events.
     * @param listener the listener to notify.
     */
    public void registerResourceEvent(Integer event, ResourceListener listener);

    public void unregisterResourceEvent(ResourceListener listener);

    /**
     * @param name    of adapter
     * @param adapter
     * @param hates,  a list of names which will be eliminated by this adapter. Especially for the case where
     *                can be only one adapter responds to an event, e.g. startupCommand
     */
    public void registerResourceStateAdapter(String name, ResourceStateAdapter adapter);

    public void unregisterResourceStateAdapter(String name);

    public Host createHostAndAgent(Long hostId, ServerResource resource, Map<String, String> details, boolean old, List<String> hostTags, boolean forRebalance);

    public Host addHost(long zoneId, ServerResource resource, HostType hostType, Map<String, String> hostDetails);

    public HostVO createHostVOForConnectedAgent(StartupCommand[] cmds);

    public void checkCIDR(HostPodVO pod, Zone zone, String serverPrivateIP, String serverPrivateNetmask);

    public HostVO fillRoutingHostVO(HostVO host, StartupRoutingCommand ssCmd, HypervisorType hyType, Map<String, String> details, List<String> hostTags);

    public void deleteRoutingHost(HostVO host, boolean isForced, boolean forceDestroyStorage) throws UnableDeleteHostException;

    public boolean executeUserRequest(long hostId, ResourceState.Event event) throws AgentUnavailableException;

    boolean resourceStateTransitTo(Host host, Event event, long msId) throws NoTransitionException;

    boolean umanageHost(long hostId);

    boolean maintenanceFailed(long hostId);

    public boolean maintain(final long hostId) throws AgentUnavailableException;

    public boolean checkAndMaintain(final long hostId);

    @Override
    public boolean deleteHost(long hostId, boolean isForced, boolean isForceDeleteStorage);

    public List<HostVO> findDirectlyConnectedHosts();

    public List<HostVO> listAllUpAndEnabledHosts(HostType type, Long clusterId, Long podId, long dcId);

    public List<HostVO> listAllHostsInCluster(long clusterId);

    public List<HostVO> listHostsInClusterByStatus(long clusterId, HostStatus status);

    public List<HostVO> listAllUpAndEnabledHostsInOneZoneByType(HostType type, long dcId);

    public List<HostVO> listAllUpAndEnabledHostsInOneZoneByHypervisor(HypervisorType type, long dcId);

    public List<HostVO> listAllUpAndEnabledHostsInOneZone(long dcId);

    public List<HostVO> listAllHostsInOneZoneByType(HostType type, long dcId);

    public List<HostVO> listAllHostsInAllZonesByType(HostType type);

    public List<HypervisorType> listAvailHypervisorInZone(Long hostId, Long zoneId);

    public HostVO findHostByGuid(String guid);

    public HostVO findHostByName(String name);

    HostStats getHostStatistics(long hostId);

    Long getGuestOSCategoryId(long hostId);

    String getHostTags(long hostId);

    List<PodCluster> listByDataCenter(long dcId);

    List<HostVO> listAllNotInMaintenanceHostsInOneZone(HostType type, Long dcId);

    HypervisorType getDefaultHypervisor(long zoneId);

    HypervisorType getAvailableHypervisor(long zoneId);

    Discoverer getMatchingDiscover(HypervisorType hypervisorType);

    List<HostVO> findHostByGuid(long dcId, String guid);

    /**
     * @param type
     * @param clusterId
     * @param podId
     * @param dcId
     * @return
     */
    List<HostVO> listAllUpAndEnabledNonHAHosts(HostType type, Long clusterId, Long podId, long dcId);

    /**
     * Check if host is GPU enabled
     *
     * @param hostId the host to be checked
     * @return true if host contains GPU card else false
     */
    boolean isHostGpuEnabled(long hostId);

    /**
     * Check if host has GPU devices available
     *
     * @param hostId     the host to be checked
     * @param groupName: gpuCard name
     * @param vgpuType   the VGPU type
     * @return true when the host has the capacity with given VGPU type
     */
    boolean isGPUDeviceAvailable(long hostId, String groupName, String vgpuType);

    /**
     * Get available GPU device
     *
     * @param hostId     the host to be checked
     * @param groupName: gpuCard name
     * @param vgpuType   the VGPU type
     * @return GPUDeviceTO[]
     */
    GPUDeviceTO getGPUDevice(long hostId, String groupName, String vgpuType);

    /**
     * Return listof available GPU devices
     *
     * @param hostId,    the host to be checked
     * @param groupName: gpuCard name
     * @param vgpuType   the VGPU type
     * @return List of HostGpuGroupsVO.
     */
    List<HostGpuGroupsVO> listAvailableGPUDevice(long hostId, String groupName, String vgpuType);

    /**
     * Update GPU device details (post VM deployment)
     *
     * @param hostId,       the dest host Id
     * @param groupDetails, capacity of GPU group.
     */
    void updateGPUDetails(long hostId, HashMap<String, HashMap<String, VgpuTypesInfo>> groupDetails);

    /**
     * Get GPU details for a host
     *
     * @param host, the Host object
     * @return Details of groupNames and enabled VGPU type with remaining capacity.
     */
    HashMap<String, HashMap<String, VgpuTypesInfo>> getGPUStatistics(HostVO host);
}
