package com.cloud.resource;

import com.cloud.api.command.admin.cluster.AddClusterCmd;
import com.cloud.api.command.admin.cluster.DeleteClusterCmd;
import com.cloud.api.command.admin.host.AddHostCmd;
import com.cloud.api.command.admin.host.CancelMaintenanceCmd;
import com.cloud.api.command.admin.host.PrepareForMaintenanceCmd;
import com.cloud.api.command.admin.host.ReconnectHostCmd;
import com.cloud.api.command.admin.host.UpdateHostCmd;
import com.cloud.api.command.admin.host.UpdateHostPasswordCmd;
import com.cloud.common.request.ResourceListener;
import com.cloud.common.resource.ServerResource;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.PodCluster;
import com.cloud.gpu.HostGpuGroupsVO;
import com.cloud.host.HostVO;
import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.legacymodel.communication.command.StartupRoutingCommand;
import com.cloud.legacymodel.dc.Cluster;
import com.cloud.legacymodel.dc.Host;
import com.cloud.legacymodel.dc.HostStats;
import com.cloud.legacymodel.dc.HostStatus;
import com.cloud.legacymodel.exceptions.AgentUnavailableException;
import com.cloud.legacymodel.exceptions.DiscoveryException;
import com.cloud.legacymodel.exceptions.InvalidParameterValueException;
import com.cloud.legacymodel.exceptions.NoTransitionException;
import com.cloud.legacymodel.exceptions.ResourceInUseException;
import com.cloud.legacymodel.exceptions.UnableDeleteHostException;
import com.cloud.legacymodel.resource.ResourceState.Event;
import com.cloud.legacymodel.to.GPUDeviceTO;
import com.cloud.legacymodel.vm.VgpuTypesInfo;
import com.cloud.model.Zone;
import com.cloud.model.enumeration.HostType;
import com.cloud.model.enumeration.HypervisorType;
import com.cloud.utils.component.ManagerBase;

import javax.naming.ConfigurationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockResourceManagerImpl extends ManagerBase implements ResourceManager {

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#updateHost(com.cloud.api.commands.UpdateHostCmd)
     */
    @Override
    public Host updateHost(final UpdateHostCmd cmd) throws NoTransitionException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#cancelMaintenance(com.cloud.api.commands.CancelMaintenanceCmd)
     */
    @Override
    public Host cancelMaintenance(final CancelMaintenanceCmd cmd) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#reconnectHost(com.cloud.api.commands.ReconnectHostCmd)
     */
    @Override
    public Host reconnectHost(final ReconnectHostCmd cmd) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#discoverCluster(com.cloud.api.commands.AddClusterCmd)
     */
    @Override
    public List<? extends Cluster> discoverCluster(final AddClusterCmd cmd) throws IllegalArgumentException, DiscoveryException, ResourceInUseException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#deleteCluster(com.cloud.api.commands.DeleteClusterCmd)
     */
    @Override
    public boolean deleteCluster(final DeleteClusterCmd cmd) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#updateCluster(com.cloud.legacymodel.dc.Cluster, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public Cluster updateCluster(final Cluster cluster, final String clusterType, final String hypervisor, final String allocationState, final String managedstate) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#discoverHosts(com.cloud.api.commands.AddHostCmd)
     */
    @Override
    public List<? extends Host> discoverHosts(final AddHostCmd cmd) throws IllegalArgumentException, DiscoveryException, InvalidParameterValueException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#maintain(com.cloud.api.commands.PrepareForMaintenanceCmd)
     */
    @Override
    public Host maintain(final PrepareForMaintenanceCmd cmd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean updateClusterPassword(final UpdateHostPasswordCmd upasscmd) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#updateHostPassword(com.cloud.api.commands.UpdateHostPasswordCmd)
     */
    @Override
    public boolean updateHostPassword(final UpdateHostPasswordCmd upasscmd) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#getHost(long)
     */
    @Override
    public Host getHost(final long hostId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#getCluster(java.lang.Long)
     */
    @Override
    public Cluster getCluster(final Long clusterId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceService#getSupportedHypervisorTypes(long, boolean, java.lang.Long)
     */
    @Override
    public List<HypervisorType> getSupportedHypervisorTypes(final long zoneId, final boolean forVirtualRouter, final Long podId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean releaseHostReservation(final Long hostId) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#registerResourceEvent(java.lang.Integer, com.cloud.common.request.ResourceListener)
     */
    @Override
    public void registerResourceEvent(final Integer event, final ResourceListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#unregisterResourceEvent(com.cloud.common.request.ResourceListener)
     */
    @Override
    public void unregisterResourceEvent(final ResourceListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#registerResourceStateAdapter(java.lang.String, com.cloud.resource.ResourceStateAdapter)
     */
    @Override
    public void registerResourceStateAdapter(final String name, final ResourceStateAdapter adapter) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#unregisterResourceStateAdapter(java.lang.String)
     */
    @Override
    public void unregisterResourceStateAdapter(final String name) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#createHostAndAgent(java.lang.Long, com.cloud.common.resource.ServerResource, java.util.Map, boolean, java.util.List, boolean)
     */
    @Override
    public Host createHostAndAgent(final Long hostId, final ServerResource resource, final Map<String, String> details, final boolean old, final List<String> hostTags, final
    boolean forRebalance) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#addHost(long, com.cloud.common.resource.ServerResource, com.cloud.legacymodel.dc.Host.Type, java.util.Map)
     */
    @Override
    public Host addHost(final long zoneId, final ServerResource resource, final HostType hostType, final Map<String, String> hostDetails) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#createHostVOForConnectedAgent(com.cloud.legacymodel.communication.command.StartupCommand[])
     */
    @Override
    public HostVO createHostVOForConnectedAgent(final StartupCommand[] cmds) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#checkCIDR(com.cloud.dc.HostPodVO, com.cloud.dc.DataCenterVO, java.lang.String, java.lang.String)
     */
    @Override
    public void checkCIDR(final HostPodVO pod, final Zone dc, final String serverPrivateIP, final String serverPrivateNetmask) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#fillRoutingHostVO(com.cloud.host.HostVO, com.cloud.legacymodel.communication.command.StartupRoutingCommand, com.cloud.hypervisor.Hypervisor
     * .HypervisorType, java.util.Map, java.util.List)
     */
    @Override
    public HostVO fillRoutingHostVO(final HostVO host, final StartupRoutingCommand ssCmd, final HypervisorType hyType, final Map<String, String> details, final List<String>
            hostTags) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#deleteRoutingHost(com.cloud.host.HostVO, boolean, boolean)
     */
    @Override
    public void deleteRoutingHost(final HostVO host, final boolean isForced, final boolean forceDestroyStorage) throws UnableDeleteHostException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#executeUserRequest(long, com.cloud.legacymodel.resource.ResourceState.Event)
     */
    @Override
    public boolean executeUserRequest(final long hostId, final Event event) throws AgentUnavailableException {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#resourceStateTransitTo(com.cloud.legacymodel.dc.Host, com.cloud.legacymodel.resource.ResourceState.Event, long)
     */
    @Override
    public boolean resourceStateTransitTo(final Host host, final Event event, final long msId) throws NoTransitionException {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#umanageHost(long)
     */
    @Override
    public boolean umanageHost(final long hostId) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#maintenanceFailed(long)
     */
    @Override
    public boolean maintenanceFailed(final long hostId) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#maintain(long)
     */
    @Override
    public boolean maintain(final long hostId) throws AgentUnavailableException {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#maintain(long)
     */
    @Override
    public boolean checkAndMaintain(final long hostId) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#deleteHost(long, boolean, boolean)
     */
    @Override
    public boolean deleteHost(final long hostId, final boolean isForced, final boolean isForceDeleteStorage) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#findDirectlyConnectedHosts()
     */
    @Override
    public List<HostVO> findDirectlyConnectedHosts() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllUpAndEnabledHosts(com.cloud.legacymodel.dc.Host.Type, java.lang.Long, java.lang.Long, long)
     */
    @Override
    public List<HostVO> listAllUpAndEnabledHosts(final HostType type, final Long clusterId, final Long podId, final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllHostsInCluster(long)
     */
    @Override
    public List<HostVO> listAllHostsInCluster(final long clusterId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listHostsInClusterByStatus(long, com.cloud.legacymodel.dc.HostStatus)
     */
    @Override
    public List<HostVO> listHostsInClusterByStatus(final long clusterId, final HostStatus status) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllUpAndEnabledHostsInOneZoneByType(com.cloud.legacymodel.dc.Host.Type, long)
     */
    @Override
    public List<HostVO> listAllUpAndEnabledHostsInOneZoneByType(final HostType type, final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<HostVO> listAllUpAndEnabledHostsInOneZoneByHypervisor(final HypervisorType type, final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<HostVO> listAllUpAndEnabledHostsInOneZone(final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllHostsInOneZoneByType(com.cloud.legacymodel.dc.Host.Type, long)
     */
    @Override
    public List<HostVO> listAllHostsInOneZoneByType(final HostType type, final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllHostsInAllZonesByType(com.cloud.legacymodel.dc.Host.Type)
     */
    @Override
    public List<HostVO> listAllHostsInAllZonesByType(final HostType type) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAvailHypervisorInZone(java.lang.Long, java.lang.Long)
     */
    @Override
    public List<HypervisorType> listAvailHypervisorInZone(final Long hostId, final Long zoneId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#findHostByGuid(java.lang.String)
     */
    @Override
    public HostVO findHostByGuid(final String guid) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#findHostByName(java.lang.String)
     */
    @Override
    public HostVO findHostByName(final String name) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#getHostStatistics(long)
     */
    @Override
    public HostStats getHostStatistics(final long hostId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#getGuestOSCategoryId(long)
     */
    @Override
    public Long getGuestOSCategoryId(final long hostId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#getHostTags(long)
     */
    @Override
    public String getHostTags(final long hostId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listByDataCenter(long)
     */
    @Override
    public List<PodCluster> listByDataCenter(final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllNotInMaintenanceHostsInOneZone(com.cloud.legacymodel.dc.Host.Type, java.lang.Long)
     */
    @Override
    public List<HostVO> listAllNotInMaintenanceHostsInOneZone(final HostType type, final Long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#getDefaultHypervisor(long)
     */
    @Override
    public HypervisorType getDefaultHypervisor(final long zoneId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#getAvailableHypervisor(long)
     */
    @Override
    public HypervisorType getAvailableHypervisor(final long zoneId) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#getMatchingDiscover(com.cloud.hypervisor.Hypervisor.HypervisorType)
     */
    @Override
    public Discoverer getMatchingDiscover(final HypervisorType hypervisorType) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#findHostByGuid(long, java.lang.String)
     */
    @Override
    public List<HostVO> findHostByGuid(final long dcId, final String guid) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.resource.ResourceManager#listAllUpAndEnabledNonHAHosts(com.cloud.legacymodel.dc.Host.Type, java.lang.Long, java.lang.Long, long)
     */
    @Override
    public List<HostVO> listAllUpAndEnabledNonHAHosts(final HostType type, final Long clusterId, final Long podId, final long dcId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isHostGpuEnabled(final long hostId) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isGPUDeviceAvailable(final long hostId, final String groupName, final String vgpuType) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public GPUDeviceTO getGPUDevice(final long hostId, final String groupName, final String vgpuType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<HostGpuGroupsVO> listAvailableGPUDevice(final long hostId, final String groupName, final String vgpuType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateGPUDetails(final long hostId, final HashMap<String, HashMap<String, VgpuTypesInfo>> deviceDetails) {
        // TODO Auto-generated method stub
    }

    @Override
    public HashMap<String, HashMap<String, VgpuTypesInfo>> getGPUStatistics(final HostVO host) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.cloud.utils.component.Manager#getName()
     */
    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return "MockResourceManagerImpl";
    }

    /* (non-Javadoc)
     * @see com.cloud.utils.component.Manager#configure(java.lang.String, java.util.Map)
     */
    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        // TODO Auto-generated method stub
        return true;
    }

    /* (non-Javadoc)
     * @see com.cloud.utils.component.Manager#start()
     */
    @Override
    public boolean start() {
        // TODO Auto-generated method stub
        return true;
    }

    /* (non-Javadoc)
     * @see com.cloud.utils.component.Manager#stop()
     */
    @Override
    public boolean stop() {
        // TODO Auto-generated method stub
        return true;
    }
}
