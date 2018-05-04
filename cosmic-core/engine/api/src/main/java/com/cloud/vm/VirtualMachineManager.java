package com.cloud.vm;

import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeploymentPlanner;
import com.cloud.framework.config.ConfigKey;
import com.cloud.legacymodel.exceptions.AgentUnavailableException;
import com.cloud.legacymodel.exceptions.ConcurrentOperationException;
import com.cloud.legacymodel.exceptions.InsufficientCapacityException;
import com.cloud.legacymodel.exceptions.InsufficientServerCapacityException;
import com.cloud.legacymodel.exceptions.NoTransitionException;
import com.cloud.legacymodel.exceptions.OperationTimedoutException;
import com.cloud.legacymodel.exceptions.ResourceUnavailableException;
import com.cloud.legacymodel.exceptions.VirtualMachineMigrationException;
import com.cloud.legacymodel.network.Network;
import com.cloud.legacymodel.network.Nic;
import com.cloud.legacymodel.storage.StoragePool;
import com.cloud.legacymodel.storage.VirtualMachineTemplate;
import com.cloud.legacymodel.to.NicTO;
import com.cloud.legacymodel.to.VirtualMachineTO;
import com.cloud.legacymodel.vm.VirtualMachine;
import com.cloud.model.enumeration.DiskControllerType;
import com.cloud.model.enumeration.HypervisorType;
import com.cloud.model.enumeration.VirtualMachineType;
import com.cloud.offering.DiskOfferingInfo;
import com.cloud.offering.ServiceOffering;
import com.cloud.utils.component.Manager;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages allocating resources to vms.
 */
public interface VirtualMachineManager extends Manager {

    static final ConfigKey<Boolean> ExecuteInSequence = new ConfigKey<>("Advanced", Boolean.class, "execute.in.sequence.hypervisor.commands", "false",
            "If set to true, start, stop, reboot, copy and migrate commands will be serialized on the agent side. If set to false the commands are executed in parallel. Default " +
                    "value is false.", false);

    static final ConfigKey<String> VmConfigDriveLabel = new ConfigKey<>("Hidden", String.class, "vm.configdrive.label", "config",
            "The default label name for the config drive", false);

    /**
     * Allocates a new virtual machine instance in the CloudStack DB.  This
     * orchestrates the creation of all virtual resources needed in CloudStack
     * DB to bring up a VM.
     *
     * @param vmInstanceName    Instance name of the VM.  This name uniquely
     *                          a VM in CloudStack's deploy environment.  The caller gets to
     *                          define this VM but it must be unqiue for all of CloudStack.
     * @param template          The template this VM is based on.
     * @param serviceOffering   The service offering that specifies the offering this VM should provide.
     * @param defaultNetwork    The default network for the VM.
     * @param rootDiskOffering  For created VMs not based on templates, root disk offering specifies the root disk.
     * @param dataDiskOfferings Data disks to attach to the VM.
     * @param auxiliaryNetworks additional networks to attach the VMs to.
     * @param plan              How to deploy the VM.
     * @param hyperType         Hypervisor type
     * @throws InsufficientCapacityException If there are insufficient capacity to deploy this vm.
     */
    void allocate(String vmInstanceName, VirtualMachineTemplate template, ServiceOffering serviceOffering, DiskOfferingInfo rootDiskOfferingInfo,
                  List<DiskOfferingInfo> dataDiskOfferings, LinkedHashMap<? extends Network, List<? extends NicProfile>> auxiliaryNetworks, DeploymentPlan plan,
                  HypervisorType hyperType, DiskControllerType diskControllerType) throws InsufficientCapacityException;

    void allocate(String vmInstanceName, VirtualMachineTemplate template, ServiceOffering serviceOffering,
                  LinkedHashMap<? extends Network, List<? extends NicProfile>> networkProfiles, DeploymentPlan plan, HypervisorType hyperType) throws InsufficientCapacityException;

    void start(String vmUuid, Map<VirtualMachineProfile.Param, Object> params);

    void start(String vmUuid, Map<VirtualMachineProfile.Param, Object> params, DeploymentPlan planToDeploy, DeploymentPlanner planner);

    void stop(String vmUuid) throws ResourceUnavailableException;

    void expunge(String vmUuid) throws ResourceUnavailableException;

    void registerGuru(VirtualMachineType type, VirtualMachineGuru guru);

    boolean stateTransitTo(VirtualMachine vm, VirtualMachine.Event e, Long hostId) throws NoTransitionException;

    void advanceStart(String vmUuid, Map<VirtualMachineProfile.Param, Object> params, DeploymentPlanner planner) throws InsufficientCapacityException, ResourceUnavailableException,
            ConcurrentOperationException, OperationTimedoutException;

    void advanceStart(String vmUuid, Map<VirtualMachineProfile.Param, Object> params, DeploymentPlan planToDeploy, DeploymentPlanner planner) throws InsufficientCapacityException,
            ResourceUnavailableException, ConcurrentOperationException, OperationTimedoutException;

    void orchestrateStart(String vmUuid, Map<VirtualMachineProfile.Param, Object> params, DeploymentPlan planToDeploy, DeploymentPlanner planner) throws
            InsufficientCapacityException,
            ResourceUnavailableException, ConcurrentOperationException, OperationTimedoutException;

    void advanceStop(String vmUuid, boolean cleanupEvenIfUnableToStop) throws ResourceUnavailableException, OperationTimedoutException, ConcurrentOperationException;

    void advanceExpunge(String vmUuid) throws ResourceUnavailableException, OperationTimedoutException, ConcurrentOperationException;

    void destroy(String vmUuid) throws AgentUnavailableException, OperationTimedoutException, ConcurrentOperationException;

    void stopForced(String vmUuid) throws ResourceUnavailableException;

    void migrateAway(String vmUuid, long hostId) throws InsufficientServerCapacityException;

    void migrate(String vmUuid, long srcHostId, DeployDestination dest) throws ResourceUnavailableException, ConcurrentOperationException, VirtualMachineMigrationException;

    void migrateWithStorage(String vmUuid, long srcId, long destId, Map<Long, Long> volumeToPool) throws ResourceUnavailableException, ConcurrentOperationException;

    void reboot(String vmUuid, Map<VirtualMachineProfile.Param, Object> params) throws InsufficientCapacityException, ResourceUnavailableException;

    void advanceReboot(String vmUuid, Map<VirtualMachineProfile.Param, Object> params) throws InsufficientCapacityException, ResourceUnavailableException,
            ConcurrentOperationException, OperationTimedoutException;

    /**
     * Check to see if a virtual machine can be upgraded to the given service offering
     *
     * @param vm
     * @param offering
     * @return true if the host can handle the upgrade, false otherwise
     */
    boolean isVirtualMachineUpgradable(final VirtualMachine vm, final ServiceOffering offering);

    VirtualMachine findById(long vmId);

    void storageMigration(String vmUuid, StoragePool storagePoolId);

    /**
     * @param vmInstance
     * @param newServiceOffering
     */
    void checkIfCanUpgrade(VirtualMachine vmInstance, ServiceOffering newServiceOffering);

    /**
     * @param vmId
     * @param serviceOfferingId
     * @return
     */
    boolean upgradeVmDb(long vmId, long serviceOfferingId);

    /**
     * @param vm
     * @param network
     * @param requested TODO
     * @return
     * @throws ConcurrentOperationException
     * @throws ResourceUnavailableException
     * @throws InsufficientCapacityException
     */
    NicProfile addVmToNetwork(VirtualMachine vm, Network network, NicProfile requested) throws ConcurrentOperationException,
            ResourceUnavailableException, InsufficientCapacityException;

    /**
     * @param vm
     * @param nic
     * @return
     * @throws ResourceUnavailableException
     * @throws ConcurrentOperationException
     */
    boolean removeNicFromVm(VirtualMachine vm, Nic nic) throws ConcurrentOperationException, ResourceUnavailableException;

    /**
     * @param vm
     * @param network
     * @param broadcastUri TODO
     * @return
     * @throws ResourceUnavailableException
     * @throws ConcurrentOperationException
     */
    boolean removeVmFromNetwork(VirtualMachine vm, Network network, URI broadcastUri) throws ConcurrentOperationException, ResourceUnavailableException;

    /**
     * @param nic
     * @param hypervisorType
     * @return
     */
    NicTO toNicTO(NicProfile nic, HypervisorType hypervisorType);

    /**
     * @param profile
     * @param hvGuru
     * @return
     */
    VirtualMachineTO toVmTO(VirtualMachineProfile profile);

    VirtualMachine reConfigureVm(String vmUuid, ServiceOffering newServiceOffering, boolean sameHost) throws ResourceUnavailableException, ConcurrentOperationException,
            InsufficientServerCapacityException;

    void findHostAndMigrate(String vmUuid, Long newSvcOfferingId, DeploymentPlanner.ExcludeList excludeHostList) throws InsufficientCapacityException,
            ConcurrentOperationException, ResourceUnavailableException;

    void migrateForScale(String vmUuid, long srcHostId, DeployDestination dest, Long newSvcOfferingId) throws ResourceUnavailableException, ConcurrentOperationException;

    boolean getExecuteInSequence(HypervisorType hypervisorType);

    public interface Topics {
        public static final String VM_POWER_STATE = "vm.powerstate";
    }
}
