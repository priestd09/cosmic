package com.cloud.hypervisor.xenserver.resource;

import com.cloud.common.agent.IAgentControl;
import com.cloud.common.resource.ServerResource;
import com.cloud.common.storageprocessor.resource.StorageSubsystemCommandHandler;
import com.cloud.common.storageprocessor.resource.StorageSubsystemCommandHandlerBase;
import com.cloud.common.virtualnetwork.VirtualRoutingResource;
import com.cloud.hypervisor.HypervisorResource;
import com.cloud.hypervisor.xenserver.resource.wrapper.xenbase.CitrixRequestWrapper;
import com.cloud.hypervisor.xenserver.resource.wrapper.xenbase.XenServerUtilitiesHelper;
import com.cloud.legacymodel.ExecutionResult;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.RebootAnswer;
import com.cloud.legacymodel.communication.answer.StartAnswer;
import com.cloud.legacymodel.communication.answer.StopAnswer;
import com.cloud.legacymodel.communication.command.Command;
import com.cloud.legacymodel.communication.command.GetHostStatsCommand;
import com.cloud.legacymodel.communication.command.GetVmStatsCommand;
import com.cloud.legacymodel.communication.command.NetworkElementCommand;
import com.cloud.legacymodel.communication.command.PingCommand;
import com.cloud.legacymodel.communication.command.PingRoutingCommand;
import com.cloud.legacymodel.communication.command.PingRoutingWithNwGroupsCommand;
import com.cloud.legacymodel.communication.command.RebootCommand;
import com.cloud.legacymodel.communication.command.SetNetworkACLCommand;
import com.cloud.legacymodel.communication.command.StartCommand;
import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.legacymodel.communication.command.StartupRoutingCommand;
import com.cloud.legacymodel.communication.command.StartupStorageCommand;
import com.cloud.legacymodel.communication.command.StopCommand;
import com.cloud.legacymodel.dc.HostStatsEntry;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;
import com.cloud.legacymodel.network.VirtualRouterDeployer;
import com.cloud.legacymodel.storage.StoragePoolInfo;
import com.cloud.legacymodel.to.DataStoreTO;
import com.cloud.legacymodel.to.DataTO;
import com.cloud.legacymodel.to.DiskTO;
import com.cloud.legacymodel.to.GPUDeviceTO;
import com.cloud.legacymodel.to.NfsTO;
import com.cloud.legacymodel.to.NicTO;
import com.cloud.legacymodel.to.TemplateObjectTO;
import com.cloud.legacymodel.to.VirtualMachineTO;
import com.cloud.legacymodel.to.VolumeObjectTO;
import com.cloud.legacymodel.utils.Pair;
import com.cloud.legacymodel.vm.BootloaderType;
import com.cloud.legacymodel.vm.HostVmStateReportEntry;
import com.cloud.legacymodel.vm.VgpuTypesInfo;
import com.cloud.legacymodel.vm.VirtualMachine.PowerState;
import com.cloud.legacymodel.vm.VmStatsEntry;
import com.cloud.model.enumeration.BroadcastDomainType;
import com.cloud.model.enumeration.HostType;
import com.cloud.model.enumeration.HypervisorType;
import com.cloud.model.enumeration.StoragePoolType;
import com.cloud.model.enumeration.StorageResourceType;
import com.cloud.model.enumeration.TrafficType;
import com.cloud.model.enumeration.VolumeType;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.PropertiesUtil;
import com.cloud.utils.StringUtils;
import com.cloud.utils.net.NetUtils;
import com.cloud.utils.script.Script;
import com.cloud.utils.ssh.SSHCmdHelper;
import com.cloud.utils.ssh.SshHelper;

import javax.ejb.Local;
import javax.naming.ConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.trilead.ssh2.SCPClient;
import com.xensource.xenapi.Bond;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Console;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.HostCpu;
import com.xensource.xenapi.HostMetrics;
import com.xensource.xenapi.Network;
import com.xensource.xenapi.PBD;
import com.xensource.xenapi.PIF;
import com.xensource.xenapi.Pool;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Session;
import com.xensource.xenapi.Task;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.Types.XenAPIException;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VIF;
import com.xensource.xenapi.VLAN;
import com.xensource.xenapi.VM;
import com.xensource.xenapi.XenAPIObject;
import org.apache.commons.io.FileUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * CitrixResourceBase encapsulates the calls to the XenServer Xapi process to perform the required functionalities for
 * CloudStack.
 * <p>
 * ==============> READ THIS <============== Because the XenServer objects can expire when the session expires, we
 * cannot keep any of the actual XenServer objects in this class. The only thing that is constant is the UUID of the
 * XenServer objects but not the objects themselves! This is very important before you do any changes in this code here.
 */
@Local(value = ServerResource.class)
public abstract class CitrixResourceBase implements ServerResource, HypervisorResource, VirtualRouterDeployer {

    protected static final XenServerConnectionPool ConnPool = XenServerConnectionPool.getInstance();
    protected static final HashMap<VmPowerState, PowerState> s_powerStatesTable;
    static final Random Rand = new Random(System.currentTimeMillis());
    // static min values for guests on xenserver
    private static final long mem_128m = 134217728L;
    private static final Logger s_logger = LoggerFactory.getLogger(CitrixResourceBase.class);

    static {
        s_powerStatesTable = new HashMap<>();
        s_powerStatesTable.put(VmPowerState.HALTED, PowerState.PowerOff);
        s_powerStatesTable.put(VmPowerState.PAUSED, PowerState.PowerOff);
        s_powerStatesTable.put(VmPowerState.RUNNING, PowerState.PowerOn);
        s_powerStatesTable.put(VmPowerState.SUSPENDED, PowerState.PowerOff);
        s_powerStatesTable.put(VmPowerState.UNRECOGNIZED, PowerState.PowerUnknown);
    }

    protected final XsHost _host = new XsHost();
    protected final int _retry = 100;
    protected final int _sleep = 10000;
    final int _maxWeight = 256;
    public String _attachIsoDeviceNum = "3";
    protected IAgentControl _agentControl;
    protected boolean _canBridgeFirewall = false;
    protected String _cluster;
    // Guest and Host Performance Statistics
    protected String _consolidationFunction = "AVERAGE";
    protected long _dcId;
    protected String _guestNetworkName;
    protected int _heartbeatInterval = 60;
    protected int _heartbeatTimeout = 120;
    protected String _instance; // instance name (default is usually "VM")
    protected String _linkLocalPrivateNetworkName;
    protected int _maxNics = 7;
    protected int _migratewait;
    protected String _name;
    protected Queue<String> _password = new LinkedList<>();

    protected String _pod;
    protected int _pollingIntervalInSeconds = 60;

    protected String _privateNetworkName;
    protected String _publicNetworkName;
    protected String _storageNetworkName1;
    protected String _storageNetworkName2;
    protected List<VIF> _tmpDom0Vif = new ArrayList<>();
    protected String _username;
    protected VirtualRoutingResource _vrResource;
    protected String _configDriveIsopath = "/opt/xensource/packages/configdrive_iso/";
    protected String _configDriveSRName = "ConfigDriveISOs";
    protected XenServerUtilitiesHelper xenServerUtilitiesHelper = new XenServerUtilitiesHelper();
    protected int _wait;
    protected StorageSubsystemCommandHandler storageHandler;
    // Hypervisor specific params with generic value, may need to be overridden
    // for specific versions
    long _xsMemoryUsed = 128 * 1024 * 1024L; // xenserver hypervisor used 128 M
    double _xsVirtualizationFactor = 63.0 / 64.0; // 1 - virtualization overhead

    public CitrixResourceBase() {
    }

    /**
     * Replaces the old password with the new password used to connect to the host.
     *
     * @param password - the new host password.
     * @return the old password.
     */
    public String replaceOldPasswdInQueue(final String password) {
        final String oldPasswd = this._password.poll();
        this._password.add(password);

        return oldPasswd;
    }

    public String getPwdFromQueue() {
        return this._password.peek();
    }

    public XenServerUtilitiesHelper getXenServerUtilitiesHelper() {
        return this.xenServerUtilitiesHelper;
    }

    public boolean canBridgeFirewall() {
        return this._canBridgeFirewall;
    }

    public boolean canBridgeFirewall(final Connection conn) {
        return Boolean.valueOf(
                callHostPlugin(conn, "vmops", "can_bridge_firewall", "host_uuid", this._host.getUuid(), "instance", this._instance));
    }

    public String callHostPlugin(final Connection conn, final String plugin, final String cmd, final String... params) {
        final Map<String, String> args = new HashMap<>();
        final String msg;
        try {
            for (int i = 0; i < params.length; i += 2) {
                args.put(params[i], params[i + 1]);
            }

            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin executing for command " + cmd + " with " + getArgsString(args));
            }
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            final String result = host.callPlugin(conn, plugin, cmd, args);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin Result: " + result);
            }
            return result.replace("\n", "");
        } catch (final XenAPIException e) {
            msg = "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.toString();
            s_logger.warn(msg);
        } catch (final XmlRpcException e) {
            msg = "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.getMessage();
            s_logger.debug(msg);
        }
        throw new CloudRuntimeException(msg);
    }

    protected String getArgsString(final Map<String, String> args) {
        final StringBuilder argString = new StringBuilder();
        for (final Map.Entry<String, String> arg : args.entrySet()) {
            argString.append(arg.getKey() + ": " + arg.getValue() + ", ");
        }
        return argString.toString();
    }

    protected StorageSubsystemCommandHandler buildStorageHandler() {
        final XenServerStorageProcessor processor = new XenServerStorageProcessor(this);
        return new StorageSubsystemCommandHandlerBase(processor);
    }

    public boolean cleanupHaltedVms(final Connection conn) throws XenAPIException, XmlRpcException {
        final Host host = Host.getByUuid(conn, this._host.getUuid());
        final Map<VM, VM.Record> vms = VM.getAllRecords(conn);
        boolean success = true;
        if (vms != null && !vms.isEmpty()) {
            for (final Map.Entry<VM, VM.Record> entry : vms.entrySet()) {
                final VM vm = entry.getKey();
                final VM.Record vmRec = entry.getValue();
                if (vmRec.isATemplate || vmRec.isControlDomain) {
                    continue;
                }

                if (VmPowerState.HALTED.equals(vmRec.powerState) && vmRec.affinity.equals(host) && !isAlienVm(vm, conn)) {
                    try {
                        vm.destroy(conn);
                    } catch (final Exception e) {
                        s_logger.warn(
                                "Catch Exception " + e.getClass().getName() + ": unable to destroy VM " + vmRec.nameLabel + " due to ",
                                e);
                        success = false;
                    }
                }
            }
        }
        return success;
    }

    private static boolean isAlienVm(final VM vm, final Connection conn) throws XenAPIException, XmlRpcException {
        // TODO : we need a better way to tell whether or not the VM belongs to
        // CloudStack
        final String vmName = vm.getNameLabel(conn);
        if (vmName.matches("^[ivs]-\\d+-.+")) {
            return false;
        }

        return true;
    }

    public void cleanupTemplateSR(final Connection conn) {
        Set<PBD> pbds = null;
        try {
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            pbds = host.getPBDs(conn);
        } catch (final XenAPIException e) {
            s_logger.warn("Unable to get the SRs " + e.toString(), e);
            throw new CloudRuntimeException("Unable to get SRs " + e.toString(), e);
        } catch (final Exception e) {
            throw new CloudRuntimeException("Unable to get SRs " + e.getMessage(), e);
        }
        for (final PBD pbd : pbds) {
            SR sr = null;
            SR.Record srRec = null;
            try {
                sr = pbd.getSR(conn);
                srRec = sr.getRecord(conn);
            } catch (final Exception e) {
                s_logger.warn("pbd.getSR get Exception due to ", e);
                continue;
            }
            final String type = srRec.type;
            if (srRec.shared) {
                continue;
            }
            if (SRType.NFS.equals(type) || SRType.ISO.equals(type) && srRec.nameDescription.contains("template")) {
                try {
                    pbd.unplug(conn);
                    pbd.destroy(conn);
                    sr.forget(conn);
                } catch (final Exception e) {
                    s_logger.warn("forget SR catch Exception due to ", e);
                }
            }
        }
    }

    protected Pair<VM, VM.Record> getControlDomain(final Connection conn) throws XenAPIException, XmlRpcException {
        final Host host = Host.getByUuid(conn, this._host.getUuid());
        Set<VM> vms = null;
        vms = host.getResidentVMs(conn);
        for (final VM vm : vms) {
            if (vm.getIsControlDomain(conn)) {
                return new Pair<>(vm, vm.getRecord(conn));
            }
        }

        throw new CloudRuntimeException("Com'on no control domain?  What the crap?!#@!##$@");
    }

    protected VDI cloudVDIcopy(final Connection conn, final VDI vdi, final SR sr, int wait) throws Exception {
        Task task = null;
        if (wait == 0) {
            wait = 2 * 60 * 60;
        }
        try {
            task = vdi.copyAsync(conn, sr);
            // poll every 1 seconds , timeout after 2 hours
            waitForTask(conn, task, 1000, (long) wait * 1000);
            checkForSuccess(conn, task);
            final VDI dvdi = Types.toVDI(task, conn);
            return dvdi;
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e.toString());
                }
            }
        }
    }

    public void waitForTask(final Connection c, final Task task, final long pollInterval, final long timeout)
            throws XenAPIException, XmlRpcException, TimeoutException {
        final long beginTime = System.currentTimeMillis();
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Task " + task.getNameLabel(c) + " (" + task.getUuid(c) + ") sent to " + c.getSessionReference()
                    + " is pending completion with a " + timeout
                    + "ms timeout");
        }
        while (task.getStatus(c) == Types.TaskStatusType.PENDING) {
            try {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Task " + task.getNameLabel(c) + " (" + task.getUuid(c) + ") is pending, sleeping for "
                            + pollInterval + "ms");
                }
                Thread.sleep(pollInterval);
            } catch (final InterruptedException e) {
            }
            if (System.currentTimeMillis() - beginTime > timeout) {
                final String msg = "Async " + timeout / 1000 + " seconds timeout for task " + task.toString();
                s_logger.warn(msg);
                task.cancel(c);
                task.destroy(c);
                throw new TimeoutException(msg);
            }
        }
    }

    public void checkForSuccess(final Connection c, final Task task) throws XenAPIException, XmlRpcException {
        if (task.getStatus(c) == Types.TaskStatusType.SUCCESS) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Task " + task.getNameLabel(c) + " (" + task.getUuid(c) + ") completed");
            }
            return;
        } else {
            final String msg = "Task failed! Task record: " + task.getRecord(c);
            s_logger.warn(msg);
            task.cancel(c);
            task.destroy(c);
            throw new Types.BadAsyncResult(msg);
        }
    }

    public HashMap<String, String> clusterVMMetaDataSync(final Connection conn) {
        final HashMap<String, String> vmMetaDatum = new HashMap<>();
        try {
            final Map<VM, VM.Record> vm_map = VM.getAllRecords(conn);
            if (vm_map != null) {
                for (final VM.Record record : vm_map.values()) {
                    if (record.isControlDomain || record.isASnapshot || record.isATemplate) {
                        continue; // Skip DOM0
                    }
                    final String platform = StringUtils.mapToString(record.platform);
                    if (platform.isEmpty()) {
                        continue; // Skip if platform is null
                    }
                    vmMetaDatum.put(record.nameLabel, StringUtils.mapToString(record.platform));
                }
            }
        } catch (final XmlRpcException | XenAPIException e) {
            final String msg = "Unable to get vms through host " + this._host.getUuid() + " due to to " + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg);
        }
        return vmMetaDatum;
    }

    private void CheckXenHostInfo() throws ConfigurationException {
        final Connection conn = ConnPool.getConnect(this._host.getIp(), this._username, this._password);
        if (conn == null) {
            throw new ConfigurationException("Can not create connection to " + this._host.getIp());
        }
        try {
            Host.Record hostRec = null;
            try {
                final Host host = Host.getByUuid(conn, this._host.getUuid());
                hostRec = host.getRecord(conn);
                final Pool.Record poolRec = Pool.getAllRecords(conn).values().iterator().next();
                this._host.setPool(poolRec.uuid);
            } catch (final Exception e) {
                throw new ConfigurationException("Can not get host information from " + this._host.getIp());
            }
            if (!hostRec.address.equals(this._host.getIp())) {
                final String msg = "Host " + this._host.getIp() + " seems be reinstalled, please remove this host and readd";
                s_logger.error(msg);
                throw new ConfigurationException(msg);
            }
        } finally {
            try {
                Session.logout(conn);
            } catch (final Exception e) {
            }
        }
    }

    public String connect(final Connection conn, final String vmname, final String ipAddress) {
        return connect(conn, vmname, ipAddress, 3922);
    }

    public String connect(final Connection conn, final String vmName, final String ipAddress, final int port) {
        for (int i = 0; i <= this._retry; i++) {
            try {
                final Set<VM> vms = VM.getByNameLabel(conn, vmName);
                if (vms.size() < 1) {
                    final String msg = "VM " + vmName + " is not running";
                    s_logger.warn(msg);
                    return msg;
                }
            } catch (final Exception e) {
                final String msg = "VM.getByNameLabel " + vmName + " failed due to " + e.toString();
                s_logger.warn(msg, e);
                return msg;
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Trying to connect to " + ipAddress + " attempt " + i + " of " + this._retry);
            }
            if (pingdomr(conn, ipAddress, Integer.toString(port))) {
                return null;
            }
            try {
                Thread.sleep(this._sleep);
            } catch (final InterruptedException e) {
            }
        }
        final String msg = "Timeout, Unable to logon to " + ipAddress;
        s_logger.debug(msg);

        return msg;
    }

    protected boolean pingdomr(final Connection conn, final String host, final String port) {
        final String status;
        status = callHostPlugin(conn, "vmops", "pingdomr", "host", host, "port", port);

        if (status == null || status.isEmpty()) {
            return false;
        }

        return true;
    }

    public String copyVhdFromSecondaryStorage(final Connection conn, final String mountpoint, final String sruuid,
                                              final int wait) {
        final String nameLabel = "cloud-" + UUID.randomUUID().toString();
        final String results = callHostPluginAsync(conn, "vmopspremium", "copy_vhd_from_secondarystorage", wait,
                "mountpoint", mountpoint, "sruuid", sruuid, "namelabel",
                nameLabel);
        String errMsg = null;
        if (results == null || results.isEmpty()) {
            errMsg = "copy_vhd_from_secondarystorage return null";
        } else {
            final String[] tmp = results.split("#");
            final String status = tmp[0];
            if (status.equals("0")) {
                return tmp[1];
            } else {
                errMsg = tmp[1];
            }
        }
        final String source = mountpoint.substring(mountpoint.lastIndexOf('/') + 1);
        if (killCopyProcess(conn, source)) {
            destroyVDIbyNameLabel(conn, nameLabel);
        }
        s_logger.warn(errMsg);
        throw new CloudRuntimeException(errMsg);
    }

    protected SR createNfsSRbyURI(final Connection conn, final URI uri, final boolean shared) {
        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Creating a " + (shared ? "shared SR for " : "not shared SR for ") + uri);
            }

            final Map<String, String> deviceConfig = new HashMap<>();
            String path = uri.getPath();
            path = path.replace("//", "/");
            deviceConfig.put("server", uri.getHost());
            deviceConfig.put("serverpath", path);
            final String name = UUID.nameUUIDFromBytes(new String(uri.getHost() + path).getBytes()).toString();
            if (!shared) {
                final Set<SR> srs = SR.getByNameLabel(conn, name);
                for (final SR sr : srs) {
                    final SR.Record record = sr.getRecord(conn);
                    if (SRType.NFS.equals(record.type) && record.contentType.equals("user") && !record.shared) {
                        removeSRSync(conn, sr);
                    }
                }
            }

            final Host host = Host.getByUuid(conn, this._host.getUuid());
            final Map<String, String> smConfig = new HashMap<>();
            smConfig.put("nosubdir", "true");
            final SR sr = SR.create(conn, host, deviceConfig, new Long(0), name, uri.getHost() + uri.getPath(),
                    SRType.NFS.toString(), "user", shared, smConfig);

            if (!checkSR(conn, sr)) {
                throw new Exception("no attached PBD");
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug(logX(sr, "Created a SR; UUID is " + sr.getUuid(conn) + " device config is " + deviceConfig));
            }
            sr.scan(conn);
            return sr;
        } catch (final XenAPIException e) {
            final String msg = "Can not create second storage SR mountpoint: " + uri.getHost() + uri.getPath() + " due to "
                    + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        } catch (final Exception e) {
            final String msg = "Can not create second storage SR mountpoint: " + uri.getHost() + uri.getPath() + " due to "
                    + e.getMessage();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        }
    }

    protected String removeSRSync(final Connection conn, final SR sr) {
        if (sr == null) {
            return null;
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(logX(sr, "Removing SR"));
        }
        long waittime = 0;
        try {
            final Set<VDI> vdis = sr.getVDIs(conn);
            for (final VDI vdi : vdis) {
                final Map<java.lang.String, Types.VdiOperations> currentOperation = vdi.getCurrentOperations(conn);
                if (currentOperation == null || currentOperation.size() == 0) {
                    continue;
                }
                if (waittime >= 1800000) {
                    final String msg = "This template is being used, try late time";
                    s_logger.warn(msg);
                    return msg;
                }
                waittime += 30000;
                try {
                    Thread.sleep(30000);
                } catch (final InterruptedException ex) {
                }
            }
            removeSR(conn, sr);
            return null;
        } catch (final XenAPIException e) {
            s_logger.warn(logX(sr, "Unable to get current opertions " + e.toString()), e);
        } catch (final XmlRpcException e) {
            s_logger.warn(logX(sr, "Unable to get current opertions " + e.getMessage()), e);
        }
        final String msg = "Remove SR failed";
        s_logger.warn(msg);
        return msg;
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        this._name = name;

        try {
            this._dcId = Long.parseLong((String) params.get("zone"));
        } catch (final NumberFormatException e) {
            throw new ConfigurationException("Unable to get the zone " + params.get("zone"));
        }

        this._host.setUuid((String) params.get("guid"));

        this._name = this._host.getUuid();
        this._host.setIp((String) params.get("ipaddress"));

        this._username = (String) params.get("username");
        this._password.add((String) params.get("password"));
        this._pod = (String) params.get("pod");
        this._cluster = (String) params.get("cluster");
        this._privateNetworkName = (String) params.get("private.network.device");
        this._publicNetworkName = (String) params.get("public.network.device");
        this._guestNetworkName = (String) params.get("guest.network.device");
        this._instance = (String) params.get("instance.name");

        this._linkLocalPrivateNetworkName = (String) params.get("private.linkLocal.device");
        if (this._linkLocalPrivateNetworkName == null) {
            this._linkLocalPrivateNetworkName = "cloud_link_local_network";
        }

        this._storageNetworkName1 = (String) params.get("storage.network.device1");
        this._storageNetworkName2 = (String) params.get("storage.network.device2");

        this._heartbeatTimeout = NumbersUtil.parseInt((String) params.get("xenserver.heartbeat.timeout"), 120);
        this._heartbeatInterval = NumbersUtil.parseInt((String) params.get("xenserver.heartbeat.interval"), 60);

        String value = (String) params.get("wait");
        this._wait = NumbersUtil.parseInt(value, 600);

        value = (String) params.get("migratewait");
        this._migratewait = NumbersUtil.parseInt(value, 3600);

        this._maxNics = NumbersUtil.parseInt((String) params.get("xenserver.nics.max"), 7);

        if (this._pod == null) {
            throw new ConfigurationException("Unable to get the pod");
        }

        if (this._host.getIp() == null) {
            throw new ConfigurationException("Unable to get the host address");
        }

        if (this._username == null) {
            throw new ConfigurationException("Unable to get the username");
        }

        if (this._password.peek() == null) {
            throw new ConfigurationException("Unable to get the password");
        }

        if (this._host.getUuid() == null) {
            throw new ConfigurationException("Unable to get the uuid");
        }

        CheckXenHostInfo();

        this.storageHandler = buildStorageHandler();

        this._vrResource = new VirtualRoutingResource(this);
        if (!this._vrResource.configure(params)) {
            throw new ConfigurationException("Unable to configure VirtualRoutingResource");
        }
        return true;
    }

    protected boolean checkSR(final Connection conn, final SR sr) {
        try {
            final SR.Record srr = sr.getRecord(conn);
            final Set<PBD> pbds = sr.getPBDs(conn);
            if (pbds.size() == 0) {
                final String msg = "There is no PBDs for this SR: " + srr.nameLabel + " on host:" + this._host.getUuid();
                s_logger.warn(msg);
                return false;
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Checking " + srr.nameLabel + " or SR " + srr.uuid + " on " + this._host);
            }
            if (srr.shared) {
                if (SRType.NFS.equals(srr.type)) {
                    final Map<String, String> smConfig = srr.smConfig;
                    if (!smConfig.containsKey("nosubdir")) {
                        smConfig.put("nosubdir", "true");
                        sr.setSmConfig(conn, smConfig);
                    }
                }

                final Host host = Host.getByUuid(conn, this._host.getUuid());
                boolean found = false;
                for (final PBD pbd : pbds) {
                    final PBD.Record pbdr = pbd.getRecord(conn);
                    if (host.equals(pbdr.host)) {
                        if (!pbdr.currentlyAttached) {
                            pbdPlug(conn, pbd, pbdr.uuid);
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    final PBD.Record pbdr = srr.PBDs.iterator().next().getRecord(conn);
                    pbdr.host = host;
                    pbdr.uuid = "";
                    final PBD pbd = PBD.create(conn, pbdr);
                    pbdPlug(conn, pbd, pbd.getUuid(conn));
                }
            } else {
                for (final PBD pbd : pbds) {
                    final PBD.Record pbdr = pbd.getRecord(conn);
                    if (!pbdr.currentlyAttached) {
                        pbdPlug(conn, pbd, pbdr.uuid);
                    }
                }
            }
        } catch (final Exception e) {
            final String msg = "checkSR failed host:" + this._host + " due to " + e.toString();
            s_logger.warn(msg, e);
            return false;
        }
        return true;
    }

    protected String logX(final XenAPIObject obj, final String msg) {
        return new StringBuilder("Host ").append(this._host.getIp()).append(" ").append(obj.toWireString()).append(": ").append(
                msg).toString();
    }

    public void removeSR(final Connection conn, final SR sr) {
        if (sr == null) {
            return;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug(logX(sr, "Removing SR"));
        }

        for (int i = 0; i < 2; i++) {
            try {
                final Set<VDI> vdis = sr.getVDIs(conn);
                for (final VDI vdi : vdis) {
                    vdi.forget(conn);
                }

                Set<PBD> pbds = sr.getPBDs(conn);
                for (final PBD pbd : pbds) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug(logX(pbd, "Unplugging pbd"));
                    }

                    // if (pbd.getCurrentlyAttached(conn)) {
                    pbd.unplug(conn);
                    // }

                    pbd.destroy(conn);
                }

                pbds = sr.getPBDs(conn);

                if (pbds.size() == 0) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug(logX(sr, "Forgetting"));
                    }

                    sr.forget(conn);

                    return;
                }

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(logX(sr, "There is still one or more PBDs attached."));

                    if (s_logger.isTraceEnabled()) {
                        for (final PBD pbd : pbds) {
                            s_logger.trace(logX(pbd, " Still attached"));
                        }
                    }
                }
            } catch (final XenAPIException e) {
                s_logger.debug(logX(sr, "Catch XenAPIException: " + e.toString()));
            } catch (final XmlRpcException e) {
                s_logger.debug(logX(sr, "Catch Exception: " + e.getMessage()));
            }
        }

        s_logger.warn(logX(sr, "Unable to remove SR"));
    }

    private void pbdPlug(final Connection conn, final PBD pbd, final String uuid) {
        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Plugging in PBD " + uuid + " for " + this._host);
            }
            pbd.plug(conn);
        } catch (final Exception e) {
            final String msg = "PBD " + uuid + " is not attached! and PBD plug failed due to " + e.toString()
                    + ". Please check this PBD in " + this._host;
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg);
        }
    }

    public VBD createPatchVbd(final Connection conn, final String vmName, final VM vm)
            throws XmlRpcException, XenAPIException {

        if (this._host.getSystemvmisouuid() == null) {
            final Set<SR> srs = SR.getByNameLabel(conn, "XenServer Tools");
            if (srs.size() != 1) {
                throw new CloudRuntimeException("There are " + srs.size() + " SRs with name XenServer Tools");
            }
            final SR sr = srs.iterator().next();
            sr.scan(conn);

            final SR.Record srr = sr.getRecord(conn);

            if (this._host.getSystemvmisouuid() == null) {
                for (final VDI vdi : srr.VDIs) {
                    final VDI.Record vdir = vdi.getRecord(conn);
                    if (vdir.nameLabel.contains("systemvm.iso")) {
                        this._host.setSystemvmisouuid(vdir.uuid);
                        break;
                    }
                }
            }
            if (this._host.getSystemvmisouuid() == null) {
                throw new CloudRuntimeException("can not find systemvmiso");
            }
        }

        final VBD.Record cdromVBDR = new VBD.Record();
        cdromVBDR.VM = vm;
        cdromVBDR.empty = true;
        cdromVBDR.bootable = false;
        cdromVBDR.userdevice = "3";
        cdromVBDR.mode = Types.VbdMode.RO;
        cdromVBDR.type = Types.VbdType.CD;
        final VBD cdromVBD = VBD.create(conn, cdromVBDR);
        cdromVBD.insert(conn, VDI.getByUuid(conn, this._host.getSystemvmisouuid()));

        return cdromVBD;
    }

    protected boolean createSecondaryStorageFolder(final Connection conn, final String remoteMountPath,
                                                   final String newFolder) {
        final String result = callHostPlugin(conn, "vmopsSnapshot", "create_secondary_storage_folder", "remoteMountPath",
                remoteMountPath, "newFolder", newFolder);
        return result != null;
    }

    protected String callHostPluginAsync(final Connection conn, final String plugin, final String cmd, final int wait,
                                         final String... params) {
        final int timeout = wait * 1000;
        final Map<String, String> args = new HashMap<>();
        Task task = null;
        try {
            for (int i = 0; i < params.length; i += 2) {
                args.put(params[i], params[i + 1]);
            }
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin executing for command " + cmd + " with " + getArgsString(args));
            }
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            task = host.callPluginAsync(conn, plugin, cmd, args);
            // poll every 1 seconds
            waitForTask(conn, task, 1000, timeout);
            checkForSuccess(conn, task);
            final String result = task.getResult(conn);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin Result: " + result);
            }
            return result.replace("<value>", "").replace("</value>", "").replace("\n", "");
        } catch (final Types.HandleInvalid e) {
            s_logger.warn("callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args)
                    + " due to HandleInvalid clazz:" + e.clazz + ", handle:" + e.handle);
        } catch (final XenAPIException e) {
            s_logger.warn(
                    "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.toString(), e);
        } catch (final Exception e) {
            s_logger.warn(
                    "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.getMessage(),
                    e);
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e1) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e1.toString());
                }
            }
        }
        return null;
    }

    boolean killCopyProcess(final Connection conn, final String nameLabel) {
        final String results = callHostPluginAsync(conn, "vmops", "kill_copy_process", 60, "namelabel", nameLabel);
        String errMsg = null;
        if (results == null || results.equals("false")) {
            errMsg = "kill_copy_process failed";
            s_logger.warn(errMsg);
            return false;
        } else {
            return true;
        }
    }

    public VBD createVbd(final Connection conn, final DiskTO volume, final String vmName, final VM vm,
                         final BootloaderType bootLoaderType, VDI vdi) throws XmlRpcException,
            XenAPIException {
        final VolumeType type = volume.getType();

        if (vdi == null) {
            vdi = mount(conn, vmName, volume);
        }

        if (vdi != null) {
            if ("detached".equals(vdi.getNameLabel(conn))) {
                vdi.setNameLabel(conn, vmName + "-DATA");
            }

            final Map<String, String> smConfig = vdi.getSmConfig(conn);
            for (final String key : smConfig.keySet()) {
                if (key.startsWith("host_")) {
                    vdi.removeFromSmConfig(conn, key);
                    break;
                }
            }
        }
        final VBD.Record vbdr = new VBD.Record();
        vbdr.VM = vm;
        if (vdi != null) {
            vbdr.VDI = vdi;
        } else {
            vbdr.empty = true;
        }
        if (type == VolumeType.ROOT && bootLoaderType == BootloaderType.PyGrub) {
            vbdr.bootable = true;
        } else if (type == VolumeType.ISO && bootLoaderType == BootloaderType.CD) {
            vbdr.bootable = true;
        }

        if (volume.getType() == VolumeType.ISO) {
            vbdr.mode = Types.VbdMode.RO;
            vbdr.type = Types.VbdType.CD;
            vbdr.userdevice = "3";
        } else {
            vbdr.mode = Types.VbdMode.RW;
            vbdr.type = Types.VbdType.DISK;
            vbdr.unpluggable = volume.getType() == VolumeType.ROOT ? false : true;
            vbdr.userdevice = "autodetect";
            final Long deviceId = volume.getDiskSeq();
            if (deviceId != null && !isDeviceUsed(conn, vm, deviceId) || deviceId > 3) {
                vbdr.userdevice = deviceId.toString();
            }
        }
        final VBD vbd = VBD.create(conn, vbdr);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("VBD " + vbd.getUuid(conn) + " created for " + volume);
        }

        return vbd;
    }

    protected VDI mount(final Connection conn, final String vmName, final DiskTO volume)
            throws XmlRpcException, XenAPIException {
        final DataTO data = volume.getData();
        final VolumeType type = volume.getType();
        if (type == VolumeType.ISO) {
            final TemplateObjectTO iso = (TemplateObjectTO) data;
            final DataStoreTO store = iso.getDataStore();

            if (store == null) {
                // It's a fake iso
                return null;
            }

            // corer case, xenserver pv driver iso
            final String templateName = iso.getName();
            if (templateName.startsWith("xs-tools")) {
                try {
                    final Set<VDI> vdis = VDI.getByNameLabel(conn, templateName);
                    if (vdis.isEmpty()) {
                        throw new CloudRuntimeException("Could not find ISO with URL: " + templateName);
                    }
                    return vdis.iterator().next();
                } catch (final XenAPIException e) {
                    throw new CloudRuntimeException("Unable to get pv iso: " + templateName + " due to " + e.toString());
                } catch (final Exception e) {
                    throw new CloudRuntimeException("Unable to get pv iso: " + templateName + " due to " + e.toString());
                }
            }

            if (!(store instanceof NfsTO)) {
                throw new CloudRuntimeException("only support mount iso on nfs");
            }
            final NfsTO nfsStore = (NfsTO) store;
            final String isoPath = nfsStore.getUrl() + File.separator + iso.getPath();
            final int index = isoPath.lastIndexOf("/");

            final String mountpoint = isoPath.substring(0, index);
            final URI uri;
            try {
                uri = new URI(mountpoint);
            } catch (final URISyntaxException e) {
                throw new CloudRuntimeException("Incorrect uri " + mountpoint, e);
            }
            final SR isoSr = createIsoSRbyURI(conn, uri, vmName, false);

            final String isoname = isoPath.substring(index + 1);

            final VDI isoVdi = getVDIbyLocationandSR(conn, isoname, isoSr);

            if (isoVdi == null) {
                throw new CloudRuntimeException("Unable to find ISO " + isoPath);
            }
            return isoVdi;
        } else {
            final VolumeObjectTO vol = (VolumeObjectTO) data;
            return VDI.getByUuid(conn, vol.getPath());
        }
    }

    public boolean isDeviceUsed(final Connection conn, final VM vm, final Long deviceId) {
        // Figure out the disk number to attach the VM to

        String msg = null;
        try {
            final Set<String> allowedVBDDevices = vm.getAllowedVBDDevices(conn);
            if (allowedVBDDevices.contains(deviceId.toString())) {
                return false;
            }
            return true;
        } catch (final XmlRpcException e) {
            msg = "Catch XmlRpcException due to: " + e.getMessage();
            s_logger.warn(msg, e);
        } catch (final XenAPIException e) {
            msg = "Catch XenAPIException due to: " + e.toString();
            s_logger.warn(msg, e);
        }
        throw new CloudRuntimeException("When check deviceId " + msg);
    }

    protected SR createIsoSRbyURI(final Connection conn, final URI uri, final String vmName, final boolean shared) {
        try {
            final Map<String, String> deviceConfig = new HashMap<>();
            String path = uri.getPath();
            path = path.replace("//", "/");
            deviceConfig.put("location", uri.getHost() + ":" + path);
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            final SR sr = SR.create(conn, host, deviceConfig, new Long(0), uri.getHost() + path, "iso", "iso", "iso", shared,
                    new HashMap<>());
            sr.setNameLabel(conn, vmName + "-ISO");
            sr.setNameDescription(conn, deviceConfig.get("location"));

            sr.scan(conn);
            return sr;
        } catch (final XenAPIException e) {
            final String msg = "createIsoSRbyURI failed! mountpoint: " + uri.getHost() + uri.getPath() + " due to "
                    + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        } catch (final Exception e) {
            final String msg = "createIsoSRbyURI failed! mountpoint: " + uri.getHost() + uri.getPath() + " due to "
                    + e.getMessage();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        }
    }

    protected VDI getVDIbyLocationandSR(final Connection conn, final String loc, final SR sr) {
        try {
            final Set<VDI> vdis = sr.getVDIs(conn);
            for (final VDI vdi : vdis) {
                if (vdi.getLocation(conn).startsWith(loc)) {
                    return vdi;
                }
            }

            final String msg = "can not getVDIbyLocationandSR " + loc;
            s_logger.warn(msg);
            return null;
        } catch (final XenAPIException e) {
            final String msg = "getVDIbyLocationandSR exception " + loc + " due to " + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        } catch (final Exception e) {
            final String msg = "getVDIbyLocationandSR exception " + loc + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        }
    }

    public void createVGPU(final Connection conn, final StartCommand cmd, final VM vm, final GPUDeviceTO gpuDevice)
            throws XenAPIException, XmlRpcException {
    }

    public VIF createVif(final Connection conn, final String vmName, final VM vm, final VirtualMachineTO vmSpec,
                         final NicTO nic) throws XmlRpcException, XenAPIException {
        assert nic.getUuid() != null : "Nic should have a uuid value";

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Creating VIF for " + vmName + " on nic " + nic);
        }
        VIF.Record vifr = new VIF.Record();
        vifr.VM = vm;
        vifr.device = getLowestAvailableVIFDeviceNum(conn, vm);
        vifr.MAC = nic.getMac();

        // Nicira needs these IDs to find the NIC
        vifr.otherConfig = new HashMap<>();
        vifr.otherConfig.put("nicira-iface-id", nic.getUuid());
        vifr.otherConfig.put("nicira-vm-id", vm.getUuid(conn));
        // Provide XAPI with the cloudstack vm and nic uids.
        vifr.otherConfig.put("cloudstack-nic-id", nic.getUuid());
        if (vmSpec != null) {
            vifr.otherConfig.put("cloudstack-vm-id", vmSpec.getUuid());
        }

        // OVS plugin looks at network UUID in the vif 'otherconfig' details to
        // group VIF's & tunnel ports as part of tier
        // when bridge is setup for distributed routing
        vifr.otherConfig.put("cloudstack-network-id", nic.getNetworkUuid());

        // Nuage Vsp needs Virtual Router IP to be passed in the otherconfig
        // get the virtual router IP information from broadcast uri
        final URI broadcastUri = nic.getBroadcastUri();
        if (broadcastUri != null && broadcastUri.getScheme().equalsIgnoreCase(BroadcastDomainType.Vsp.scheme())) {
            final String path = broadcastUri.getPath();
            vifr.otherConfig.put("vsp-vr-ip", path.substring(1));
        }
        vifr.network = getNetwork(conn, nic);

        if (nic.getNetworkRateMbps() != null && nic.getNetworkRateMbps().intValue() != -1) {
            vifr.qosAlgorithmType = "ratelimit";
            vifr.qosAlgorithmParams = new HashMap<>();
            // convert mbs to kilobyte per second
            vifr.qosAlgorithmParams.put("kbps", Integer.toString(nic.getNetworkRateMbps() * 128));
        }

        vifr.lockingMode = Types.VifLockingMode.NETWORK_DEFAULT;
        final VIF vif = VIF.create(conn, vifr);
        if (s_logger.isDebugEnabled()) {
            vifr = vif.getRecord(conn);
            if (vifr != null) {
                s_logger.debug("Created a vif " + vifr.uuid + " on " + vifr.device);
            }
        }

        return vif;
    }

    public VM createVmFromTemplate(final Connection conn, final VirtualMachineTO vmSpec, final Host host)
            throws XenAPIException, XmlRpcException {
        final String guestOsTypeName = getGuestOsType(vmSpec.getOs(), vmSpec.getPlatformEmulator(),
                vmSpec.getBootloader() == BootloaderType.CD);
        final Set<VM> templates = VM.getByNameLabel(conn, guestOsTypeName);
        if (templates == null || templates.isEmpty()) {
            throw new CloudRuntimeException("Cannot find template " + guestOsTypeName + " on XenServer host");
        }
        assert templates.size() == 1 : "Should only have 1 template but found " + templates.size();
        final VM template = templates.iterator().next();

        final VM.Record vmr = template.getRecord(conn);
        vmr.affinity = host;
        vmr.otherConfig.remove("disks");
        vmr.otherConfig.remove("default_template");
        vmr.otherConfig.remove("mac_seed");
        vmr.isATemplate = false;
        vmr.nameLabel = vmSpec.getName();
        vmr.actionsAfterCrash = Types.OnCrashBehaviour.DESTROY;
        vmr.actionsAfterShutdown = Types.OnNormalExit.DESTROY;
        vmr.otherConfig.put("vm_uuid", vmSpec.getUuid());
        vmr.VCPUsMax = (long) vmSpec.getCpus(); // FIX ME: In case of dynamic
        // scaling this VCPU max should
        // be the minumum of
        // recommended value for that template and capacity remaining on host

        if (isDmcEnabled(conn, host) && vmSpec.isEnableDynamicallyScaleVm()) {
            // scaling is allowed
            vmr.memoryStaticMin = getStaticMin(vmSpec.getOs(), vmSpec.getBootloader() == BootloaderType.CD,
                    vmSpec.getMinRam(), vmSpec.getMaxRam());
            vmr.memoryStaticMax = getStaticMax(vmSpec.getOs(), vmSpec.getBootloader() == BootloaderType.CD,
                    vmSpec.getMinRam(), vmSpec.getMaxRam());
            vmr.memoryDynamicMin = vmSpec.getMinRam();
            vmr.memoryDynamicMax = vmSpec.getMaxRam();
            if (guestOsTypeName.toLowerCase().contains("windows")) {
                vmr.VCPUsMax = (long) vmSpec.getCpus();
            } else {
                if (vmSpec.getVcpuMaxLimit() != null) {
                    vmr.VCPUsMax = (long) vmSpec.getVcpuMaxLimit();
                }
            }
        } else {
            // scaling disallowed, set static memory target
            if (vmSpec.isEnableDynamicallyScaleVm() && !isDmcEnabled(conn, host)) {
                s_logger.warn("Host " + host.getHostname(conn) + " does not support dynamic scaling, so the vm "
                        + vmSpec.getName() + " is not dynamically scalable");
            }
            vmr.memoryStaticMin = vmSpec.getMinRam();
            vmr.memoryStaticMax = vmSpec.getMaxRam();
            vmr.memoryDynamicMin = vmSpec.getMinRam();
            vmr.memoryDynamicMax = vmSpec.getMaxRam();

            vmr.VCPUsMax = (long) vmSpec.getCpus();
        }

        vmr.VCPUsAtStartup = (long) vmSpec.getCpus();
        vmr.consoles.clear();

        final VM vm = VM.create(conn, vmr);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Created VM " + vm.getUuid(conn) + " for " + vmSpec.getName());
        }

        final String bootArgs = vmSpec.getBootArgs();
        if (bootArgs != null && bootArgs.length() > 0) {
            String pvargs = vm.getPVArgs(conn);
            pvargs = pvargs + vmSpec.getBootArgs().replaceAll(" ", "%");
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("PV args are " + pvargs);
            }
            vm.setPVArgs(conn, pvargs);
        }

        if (!(guestOsTypeName.startsWith("Windows") || guestOsTypeName.startsWith("Citrix")
                || guestOsTypeName.startsWith("Other"))) {
            if (vmSpec.getBootloader() == BootloaderType.CD) {
                final DiskTO[] disks = vmSpec.getDisks();
                for (final DiskTO disk : disks) {
                    if (disk.getType() == VolumeType.ISO) {
                        final TemplateObjectTO iso = (TemplateObjectTO) disk.getData();
                        final String osType = iso.getGuestOsType();
                        if (osType != null) {
                            final String isoGuestOsName = getGuestOsType(osType, vmSpec.getPlatformEmulator(),
                                    vmSpec.getBootloader() == BootloaderType.CD);
                            if (!isoGuestOsName.equals(guestOsTypeName)) {
                                vmSpec.setBootloader(BootloaderType.PyGrub);
                            }
                        }
                    }
                }
            }
            if (vmSpec.getBootloader() == BootloaderType.CD) {
                vm.setPVBootloader(conn, "eliloader");
                if (!vm.getOtherConfig(conn).containsKey("install-repository")) {
                    vm.addToOtherConfig(conn, "install-repository", "cdrom");
                }
            } else if (vmSpec.getBootloader() == BootloaderType.PyGrub) {
                vm.setPVBootloader(conn, "pygrub");
                vm.setPVBootloaderArgs(conn, CitrixHelper.getPVbootloaderArgs(guestOsTypeName));
            } else {
                vm.destroy(conn);
                throw new CloudRuntimeException("Unable to handle boot loader type: " + vmSpec.getBootloader());
            }
        }
        try {
            finalizeVmMetaData(vm, conn, vmSpec);
        } catch (final Exception e) {
            throw new CloudRuntimeException("Unable to finalize VM MetaData: " + vmSpec);
        }
        return vm;
    }

    protected String getGuestOsType(final String stdType, String platformEmulator, final boolean bootFromCD) {
        if (platformEmulator == null) {
            s_logger.debug("no guest OS type, start it as HVM guest");
            platformEmulator = "Other install media";
        }
        return platformEmulator;
    }

    /**
     * When Dynamic Memory Control (DMC) is enabled - xenserver allows scaling the guest memory while the guest is running
     * <p>
     * By default this is disallowed, override the specific xenserver resource if this is enabled
     */
    public boolean isDmcEnabled(final Connection conn, final Host host) throws XenAPIException, XmlRpcException {
        return false;
    }

    private long getStaticMin(final String os, final boolean b, final long dynamicMinRam, final long dynamicMaxRam) {
        final long recommendedValue = CitrixHelper.getXenServerStaticMin(os, b);
        if (recommendedValue == 0) {
            s_logger.warn("No recommended value found for dynamic min");
            return dynamicMinRam;
        }

        if (dynamicMinRam < recommendedValue) { // XS contraint that dynamic min
            // > static min
            s_logger.warn("Vm is set to dynamixMin " + dynamicMinRam + " less than the recommended static min "
                    + recommendedValue + ", could lead to stability issues");
        }
        return dynamicMinRam;
    }

    private long getStaticMax(final String os, final boolean b, final long dynamicMinRam, final long dynamicMaxRam) {
        final long recommendedValue = CitrixHelper.getXenServerStaticMax(os, b);
        if (recommendedValue == 0) {
            s_logger.warn("No recommended value found for dynamic max, setting static max and dynamic max equal");
            return dynamicMaxRam;
        }
        final long staticMax = Math.min(recommendedValue, 4l * dynamicMinRam); // XS
        // constraint
        // for
        // stability
        if (dynamicMaxRam > staticMax) { // XS contraint that dynamic max <=
            // static max
            s_logger.warn("dynamixMax " + dynamicMaxRam + " cant be greater than static max " + staticMax
                    + ", can lead to stability issues. Setting static max as much as dynamic max ");
            return dynamicMaxRam;
        }
        return staticMax;
    }

    protected void finalizeVmMetaData(final VM vm, final Connection conn, final VirtualMachineTO vmSpec)
            throws Exception {

        final Map<String, String> details = vmSpec.getDetails();
        if (details != null) {
            final String platformstring = details.get("platform");
            if (platformstring != null && !platformstring.isEmpty()) {
                final Map<String, String> platform = StringUtils.stringToMap(platformstring);
                vm.setPlatform(conn, platform);
            } else {
                final String timeoffset = details.get("timeoffset");
                if (timeoffset != null) {
                    final Map<String, String> platform = vm.getPlatform(conn);
                    platform.put("timeoffset", timeoffset);
                    vm.setPlatform(conn, platform);
                }
                final String coresPerSocket = details.get("cpu.corespersocket");
                if (coresPerSocket != null) {
                    final Map<String, String> platform = vm.getPlatform(conn);
                    platform.put("cores-per-socket", coresPerSocket);
                    vm.setPlatform(conn, platform);
                }
            }
            if (!BootloaderType.CD.equals(vmSpec.getBootloader())) {
                final String xenservertoolsversion = details.get("hypervisortoolsversion");
                if ((xenservertoolsversion == null || !xenservertoolsversion.equalsIgnoreCase("xenserver61"))
                        && vmSpec.getGpuDevice() == null) {
                    final Map<String, String> platform = vm.getPlatform(conn);
                    platform.remove("device_id");
                    vm.setPlatform(conn, platform);
                }
            }
        }
    }

    public VM createWorkingVM(final Connection conn, final String vmName, final String guestOSType,
                              final String platformEmulator, final List<VolumeObjectTO> listVolumeTo)
            throws XenAPIException, XmlRpcException {
        // below is redundant but keeping for consistency and code readabilty
        final String guestOsTypeName = platformEmulator;
        if (guestOsTypeName == null) {
            final String msg = " Hypervisor " + this.getClass().getName() + " doesn't support guest OS type " + guestOSType
                    + ". you can choose 'Other install media' to run it as HVM";
            s_logger.warn(msg);
            throw new CloudRuntimeException(msg);
        }
        final VM template = getVM(conn, guestOsTypeName);
        final VM vm = template.createClone(conn, vmName);
        vm.setIsATemplate(conn, false);
        final Map<VDI, VolumeObjectTO> vdiMap = new HashMap<>();
        for (final VolumeObjectTO volume : listVolumeTo) {
            final String vdiUuid = volume.getPath();
            try {
                final VDI vdi = VDI.getByUuid(conn, vdiUuid);
                vdiMap.put(vdi, volume);
            } catch (final Types.UuidInvalid e) {
                s_logger.warn("Unable to find vdi by uuid: " + vdiUuid + ", skip it");
            }
        }
        for (final Map.Entry<VDI, VolumeObjectTO> entry : vdiMap.entrySet()) {
            final VDI vdi = entry.getKey();
            final VolumeObjectTO volumeTO = entry.getValue();
            final VBD.Record vbdr = new VBD.Record();
            vbdr.VM = vm;
            vbdr.VDI = vdi;
            if (volumeTO.getVolumeType() == VolumeType.ROOT) {
                vbdr.bootable = true;
                vbdr.unpluggable = false;
            } else {
                vbdr.bootable = false;
                vbdr.unpluggable = true;
            }
            vbdr.userdevice = "autodetect";
            vbdr.mode = Types.VbdMode.RW;
            vbdr.type = Types.VbdType.DISK;
            VBD.create(conn, vbdr);
        }
        return vm;
    }

    public VM getVM(final Connection conn, final String vmName) {
        // Look up VMs with the specified name
        final Set<VM> vms;
        try {
            vms = VM.getByNameLabel(conn, vmName);
        } catch (final XenAPIException e) {
            throw new CloudRuntimeException("Unable to get " + vmName + ": " + e.toString(), e);
        } catch (final Exception e) {
            throw new CloudRuntimeException("Unable to get " + vmName + ": " + e.getMessage(), e);
        }

        // If there are no VMs, throw an exception
        if (vms.size() == 0) {
            throw new CloudRuntimeException("VM with name: " + vmName + " does not exist.");
        }

        // If there is more than one VM, print a warning
        if (vms.size() > 1) {
            s_logger.warn("Found " + vms.size() + " VMs with name: " + vmName);
        }

        // Return the first VM in the set
        return vms.iterator().next();
    }

    protected boolean deleteSecondaryStorageFolder(final Connection conn, final String remoteMountPath,
                                                   final String folder) {
        final String details = callHostPlugin(conn, "vmopsSnapshot", "delete_secondary_storage_folder", "remoteMountPath",
                remoteMountPath, "folder", folder);
        return details != null && details.equals("1");
    }

    public void destroyPatchVbd(final Connection conn, final String vmName) {
        try {
            if (!vmName.startsWith("r-") && !vmName.startsWith("s-") && !vmName.startsWith("v-")) {
                return;
            }
            final Set<VM> vms = VM.getByNameLabel(conn, vmName);
            for (final VM vm : vms) {
                final Set<VBD> vbds = vm.getVBDs(conn);
                for (final VBD vbd : vbds) {
                    if (vbd.getType(conn) == Types.VbdType.CD) {
                        vbd.eject(conn);
                        vbd.destroy(conn);
                        break;
                    }
                }
            }
        } catch (final Exception e) {
            s_logger.debug("Cannot destory CD-ROM device for VM " + vmName + " due to " + e.toString(), e);
        }
    }

    void destroyVDIbyNameLabel(final Connection conn, final String nameLabel) {
        try {
            final Set<VDI> vdis = VDI.getByNameLabel(conn, nameLabel);
            if (vdis.size() != 1) {
                s_logger.warn("destoryVDIbyNameLabel failed due to there are " + vdis.size() + " VDIs with name " + nameLabel);
                return;
            }
            for (final VDI vdi : vdis) {
                try {
                    vdi.destroy(conn);
                } catch (final Exception e) {
                    final String msg = "Failed to destroy VDI : " + nameLabel + "due to " + e.toString()
                            + "\n Force deleting VDI using system 'rm' command";
                    s_logger.warn(msg);
                    try {
                        final String srUUID = vdi.getSR(conn).getUuid(conn);
                        final String vdiUUID = vdi.getUuid(conn);
                        final String vdifile = "/var/run/sr-mount/" + srUUID + "/" + vdiUUID + ".vhd";
                        callHostPluginAsync(conn, "vmopspremium", "remove_corrupt_vdi", 10, "vdifile", vdifile);
                    } catch (final Exception e2) {
                        s_logger.warn(e2.toString());
                    }
                }
            }
        } catch (final Exception e) {
        }
    }

    public boolean doPingTest(final Connection conn, final String computingHostIp) {
        final com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(this._host.getIp(), 22);
        try {
            sshConnection.connect(null, 60000, 60000);
            if (!sshConnection.authenticateWithPassword(this._username, this._password.peek())) {
                throw new CloudRuntimeException("Unable to authenticate");
            }

            final String cmd = "ping -c 2 " + computingHostIp;
            if (!SSHCmdHelper.sshExecuteCmd(sshConnection, cmd)) {
                throw new CloudRuntimeException("Cannot ping host " + computingHostIp + " from host " + this._host.getIp());
            }
            return true;
        } catch (final Exception e) {
            s_logger.warn("Catch exception " + e.toString(), e);
            return false;
        } finally {
            sshConnection.close();
        }
    }

    public boolean doPingTest(final Connection conn, final String domRIp, final String vmIp) {
        final String args = "-i " + domRIp + " -p " + vmIp;
        final String result = callHostPlugin(conn, "vmops", "pingtest", "args", args);
        if (result == null || result.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * enableVlanNetwork creates a Network object, Vlan object, and thereby a tagged PIF object in Xapi.
     * <p>
     * In XenServer, VLAN is added by - Create a network, which is unique cluster wide. - Find the PIF that you want to
     * create the VLAN on. - Create a VLAN using the network and the PIF. As a result of this operation, a tagged PIF
     * object is also created.
     * <p>
     * Here is a list of problems with clustered Xapi implementation that we are trying to circumvent. - There can be
     * multiple Networks with the same name-label so searching using name-label is not unique. - There are no other ways
     * to search for Networks other than listing all of them which is not efficient in our implementation because we can
     * have over 4000 VLAN networks. - In a clustered situation, it's possible for both hosts to detect that the Network
     * is missing and both creates it. This causes a lot of problems as one host may be using one Network and another may
     * be using a different network for their VMs. This causes problems in migration because the VMs are logically
     * attached to different networks in Xapi's database but in reality, they are attached to the same network.
     * <p>
     * To work around these problems, we do the following.
     * <p>
     * - When creating the VLAN network, we name it as VLAN-UUID of the Network it is created on-VLAN Tag. Because VLAN
     * tags is unique with one particular network, this is a unique name-label to quickly retrieve the the VLAN network
     * with when we need it again. - When we create the VLAN network, we add a timestamp and a random number as a tag into
     * the network. Then instead of creating VLAN on that network, we actually retrieve the Network again and this time
     * uses the VLAN network with lowest timestamp or lowest random number as the VLAN network. This allows VLAN creation
     * to happen on multiple hosts concurrently but even if two VLAN networks were created with the same name, only one of
     * them is used.
     * <p>
     * One cavaet about this approach is that it relies on the timestamp to be relatively accurate among different hosts.
     *
     * @param conn    Xapi Connection
     * @param tag     VLAN tag
     * @param network network on this host to create the VLAN on.
     * @return VLAN Network created.
     * @throws XenAPIException
     * @throws XmlRpcException
     */
    protected Network enableVlanNetwork(final Connection conn, final long tag, final XsLocalNetwork network)
            throws XenAPIException, XmlRpcException {
        Network vlanNetwork = null;
        final String oldName = "VLAN" + Long.toString(tag);
        final String newName = "VLAN-" + network.getNetworkRecord(conn).uuid + "-" + tag;
        XsLocalNetwork vlanNic = getNetworkByName(conn, newName);
        if (vlanNic == null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Couldn't find vlan network with the new name so trying old name: " + oldName);
            }
            vlanNic = getNetworkByName(conn, oldName);
            if (vlanNic != null) {
                s_logger.info("Renaming VLAN with old name " + oldName + " to " + newName);
                vlanNic.getNetwork().setNameLabel(conn, newName);
            }
        }
        if (vlanNic == null) { // Can't find it, then create it.
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Creating VLAN network for " + tag + " on host " + this._host.getIp());
            }
            final Network.Record nwr = new Network.Record();
            nwr.nameLabel = newName;
            nwr.tags = new HashSet<>();
            nwr.tags.add(generateTimeStamp());
            vlanNetwork = Network.create(conn, nwr);
            vlanNic = getNetworkByName(conn, newName);
            if (vlanNic == null) { // Still vlanNic is null means we could not
                // create it for some reason and no exception
                // capture happened.
                throw new CloudRuntimeException("Could not find/create vlan network with name: " + newName);
            }
        }

        final PIF nPif = network.getPif(conn);
        final PIF.Record nPifr = network.getPifRecord(conn);

        vlanNetwork = vlanNic.getNetwork();
        if (vlanNic.getPif(conn) != null) {
            return vlanNetwork;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Creating VLAN " + tag + " on host " + this._host.getIp() + " on device " + nPifr.device);
        }
        final VLAN vlan = VLAN.create(conn, nPif, tag, vlanNetwork);
        if (vlan != null) {
            final VLAN.Record vlanr = vlan.getRecord(conn);
            if (vlanr != null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("VLAN is created for " + tag + ".  The uuid is " + vlanr.uuid);
                }
            }
        }
        return vlanNetwork;
    }

    @Override
    public StartAnswer execute(final StartCommand cmd) {
        throw new CloudRuntimeException("The method has been replaced but the implementation CitrixStartCommandWrapper. "
                + "Please use the new design in order to keep compatibility. Once all ServerResource implementation are refactored those methods will dissapper.");
    }

    @Override
    public StopAnswer execute(final StopCommand cmd) {
        throw new CloudRuntimeException("The method has been replaced but the implementation CitrixStopCommandWrapper. "
                + "Please use the new design in order to keep compatibility. Once all ServerResource implementation are refactored those methods will dissapper.");
    }

    @Override
    public RebootAnswer execute(final RebootCommand cmd) {
        throw new CloudRuntimeException("The method has been replaced but the implementation CitrixRebootCommandWrapper. "
                + "Please use the new design in order to keep compatibility. Once all ServerResource implementation are refactored those methods will dissapper.");
    }

    @Override
    public ExecutionResult executeInVR(final String routerIP, final String script, final String args) {
        // Timeout is 120 seconds by default
        return executeInVR(routerIP, script, args, 120);
    }

    @Override
    public ExecutionResult executeInVR(final String routerIP, final String script, final String args, final int timeout) {
        final Pair<Boolean, String> result;
        String cmdline = "/opt/cloud/bin/router_proxy.sh " + script + " " + routerIP + " " + args;
        // semicolon need to be escape for bash
        cmdline = cmdline.replaceAll(";", "\\\\;");
        try {
            s_logger.debug("Executing command in VR: " + cmdline);
            result = SshHelper.sshExecute(this._host.getIp(), 22, this._username, null, this._password.peek(), cmdline, 60000, 60000,
                    timeout * 1000);
        } catch (final Exception e) {
            return new ExecutionResult(false, e.getMessage());
        }
        return new ExecutionResult(result.first(), result.second());
    }

    @Override
    public ExecutionResult createFileInVR(final String routerIp, final String path, final String filename,
                                          final String content) {
        final Connection conn = getConnection();
        final String hostPath = "/tmp/";

        s_logger.debug("Copying VR with ip " + routerIp + " config file into host " + this._host.getIp());
        try {
            SshHelper.scpTo(this._host.getIp(), 22, this._username, null, this._password.peek(), hostPath,
                    content.getBytes(Charset.defaultCharset()), filename, null);
        } catch (final Exception e) {
            s_logger.warn(
                    "scp VR config file into host " + this._host.getIp() + " failed with exception " + e.getMessage().toString());
        }

        final String rc = callHostPlugin(conn, "vmops", "createFileInDomr", "domrip", routerIp, "srcfilepath",
                hostPath + filename, "dstfilepath", path);
        s_logger.debug("VR Config file " + filename + " got created in VR, ip " + routerIp + " with content \n" + content);

        return new ExecutionResult(rc.startsWith("succ#"), rc.substring(5));
    }

    public Connection getConnection() {
        return ConnPool.connect(this._host.getUuid(), this._host.getPool(), this._host.getIp(), this._username, this._password, this._wait);
    }

    @Override
    public ExecutionResult prepareCommand(final NetworkElementCommand cmd) {
        // Update IP used to access router
        cmd.setRouterAccessIp(cmd.getAccessDetail(NetworkElementCommand.ROUTER_IP));
        assert cmd.getRouterAccessIp() != null;

        if (cmd instanceof SetNetworkACLCommand) {
            return prepareNetworkElementCommand((SetNetworkACLCommand) cmd);
        }
        return new ExecutionResult(true, null);
    }

    @Override
    public Map<String, Object> getConfigParams() {
        return null;
    }

    @Override
    public ExecutionResult cleanupCommand(final NetworkElementCommand cmd) {
        return new ExecutionResult(true, null);
    }

    protected String generateTimeStamp() {
        return new StringBuilder("CsCreateTime-").append(System.currentTimeMillis()).append("-").append(
                Rand.nextInt(Integer.MAX_VALUE)).toString();
    }

    public HashMap<String, HashMap<String, VgpuTypesInfo>> getGPUGroupDetails(final Connection conn)
            throws XenAPIException, XmlRpcException {
        return null;
    }

    public int getMigrateWait() {
        return this._migratewait;
    }

    public StorageSubsystemCommandHandler getStorageHandler() {
        return this.storageHandler;
    }

    public HostStatsEntry getHostStats(final Connection conn, final GetHostStatsCommand cmd, final String hostGuid,
                                       final long hostId) {

        final HostStatsEntry hostStats = new HostStatsEntry(hostId, 0, 0, 0, "host", 0, 0, 0, 0);
        final Object[] rrdData = getRRDData(conn, 1); // call rrd method with 1
        // for host

        if (rrdData == null) {
            return null;
        }

        final Integer numRows = (Integer) rrdData[0];
        final Integer numColumns = (Integer) rrdData[1];
        final Node legend = (Node) rrdData[2];
        final Node dataNode = (Node) rrdData[3];

        final NodeList legendChildren = legend.getChildNodes();
        for (int col = 0; col < numColumns; col++) {

            if (legendChildren == null || legendChildren.item(col) == null) {
                continue;
            }

            final String columnMetadata = getXMLNodeValue(legendChildren.item(col));

            if (columnMetadata == null) {
                continue;
            }

            final String[] columnMetadataList = columnMetadata.split(":");

            if (columnMetadataList.length != 4) {
                continue;
            }

            final String type = columnMetadataList[1];
            final String param = columnMetadataList[3];

            if (type.equalsIgnoreCase("host")) {

                if (param.matches("pif_eth0_rx")) {
                    hostStats.setNetworkReadKBs(getDataAverage(dataNode, col, numRows) / 1000);
                } else if (param.matches("pif_eth0_tx")) {
                    hostStats.setNetworkWriteKBs(getDataAverage(dataNode, col, numRows) / 1000);
                } else if (param.contains("memory_total_kib")) {
                    hostStats.setTotalMemoryKBs(getDataAverage(dataNode, col, numRows));
                } else if (param.contains("memory_free_kib")) {
                    hostStats.setFreeMemoryKBs(getDataAverage(dataNode, col, numRows));
                } else if (param.matches("cpu_avg")) {
                    // hostStats.setNumCpus(hostStats.getNumCpus() + 1);
                    hostStats.setCpuUtilization(hostStats.getCpuUtilization() + getDataAverage(dataNode, col, numRows));
                }

                /*
                 * if (param.contains("loadavg")) { hostStats.setAverageLoad((hostStats.getAverageLoad() +
                 * getDataAverage(dataNode, col, numRows))); }
                 */
            }
        }

        // add the host cpu utilization
        /*
         * if (hostStats.getNumCpus() != 0) { hostStats.setCpuUtilization(hostStats.getCpuUtilization() /
         * hostStats.getNumCpus()); s_logger.debug("Host cpu utilization " + hostStats.getCpuUtilization()); }
         */

        return hostStats;
    }

    protected Object[] getRRDData(final Connection conn, final int flag) {

        /*
         * Note: 1 => called from host, hence host stats 2 => called from vm, hence vm stats
         */
        Document doc = null;

        try {
            doc = getStatsRawXML(conn, flag == 1 ? true : false);
        } catch (final Exception e1) {
            s_logger.warn("Error whilst collecting raw stats from plugin: ", e1);
            return null;
        }

        if (doc == null) { // stats are null when the host plugin call fails
            // (host down state)
            return null;
        }

        final NodeList firstLevelChildren = doc.getChildNodes();
        final NodeList secondLevelChildren = firstLevelChildren.item(0).getChildNodes();
        final Node metaNode = secondLevelChildren.item(0);
        final Node dataNode = secondLevelChildren.item(1);

        Integer numRows = 0;
        Integer numColumns = 0;
        Node legend = null;
        final NodeList metaNodeChildren = metaNode.getChildNodes();
        for (int i = 0; i < metaNodeChildren.getLength(); i++) {
            final Node n = metaNodeChildren.item(i);
            if (n.getNodeName().equals("rows")) {
                numRows = Integer.valueOf(getXMLNodeValue(n));
            } else if (n.getNodeName().equals("columns")) {
                numColumns = Integer.valueOf(getXMLNodeValue(n));
            } else if (n.getNodeName().equals("legend")) {
                legend = n;
            }
        }

        return new Object[]{numRows, numColumns, legend, dataNode};
    }

    protected String getXMLNodeValue(final Node n) {
        return n.getChildNodes().item(0).getNodeValue();
    }

    protected double getDataAverage(final Node dataNode, final int col, final int numRows) {
        double value = 0;
        final double dummy = 0;
        int numRowsUsed = 0;
        for (int row = 0; row < numRows; row++) {
            final Node data = dataNode.getChildNodes().item(numRows - 1 - row).getChildNodes().item(col + 1);
            final Double currentDataAsDouble = Double.valueOf(getXMLNodeValue(data));
            if (!currentDataAsDouble.equals(Double.NaN)) {
                numRowsUsed += 1;
                value += currentDataAsDouble;
            }
        }

        if (numRowsUsed == 0) {
            if (!Double.isInfinite(value) && !Double.isNaN(value)) {
                return value;
            } else {
                s_logger.warn("Found an invalid value (infinity/NaN) in getDataAverage(), numRows=0");
                return dummy;
            }
        } else {
            if (!Double.isInfinite(value / numRowsUsed) && !Double.isNaN(value / numRowsUsed)) {
                return value / numRowsUsed;
            } else {
                s_logger.warn("Found an invalid value (infinity/NaN) in getDataAverage(), numRows>0");
                return dummy;
            }
        }
    }

    protected Document getStatsRawXML(final Connection conn, final boolean host) {
        final Date currentDate = new Date();
        String urlStr = "http://" + this._host.getIp() + "/rrd_updates?";
        urlStr += "session_id=" + conn.getSessionReference();
        urlStr += "&host=" + (host ? "true" : "false");
        urlStr += "&cf=" + this._consolidationFunction;
        urlStr += "&interval=" + this._pollingIntervalInSeconds;
        urlStr += "&start=" + (currentDate.getTime() / 1000 - 1000 - 100);

        final URL url;
        BufferedReader in = null;
        try {
            url = new URL(urlStr);
            url.openConnection();
            final URLConnection uc = url.openConnection();
            in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
            final InputSource statsSource = new InputSource(in);
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(statsSource);
        } catch (final MalformedURLException e) {
            s_logger.warn("Malformed URL?  come on...." + urlStr);
            return null;
        } catch (final IOException e) {
            s_logger.warn("Problems getting stats using " + urlStr, e);
            return null;
        } catch (final SAXException e) {
            s_logger.warn("Problems getting stats using " + urlStr, e);
            return null;
        } catch (final ParserConfigurationException e) {
            s_logger.warn("Problems getting stats using " + urlStr, e);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException e) {
                    s_logger.warn("Unable to close the buffer ", e);
                }
            }
        }
    }

    protected HashMap<String, HostVmStateReportEntry> getHostVmStateReport(final Connection conn) {

        // TODO : new VM sync model does not require a cluster-scope report, we
        // need to optimize
        // the report accordingly
        final HashMap<String, HostVmStateReportEntry> vmStates = new HashMap<>();
        Map<VM, VM.Record> vm_map = null;
        for (int i = 0; i < 2; i++) {
            try {
                vm_map = VM.getAllRecords(conn); // USE THIS TO GET ALL VMS FROM
                // A CLUSTER
                break;
            } catch (final XmlRpcException | XenAPIException e) {
                s_logger.warn("Unable to get vms", e);
            }
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                s_logger.warn("Caught (previously ignored) interrupted exception", e);
            }
        }

        if (vm_map == null) {
            return vmStates;
        }
        for (final VM.Record record : vm_map.values()) {
            if (record.isControlDomain || record.isASnapshot || record.isATemplate) {
                continue; // Skip DOM0
            }

            final VmPowerState ps = record.powerState;
            final Host host = record.residentOn;
            String host_uuid = null;
            if (!isRefNull(host)) {
                try {
                    host_uuid = host.getUuid(conn);
                } catch (final BadServerResponse e) {
                    s_logger.error("Failed to get host uuid for host " + host.toWireString(), e);
                } catch (final XenAPIException e) {
                    s_logger.error("Failed to get host uuid for host " + host.toWireString(), e);
                } catch (final XmlRpcException e) {
                    s_logger.error("Failed to get host uuid for host " + host.toWireString(), e);
                }

                if (host_uuid.equalsIgnoreCase(this._host.getUuid())) {
                    vmStates.put(record.nameLabel, new HostVmStateReportEntry(convertToPowerState(ps), host_uuid));
                }
            }
        }

        return vmStates;
    }

    public VDI getIsoVDIByURL(final Connection conn, final String vmName, final String isoURL) {
        SR isoSR = null;
        String mountpoint = null;
        if (isoURL.startsWith("xs-tools")) {
            try {
                final Set<VDI> vdis = VDI.getByNameLabel(conn, isoURL);
                if (vdis.isEmpty()) {
                    throw new CloudRuntimeException("Could not find ISO with URL: " + isoURL);
                }
                return vdis.iterator().next();
            } catch (final XenAPIException e) {
                throw new CloudRuntimeException("Unable to get pv iso: " + isoURL + " due to " + e.toString());
            } catch (final Exception e) {
                throw new CloudRuntimeException("Unable to get pv iso: " + isoURL + " due to " + e.toString());
            }
        }

        final int index = isoURL.lastIndexOf("/");
        mountpoint = isoURL.substring(0, index);

        final URI uri;
        try {
            uri = new URI(mountpoint);
        } catch (final URISyntaxException e) {
            throw new CloudRuntimeException("isoURL is wrong: " + isoURL);
        }
        isoSR = getISOSRbyVmName(conn, vmName);
        if (isoSR == null) {
            isoSR = createIsoSRbyURI(conn, uri, vmName, false);
        }

        final String isoName = isoURL.substring(index + 1);

        final VDI isoVDI = getVDIbyLocationandSR(conn, isoName, isoSR);

        if (isoVDI != null) {
            return isoVDI;
        } else {
            throw new CloudRuntimeException("Could not find ISO with URL: " + isoURL);
        }
    }

    public SR getISOSRbyVmName(final Connection conn, final String vmName) {
        try {
            final Set<SR> srs = SR.getByNameLabel(conn, vmName + "-ISO");
            if (srs.size() == 0) {
                return null;
            } else if (srs.size() == 1) {
                return srs.iterator().next();
            } else {
                final String msg = "getIsoSRbyVmName failed due to there are more than 1 SR having same Label";
                s_logger.warn(msg);
            }
        } catch (final XenAPIException e) {
            final String msg = "getIsoSRbyVmName failed due to " + e.toString();
            s_logger.warn(msg, e);
        } catch (final Exception e) {
            final String msg = "getIsoSRbyVmName failed due to " + e.getMessage();
            s_logger.warn(msg, e);
        }
        return null;
    }

    public String getLabel() {
        final Connection conn = getConnection();
        final String result = callHostPlugin(conn, "ovstunnel", "getLabel");
        return result;
    }

    public String getLowestAvailableVIFDeviceNum(final Connection conn, final VM vm) {
        String vmName = "";
        try {
            vmName = vm.getNameLabel(conn);
            final List<Integer> usedDeviceNums = new ArrayList<>();
            final Set<VIF> vifs = vm.getVIFs(conn);
            final Iterator<VIF> vifIter = vifs.iterator();
            while (vifIter.hasNext()) {
                final VIF vif = vifIter.next();
                try {
                    final String deviceId = vif.getDevice(conn);
                    if (vm.getIsControlDomain(conn) || vif.getCurrentlyAttached(conn)) {
                        usedDeviceNums.add(Integer.valueOf(deviceId));
                    } else {
                        s_logger.debug("Found unplugged VIF " + deviceId + " in VM " + vmName + " destroy it");
                        vif.destroy(conn);
                    }
                } catch (final NumberFormatException e) {
                    final String msg = "Obtained an invalid value for an allocated VIF device number for VM: " + vmName;
                    s_logger.debug(msg, e);
                    throw new CloudRuntimeException(msg);
                }
            }

            for (Integer i = 0; i < this._maxNics; i++) {
                if (!usedDeviceNums.contains(i)) {
                    s_logger.debug("Lowest available Vif device number: " + i + " for VM: " + vmName);
                    return i.toString();
                }
            }
        } catch (final XmlRpcException e) {
            final String msg = "Caught XmlRpcException: " + e.getMessage();
            s_logger.warn(msg, e);
        } catch (final XenAPIException e) {
            final String msg = "Caught XenAPIException: " + e.toString();
            s_logger.warn(msg, e);
        }

        throw new CloudRuntimeException("Could not find available VIF slot in VM with name: " + vmName);
    }

    @Override
    public String getName() {
        return this._name;
    }

    @Override
    public void setName(final String name) {
    }

    public XsLocalNetwork getNativeNetworkForTraffic(final Connection conn, final TrafficType type, final String name)
            throws XenAPIException, XmlRpcException {
        if (name != null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Looking for network named " + name);
            }
            return getNetworkByName(conn, name);
        }

        if (type == TrafficType.Guest) {
            return new XsLocalNetwork(this, Network.getByUuid(conn, this._host.getGuestNetwork()), null,
                    PIF.getByUuid(conn, this._host.getGuestPif()), null);
        } else if (type == TrafficType.Control) {
            setupLinkLocalNetwork(conn);
            return new XsLocalNetwork(this, Network.getByUuid(conn, this._host.getLinkLocalNetwork()));
        } else if (type == TrafficType.Management) {
            return new XsLocalNetwork(this, Network.getByUuid(conn, this._host.getPrivateNetwork()), null,
                    PIF.getByUuid(conn, this._host.getPrivatePif()), null);
        } else if (type == TrafficType.Public) {
            return new XsLocalNetwork(this, Network.getByUuid(conn, this._host.getPublicNetwork()), null,
                    PIF.getByUuid(conn, this._host.getPublicPif()), null);
        } else if (type == TrafficType.Storage) {
            /*
             * TrafficType.Storage is for secondary storage, while storageNetwork1 is for primary storage, we need better name
             * here
             */
            return new XsLocalNetwork(this, Network.getByUuid(conn, this._host.getStorageNetwork1()), null,
                    PIF.getByUuid(conn, this._host.getStoragePif1()), null);
        }

        throw new CloudRuntimeException("Unsupported network type: " + type);
    }

    public Network getNetwork(final Connection conn, final NicTO nic) throws XenAPIException, XmlRpcException {
        final String name = nic.getName();
        final XsLocalNetwork network = getNativeNetworkForTraffic(conn, nic.getType(), name);
        if (network == null) {
            s_logger.error("Network is not configured on the backend for nic " + nic.toString());
            throw new CloudRuntimeException(
                    "Network for the backend is not configured correctly for network broadcast domain: " + nic.getBroadcastUri());
        }
        final URI uri = nic.getBroadcastUri();
        final BroadcastDomainType type = nic.getBroadcastType();
        if (uri != null && uri.toString().contains("untagged")) {
            return network.getNetwork();
        } else if (uri != null && type == BroadcastDomainType.Vlan) {
            assert BroadcastDomainType.getSchemeValue(uri) == BroadcastDomainType.Vlan;
            final long vlan = Long.parseLong(BroadcastDomainType.getValue(uri));
            return enableVlanNetwork(conn, vlan, network);
        } else if (type == BroadcastDomainType.Native || type == BroadcastDomainType.LinkLocal
                || type == BroadcastDomainType.Vsp) {
            return network.getNetwork();
        } else if (type == BroadcastDomainType.Storage) {
            if (uri == null) {
                return network.getNetwork();
            } else {
                final long vlan = Long.parseLong(BroadcastDomainType.getValue(uri));
                return enableVlanNetwork(conn, vlan, network);
            }
        } else if (type == BroadcastDomainType.Lswitch) {
            // Nicira Logical Switch
            return network.getNetwork();
        } else if (uri != null && type == BroadcastDomainType.Pvlan) {
            assert BroadcastDomainType.getSchemeValue(uri) == BroadcastDomainType.Pvlan;
            // should we consider moving this NetUtils method to
            // BroadcastDomainType?
            final long vlan = Long.parseLong(NetUtils.getPrimaryPvlanFromUri(uri));
            return enableVlanNetwork(conn, vlan, network);
        }

        throw new CloudRuntimeException(
                "Unable to support this type of network broadcast domain: " + nic.getBroadcastUri());
    }

    public long[] getNetworkStats(final Connection conn, final String privateIP) {
        final String result = networkUsage(conn, privateIP, "get", null);
        final long[] stats = new long[2];
        if (result != null) {
            final String[] splitResult = result.split(":");
            int i = 0;
            while (i < splitResult.length - 1) {
                stats[0] += Long.parseLong(splitResult[i++]);
                stats[1] += Long.parseLong(splitResult[i++]);
            }
        }
        return stats;
    }

    public String networkUsage(final Connection conn, final String privateIpAddress, final String option,
                               final String vif) {
        if (option.equals("get")) {
            return "0:0";
        }
        return null;
    }

    public String getPerfMon(final Connection conn, final Map<String, String> params, final int wait) {
        String result = null;
        try {
            result = callHostPluginAsync(conn, "vmopspremium", "asmonitor", 60, params);
            if (result != null) {
                return result;
            }
        } catch (final Exception e) {
            s_logger.error("Can not get performance monitor for AS due to ", e);
        }
        return null;
    }

    protected String callHostPluginAsync(final Connection conn, final String plugin, final String cmd, final int wait,
                                         final Map<String, String> params) {
        final int timeout = wait * 1000;
        final Map<String, String> args = new HashMap<>();
        Task task = null;
        try {
            for (final Map.Entry<String, String> entry : params.entrySet()) {
                args.put(entry.getKey(), entry.getValue());
            }
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin executing for command " + cmd + " with " + getArgsString(args));
            }
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            task = host.callPluginAsync(conn, plugin, cmd, args);
            // poll every 1 seconds
            waitForTask(conn, task, 1000, timeout);
            checkForSuccess(conn, task);
            final String result = task.getResult(conn);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin Result: " + result);
            }
            return result.replace("<value>", "").replace("</value>", "").replace("\n", "");
        } catch (final Types.HandleInvalid e) {
            s_logger.warn("callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args)
                    + " due to HandleInvalid clazz:" + e.clazz + ", handle:" + e.handle);
        } catch (final Exception e) {
            s_logger.warn(
                    "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.toString(), e);
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e1) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e1.toString());
                }
            }
        }
        return null;
    }

    @Override
    public HostType getType() {
        return HostType.Routing;
    }

    @Override
    public StartupCommand[] initialize() throws IllegalArgumentException {
        final Connection conn = getConnection();
        if (!getHostInfo(conn)) {
            s_logger.warn("Unable to get host information for " + this._host.getIp());
            return null;
        }
        final StartupRoutingCommand cmd = new StartupRoutingCommand();
        fillHostInfo(conn, cmd);
        cmd.setHypervisorType(HypervisorType.XenServer);
        cmd.setCluster(this._cluster);
        cmd.setPoolSync(false);

        try {
            final Pool pool = Pool.getByUuid(conn, this._host.getPool());
            final Pool.Record poolr = pool.getRecord(conn);
            poolr.master.getRecord(conn);
        } catch (final XmlRpcException | XenAPIException e) {
            s_logger.warn("Check for master failed, failing the FULL Cluster sync command");
        }
        final StartupStorageCommand sscmd = initializeLocalSR(conn);
        if (sscmd != null) {
            return new StartupCommand[]{cmd, sscmd};
        }
        return new StartupCommand[]{cmd};
    }

    @Override
    public PingCommand getCurrentStatus(final long id) {
        try {
            if (!pingXAPI()) {
                Thread.sleep(1000);
                if (!pingXAPI()) {
                    s_logger.warn("can not ping xenserver " + this._host.getUuid());
                    return null;
                }
            }
            final Connection conn = getConnection();
            if (!this._canBridgeFirewall) {
                return new PingRoutingCommand(getType(), id, getHostVmStateReport(conn));
            } else {
                final HashMap<String, Pair<Long, Long>> nwGrpStates = syncNetworkGroups(conn, id);
                return new PingRoutingWithNwGroupsCommand(getType(), id, getHostVmStateReport(conn), nwGrpStates);
            }
        } catch (final Exception e) {
            s_logger.warn("Unable to get current status", e);
            return null;
        }
    }

    @Override
    public Answer executeRequest(final Command cmd) {
        final CitrixRequestWrapper wrapper = CitrixRequestWrapper.getInstance();
        try {
            return wrapper.execute(cmd, this);
        } catch (final Exception e) {
            return Answer.createUnsupportedCommandAnswer(cmd);
        }
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return this._agentControl;
    }

    @Override
    public void setAgentControl(final IAgentControl agentControl) {
        this._agentControl = agentControl;
    }

    @Override
    public int getRunLevel() {
        return 0;
    }

    protected boolean getHostInfo(final Connection conn) throws IllegalArgumentException {
        try {
            final Host myself = Host.getByUuid(conn, this._host.getUuid());
            Set<HostCpu> hcs = null;
            for (int i = 0; i < 10; i++) {
                hcs = myself.getHostCPUs(conn);
                if (hcs != null) {
                    this._host.setCpus(hcs.size());
                    if (this._host.getCpus() > 0) {
                        break;
                    }
                }
                Thread.sleep(5000);
            }
            if (this._host.getCpus() <= 0) {
                throw new CloudRuntimeException("Cannot get the numbers of cpu from XenServer host " + this._host.getIp());
            }
            final Map<String, String> cpuInfo = myself.getCpuInfo(conn);
            if (cpuInfo.get("socket_count") != null) {
                this._host.setCpuSockets(Integer.parseInt(cpuInfo.get("socket_count")));
            }
            final Host.Record hr = myself.getRecord(conn);
            this._host.setProductVersion(CitrixHelper.getProductVersion(hr));

            final XsLocalNetwork privateNic = getManagementNetwork(conn);
            this._privateNetworkName = privateNic.getNetworkRecord(conn).nameLabel;
            this._host.setPrivatePif(privateNic.getPifRecord(conn).uuid);
            this._host.setPrivateNetwork(privateNic.getNetworkRecord(conn).uuid);
            this._host.setSystemvmisouuid(null);

            XsLocalNetwork guestNic = null;
            if (this._guestNetworkName != null && !this._guestNetworkName.equals(this._privateNetworkName)) {
                guestNic = getNetworkByName(conn, this._guestNetworkName);
                if (guestNic == null) {
                    s_logger.warn("Unable to find guest network " + this._guestNetworkName);
                    throw new IllegalArgumentException(
                            "Unable to find guest network " + this._guestNetworkName + " for host " + this._host.getIp());
                }
            } else {
                guestNic = privateNic;
                this._guestNetworkName = this._privateNetworkName;
            }
            this._host.setGuestNetwork(guestNic.getNetworkRecord(conn).uuid);
            this._host.setGuestPif(guestNic.getPifRecord(conn).uuid);

            XsLocalNetwork publicNic = null;
            if (this._publicNetworkName != null && !this._publicNetworkName.equals(this._guestNetworkName)) {
                publicNic = getNetworkByName(conn, this._publicNetworkName);
                if (publicNic == null) {
                    s_logger.warn("Unable to find public network " + this._publicNetworkName + " for host " + this._host.getIp());
                    throw new IllegalArgumentException(
                            "Unable to find public network " + this._publicNetworkName + " for host " + this._host.getIp());
                }
            } else {
                publicNic = guestNic;
                this._publicNetworkName = this._guestNetworkName;
            }
            this._host.setPublicPif(publicNic.getPifRecord(conn).uuid);
            this._host.setPublicNetwork(publicNic.getNetworkRecord(conn).uuid);
            if (this._storageNetworkName1 == null) {
                this._storageNetworkName1 = this._guestNetworkName;
            }
            XsLocalNetwork storageNic1 = null;
            storageNic1 = getNetworkByName(conn, this._storageNetworkName1);
            if (storageNic1 == null) {
                s_logger.warn("Unable to find storage network " + this._storageNetworkName1 + " for host " + this._host.getIp());
                throw new IllegalArgumentException(
                        "Unable to find storage network " + this._storageNetworkName1 + " for host " + this._host.getIp());
            } else {
                this._host.setStorageNetwork1(storageNic1.getNetworkRecord(conn).uuid);
                this._host.setStoragePif1(storageNic1.getPifRecord(conn).uuid);
            }

            XsLocalNetwork storageNic2 = null;
            if (this._storageNetworkName2 != null) {
                storageNic2 = getNetworkByName(conn, this._storageNetworkName2);
                if (storageNic2 != null) {
                    this._host.setStoragePif2(storageNic2.getPifRecord(conn).uuid);
                }
            }

            s_logger.info("XenServer Version is " + this._host.getProductVersion() + " for host " + this._host.getIp());
            s_logger.info("Private Network is " + this._privateNetworkName + " for host " + this._host.getIp());
            s_logger.info("Guest Network is " + this._guestNetworkName + " for host " + this._host.getIp());
            s_logger.info("Public Network is " + this._publicNetworkName + " for host " + this._host.getIp());

            return true;
        } catch (final XenAPIException e) {
            s_logger.warn("Unable to get host information for " + this._host.getIp(), e);
            return false;
        } catch (final Exception e) {
            s_logger.warn("Unable to get host information for " + this._host.getIp(), e);
            return false;
        }
    }

    protected void fillHostInfo(final Connection conn, final StartupRoutingCommand cmd) {
        final StringBuilder caps = new StringBuilder();
        try {

            final Host host = Host.getByUuid(conn, this._host.getUuid());
            final Host.Record hr = host.getRecord(conn);

            Map<String, String> details = cmd.getHostDetails();
            if (details == null) {
                details = new HashMap<>();
            }

            String productBrand = hr.softwareVersion.get("product_brand");
            if (productBrand == null) {
                productBrand = hr.softwareVersion.get("platform_name");
            }
            details.put("product_brand", productBrand);
            details.put("product_version", this._host.getProductVersion());
            if (hr.softwareVersion.get("product_version_text_short") != null) {
                details.put("product_version_text_short", hr.softwareVersion.get("product_version_text_short"));
                cmd.setHypervisorVersion(hr.softwareVersion.get("product_version_text_short"));

                cmd.setHypervisorVersion(this._host.getProductVersion());
            }
            if (this._privateNetworkName != null) {
                details.put("private.network.device", this._privateNetworkName);
            }

            cmd.setHostDetails(details);
            cmd.setName(hr.nameLabel);
            cmd.setGuid(this._host.getUuid());
            cmd.setPool(this._host.getPool());
            cmd.setDataCenter(Long.toString(this._dcId));
            for (final String cap : hr.capabilities) {
                if (cap.length() > 0) {
                    caps.append(cap).append(" , ");
                }
            }
            if (caps.length() > 0) {
                caps.delete(caps.length() - 3, caps.length());
            }
            cmd.setCaps(caps.toString());

            cmd.setCpuSockets(this._host.getCpuSockets());
            cmd.setCpus(this._host.getCpus());

            final HostMetrics hm = host.getMetrics(conn);

            long ram = 0;
            long dom0Ram = 0;
            ram = hm.getMemoryTotal(conn);
            final Set<VM> vms = host.getResidentVMs(conn);
            for (final VM vm : vms) {
                if (vm.getIsControlDomain(conn)) {
                    dom0Ram = vm.getMemoryStaticMax(conn);
                    break;
                }
            }

            ram = (long) ((ram - dom0Ram - this._xsMemoryUsed) * this._xsVirtualizationFactor);
            cmd.setMemory(ram);
            cmd.setDom0MinMemory(dom0Ram);

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Total Ram: " + ram + " dom0 Ram: " + dom0Ram);
            }

            PIF pif = PIF.getByUuid(conn, this._host.getPrivatePif());
            PIF.Record pifr = pif.getRecord(conn);
            if (pifr.IP != null && pifr.IP.length() > 0) {
                cmd.setPrivateIpAddress(pifr.IP);
                cmd.setPrivateMacAddress(pifr.MAC);
                cmd.setPrivateNetmask(pifr.netmask);
            } else {
                cmd.setPrivateIpAddress(this._host.getIp());
                cmd.setPrivateMacAddress(pifr.MAC);
                cmd.setPrivateNetmask("255.255.255.0");
            }

            pif = PIF.getByUuid(conn, this._host.getPublicPif());
            pifr = pif.getRecord(conn);
            if (pifr.IP != null && pifr.IP.length() > 0) {
                cmd.setPublicIpAddress(pifr.IP);
                cmd.setPublicMacAddress(pifr.MAC);
                cmd.setPublicNetmask(pifr.netmask);
            }

            if (this._host.getStoragePif1() != null) {
                pif = PIF.getByUuid(conn, this._host.getStoragePif1());
                pifr = pif.getRecord(conn);
                if (pifr.IP != null && pifr.IP.length() > 0) {
                    cmd.setStorageIpAddress(pifr.IP);
                    cmd.setStorageMacAddress(pifr.MAC);
                    cmd.setStorageNetmask(pifr.netmask);
                }
            }

            if (this._host.getStoragePif2() != null) {
                pif = PIF.getByUuid(conn, this._host.getStoragePif2());
                pifr = pif.getRecord(conn);
                if (pifr.IP != null && pifr.IP.length() > 0) {
                    cmd.setStorageIpAddressDeux(pifr.IP);
                    cmd.setStorageMacAddressDeux(pifr.MAC);
                    cmd.setStorageNetmaskDeux(pifr.netmask);
                }
            }

            final Map<String, String> configs = hr.otherConfig;
            cmd.setIqn(configs.get("iscsi_iqn"));

            cmd.setPod(this._pod);
            cmd.setVersion(CitrixResourceBase.class.getPackage().getImplementationVersion());
        } catch (final XmlRpcException e) {
            throw new CloudRuntimeException("XML RPC Exception" + e.getMessage(), e);
        } catch (final XenAPIException e) {
            throw new CloudRuntimeException("XenAPIException" + e.toString(), e);
        }
    }

    protected StartupStorageCommand initializeLocalSR(final Connection conn) {
        final SR lvmsr = getLocalLVMSR(conn);
        if (lvmsr != null) {
            try {
                this._host.setLocalSRuuid(lvmsr.getUuid(conn));

                final String lvmuuid = lvmsr.getUuid(conn);
                final long cap = lvmsr.getPhysicalSize(conn);
                if (cap > 0) {
                    final long avail = cap - lvmsr.getPhysicalUtilisation(conn);
                    lvmsr.setNameLabel(conn, lvmuuid);
                    final String name = "Cloud Stack Local LVM Storage Pool for " + this._host.getUuid();
                    lvmsr.setNameDescription(conn, name);
                    final Host host = Host.getByUuid(conn, this._host.getUuid());
                    final String address = host.getAddress(conn);
                    final StoragePoolInfo pInfo = new StoragePoolInfo(lvmuuid, address, SRType.LVM.toString(),
                            SRType.LVM.toString(), StoragePoolType.LVM, cap, avail);
                    final StartupStorageCommand cmd = new StartupStorageCommand();
                    cmd.setPoolInfo(pInfo);
                    cmd.setGuid(this._host.getUuid());
                    cmd.setDataCenter(Long.toString(this._dcId));
                    cmd.setResourceType(StorageResourceType.STORAGE_POOL);
                    return cmd;
                }
            } catch (final XenAPIException e) {
                final String msg = "build local LVM info err in host:" + this._host.getUuid() + e.toString();
                s_logger.warn(msg);
            } catch (final XmlRpcException e) {
                final String msg = "build local LVM info err in host:" + this._host.getUuid() + e.getMessage();
                s_logger.warn(msg);
            }
        }

        final SR extsr = getLocalEXTSR(conn);
        if (extsr != null) {
            try {
                final String extuuid = extsr.getUuid(conn);
                this._host.setLocalSRuuid(extuuid);
                final long cap = extsr.getPhysicalSize(conn);
                if (cap > 0) {
                    final long avail = cap - extsr.getPhysicalUtilisation(conn);
                    extsr.setNameLabel(conn, extuuid);
                    final String name = "Cloud Stack Local EXT Storage Pool for " + this._host.getUuid();
                    extsr.setNameDescription(conn, name);
                    final Host host = Host.getByUuid(conn, this._host.getUuid());
                    final String address = host.getAddress(conn);
                    final StoragePoolInfo pInfo = new StoragePoolInfo(extuuid, address, SRType.EXT.toString(),
                            SRType.EXT.toString(), StoragePoolType.EXT, cap, avail);
                    final StartupStorageCommand cmd = new StartupStorageCommand();
                    cmd.setPoolInfo(pInfo);
                    cmd.setGuid(this._host.getUuid());
                    cmd.setDataCenter(Long.toString(this._dcId));
                    cmd.setResourceType(StorageResourceType.STORAGE_POOL);
                    return cmd;
                }
            } catch (final XenAPIException e) {
                final String msg = "build local EXT info err in host:" + this._host.getUuid() + e.toString();
                s_logger.warn(msg);
            } catch (final XmlRpcException e) {
                final String msg = "build local EXT info err in host:" + this._host.getUuid() + e.getMessage();
                s_logger.warn(msg);
            }
        }
        return null;
    }

    protected XsLocalNetwork getManagementNetwork(final Connection conn) throws XmlRpcException, XenAPIException {
        PIF mgmtPif = null;
        PIF.Record mgmtPifRec = null;
        final Host host = Host.getByUuid(conn, this._host.getUuid());
        final Set<PIF> hostPifs = host.getPIFs(conn);
        for (final PIF pif : hostPifs) {
            final PIF.Record rec = pif.getRecord(conn);
            if (rec.management) {
                if (rec.VLAN != null && rec.VLAN != -1) {
                    final String msg = new StringBuilder(
                            "Unsupported configuration.  Management network is on a VLAN.  host=").append(this._host.getUuid()).append(
                            "; pif=").append(rec.uuid).append("; vlan=").append(rec.VLAN).toString();
                    s_logger.warn(msg);
                    throw new CloudRuntimeException(msg);
                }
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Management network is on pif=" + rec.uuid);
                }
                mgmtPif = pif;
                mgmtPifRec = rec;
                break;
            }
        }
        if (mgmtPif == null) {
            final String msg = "Unable to find management network for " + this._host.getUuid();
            s_logger.warn(msg);
            throw new CloudRuntimeException(msg);
        }
        final Bond bond = mgmtPifRec.bondSlaveOf;
        if (!isRefNull(bond)) {
            final String msg = "Management interface is on slave(" + mgmtPifRec.uuid + ") of bond(" + bond.getUuid(conn)
                    + ") on host(" + this._host.getUuid()
                    + "), please move management interface to bond!";
            s_logger.warn(msg);
            throw new CloudRuntimeException(msg);
        }
        final Network nk = mgmtPifRec.network;
        final Network.Record nkRec = nk.getRecord(conn);
        return new XsLocalNetwork(this, nk, nkRec, mgmtPif, mgmtPifRec);
    }

    /**
     * getNetworkByName() retrieves what the server thinks is the actual network used by the XenServer host. This method
     * should always be used to talk to retrieve a network by the name. The reason is because of the problems in using the
     * name label as the way to find the Network.
     * <p>
     * To see how we are working around these problems, take a look at enableVlanNetwork(). The following description
     * assumes you have looked at the description on that method.
     * <p>
     * In order to understand this, we have to see what type of networks are within a XenServer that's under CloudStack
     * control.
     * <p>
     * - Native Networks: these are networks that are untagged on the XenServer and are used to crate VLAN networks on.
     * These are created by the user and is assumed to be one per cluster. - VLAN Networks: these are dynamically created
     * by CloudStack and can have problems with duplicated names. - LinkLocal Networks: these are dynamically created by
     * CloudStack and can also have problems with duplicated names but these don't have actual PIFs.
     * <p>
     * In order to speed to retrieval of a network, we do the following: - We retrieve by the name. If only one network is
     * retrieved, we assume we retrieved the right network. - If more than one network is retrieved, we check to see which
     * one has the pif for the local host and use that. - If a pif is not found, then we look at the tags and find the one
     * with the lowest timestamp. (See enableVlanNetwork())
     *
     * @param conn Xapi connection
     * @param name name of the network
     * @return XsNic an object that contains network, network record, pif, and pif record.
     * @throws XenAPIException
     * @throws XmlRpcException
     * @see CitrixResourceBase#enableVlanNetwork
     */
    public XsLocalNetwork getNetworkByName(final Connection conn, final String name)
            throws XenAPIException, XmlRpcException {
        final Set<Network> networks = Network.getByNameLabel(conn, name);
        if (networks.size() == 1) {
            return new XsLocalNetwork(this, networks.iterator().next(), null, null, null);
        }

        if (networks.size() == 0) {
            return null;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Found more than one network with the name " + name);
        }
        Network earliestNetwork = null;
        Network.Record earliestNetworkRecord = null;
        long earliestTimestamp = Long.MAX_VALUE;
        int earliestRandom = Integer.MAX_VALUE;
        for (final Network network : networks) {
            final XsLocalNetwork nic = new XsLocalNetwork(this, network);

            if (nic.getPif(conn) != null) {
                return nic;
            }

            final Network.Record record = network.getRecord(conn);
            if (record.tags != null) {
                for (final String tag : record.tags) {
                    final Pair<Long, Integer> stamp = parseTimestamp(tag);
                    if (stamp == null) {
                        continue;
                    }

                    if (stamp.first() < earliestTimestamp
                            || stamp.first() == earliestTimestamp && stamp.second() < earliestRandom) {
                        earliestTimestamp = stamp.first();
                        earliestRandom = stamp.second();
                        earliestNetwork = network;
                        earliestNetworkRecord = record;
                    }
                }
            }
        }

        return earliestNetwork != null ? new XsLocalNetwork(this, earliestNetwork, earliestNetworkRecord, null, null)
                : null;
    }

    protected SR getLocalLVMSR(final Connection conn) {
        try {
            final Map<SR, SR.Record> map = SR.getAllRecords(conn);
            if (map != null && !map.isEmpty()) {
                for (final Map.Entry<SR, SR.Record> entry : map.entrySet()) {
                    final SR.Record srRec = entry.getValue();
                    if (SRType.LVM.equals(srRec.type)) {
                        final Set<PBD> pbds = srRec.PBDs;
                        if (pbds == null) {
                            continue;
                        }
                        for (final PBD pbd : pbds) {
                            final Host host = pbd.getHost(conn);
                            if (!isRefNull(host) && host.getUuid(conn).equals(this._host.getUuid())) {
                                if (!pbd.getCurrentlyAttached(conn)) {
                                    pbd.plug(conn);
                                }
                                final SR sr = entry.getKey();
                                sr.scan(conn);
                                return sr;
                            }
                        }
                    }
                }
            }
        } catch (final XenAPIException e) {
            final String msg = "Unable to get local LVMSR in host:" + this._host.getUuid() + e.toString();
            s_logger.warn(msg);
        } catch (final XmlRpcException e) {
            final String msg = "Unable to get local LVMSR in host:" + this._host.getUuid() + e.getCause();
            s_logger.warn(msg);
        }
        return null;
    }

    protected SR getLocalEXTSR(final Connection conn) {
        try {
            final Map<SR, SR.Record> map = SR.getAllRecords(conn);
            if (map != null && !map.isEmpty()) {
                for (final Map.Entry<SR, SR.Record> entry : map.entrySet()) {
                    final SR.Record srRec = entry.getValue();
                    if (SRType.FILE.equals(srRec.type) || SRType.EXT.equals(srRec.type)) {
                        final Set<PBD> pbds = srRec.PBDs;
                        if (pbds == null) {
                            continue;
                        }
                        for (final PBD pbd : pbds) {
                            final Host host = pbd.getHost(conn);
                            if (!isRefNull(host) && host.getUuid(conn).equals(this._host.getUuid())) {
                                if (!pbd.getCurrentlyAttached(conn)) {
                                    pbd.plug(conn);
                                }
                                final SR sr = entry.getKey();
                                sr.scan(conn);
                                return sr;
                            }
                        }
                    }
                }
            }
        } catch (final XenAPIException e) {
            final String msg = "Unable to get local EXTSR in host:" + this._host.getUuid() + e.toString();
            s_logger.warn(msg);
        } catch (final XmlRpcException e) {
            final String msg = "Unable to get local EXTSR in host:" + this._host.getUuid() + e.getCause();
            s_logger.warn(msg);
        }
        return null;
    }

    protected Pair<Long, Integer> parseTimestamp(final String timeStampStr) {
        final String[] tokens = timeStampStr.split("-");
        if (tokens.length != 3) {
            s_logger.debug("timeStamp in network has wrong pattern: " + timeStampStr);
            return null;
        }
        if (!tokens[0].equals("CsCreateTime")) {
            s_logger.debug("timeStamp in network doesn't start with CsCreateTime: " + timeStampStr);
            return null;
        }
        return new Pair<>(Long.parseLong(tokens[1]), Integer.parseInt(tokens[2]));
    }

    public String getVhdParent(final Connection conn, final String primaryStorageSRUuid, final String snapshotUuid,
                               final Boolean isISCSI) {
        final String parentUuid = callHostPlugin(conn, "vmopsSnapshot", "getVhdParent", "primaryStorageSRUuid",
                primaryStorageSRUuid, "snapshotUuid", snapshotUuid, "isISCSI",
                isISCSI.toString());

        if (parentUuid == null || parentUuid.isEmpty() || parentUuid.equalsIgnoreCase("None")) {
            s_logger.debug("Unable to get parent of VHD " + snapshotUuid + " in SR " + primaryStorageSRUuid);
            // errString is already logged.
            return null;
        }
        return parentUuid;
    }

    public VIF getVifByMac(final Connection conn, final VM router, String mac) throws XmlRpcException, XenAPIException {
        final Set<VIF> routerVIFs = router.getVIFs(conn);
        mac = mac.trim();
        for (final VIF vif : routerVIFs) {
            final String lmac = vif.getMAC(conn);
            if (lmac.trim().equals(mac)) {
                return vif;
            }
        }
        return null;
    }

    public VirtualRoutingResource getVirtualRoutingResource() {
        return this._vrResource;
    }

    public String getVMInstanceName() {
        return this._instance;
    }

    public long getVMSnapshotChainSize(final Connection conn, final VolumeObjectTO volumeTo, final String vmName)
            throws BadServerResponse, XenAPIException, XmlRpcException {
        if (volumeTo.getVolumeType() == VolumeType.DATADISK) {
            final VDI dataDisk = VDI.getByUuid(conn, volumeTo.getPath());
            if (dataDisk != null) {
                final String dataDiskName = dataDisk.getNameLabel(conn);
                if (dataDiskName != null && !dataDiskName.isEmpty()) {
                    volumeTo.setName(dataDiskName);
                }
            }
        }
        final Set<VDI> allvolumeVDIs = VDI.getByNameLabel(conn, volumeTo.getName());
        long size = 0;
        for (final VDI vdi : allvolumeVDIs) {
            try {
                if (vdi.getIsASnapshot(conn) && vdi.getSmConfig(conn).get("vhd-parent") != null) {
                    final String parentUuid = vdi.getSmConfig(conn).get("vhd-parent");
                    final VDI parentVDI = VDI.getByUuid(conn, parentUuid);
                    // add size of snapshot vdi node, usually this only contains
                    // meta data
                    size = size + vdi.getPhysicalUtilisation(conn);
                    // add size of snapshot vdi parent, this contains data
                    if (!isRefNull(parentVDI)) {
                        size = size + parentVDI.getPhysicalUtilisation(conn).longValue();
                    }
                }
            } catch (final Exception e) {
                s_logger.debug("Exception occurs when calculate snapshot capacity for volumes: due to " + e.toString());
                continue;
            }
        }
        if (volumeTo.getVolumeType() == VolumeType.ROOT) {
            final Map<VM, VM.Record> allVMs = VM.getAllRecords(conn);
            // add size of memory snapshot vdi
            if (allVMs != null && allVMs.size() > 0) {
                for (final VM vmr : allVMs.keySet()) {
                    try {
                        final String vName = vmr.getNameLabel(conn);
                        if (vName != null && vName.contains(vmName) && vmr.getIsASnapshot(conn)) {
                            final VDI memoryVDI = vmr.getSuspendVDI(conn);
                            if (!isRefNull(memoryVDI)) {
                                size = size + memoryVDI.getPhysicalUtilisation(conn);
                                final VDI pMemoryVDI = memoryVDI.getParent(conn);
                                if (!isRefNull(pMemoryVDI)) {
                                    size = size + pMemoryVDI.getPhysicalUtilisation(conn);
                                }
                            }
                        }
                    } catch (final Exception e) {
                        s_logger.debug("Exception occurs when calculate snapshot capacity for memory: due to " + e.toString());
                        continue;
                    }
                }
            }
        }
        return size;
    }

    public boolean isRefNull(final XenAPIObject object) {
        return object == null || object.toWireString().equals("OpaqueRef:NULL")
                || object.toWireString().equals("<not in database>");
    }

    public PowerState getVmState(final Connection conn, final String vmName) {
        int retry = 3;
        while (retry-- > 0) {
            try {
                final Set<VM> vms = VM.getByNameLabel(conn, vmName);
                for (final VM vm : vms) {
                    return convertToPowerState(vm.getPowerState(conn));
                }
            } catch (final BadServerResponse e) {
                // There is a race condition within xenserver such that if a vm
                // is
                // deleted and we
                // happen to ask for it, it throws this stupid response. So
                // if this happens,
                // we take a nap and try again which then avoids the race
                // condition because
                // the vm's information is now cleaned up by xenserver. The
                // error
                // is as follows
                // com.xensource.xenapi.Types$BadServerResponse
                // [HANDLE_INVALID, VM,
                // 3dde93f9-c1df-55a7-2cde-55e1dce431ab]
                s_logger.info("Unable to get a vm PowerState due to " + e.toString() + ". We are retrying.  Count: " + retry);
                try {
                    Thread.sleep(3000);
                } catch (final InterruptedException ex) {

                }
            } catch (final XenAPIException e) {
                final String msg = "Unable to get a vm PowerState due to " + e.toString();
                s_logger.warn(msg, e);
                break;
            } catch (final XmlRpcException e) {
                final String msg = "Unable to get a vm PowerState due to " + e.getMessage();
                s_logger.warn(msg, e);
                break;
            }
        }

        return PowerState.PowerOff;
    }

    private static PowerState convertToPowerState(final VmPowerState ps) {
        final PowerState powerState = s_powerStatesTable.get(ps);
        return powerState == null ? PowerState.PowerUnknown : powerState;
    }

    public HashMap<String, VmStatsEntry> getVmStats(final Connection conn, final GetVmStatsCommand cmd,
                                                    final List<String> vmUUIDs, final String hostGuid) {
        final HashMap<String, VmStatsEntry> vmResponseMap = new HashMap<>();

        for (final String vmUUID : vmUUIDs) {
            vmResponseMap.put(vmUUID, new VmStatsEntry(0, 0, 0, 0, "vm"));
        }

        final Object[] rrdData = getRRDData(conn, 2); // call rrddata with 2 for
        // vm

        if (rrdData == null) {
            return null;
        }

        final Integer numRows = (Integer) rrdData[0];
        final Integer numColumns = (Integer) rrdData[1];
        final Node legend = (Node) rrdData[2];
        final Node dataNode = (Node) rrdData[3];

        final NodeList legendChildren = legend.getChildNodes();
        for (int col = 0; col < numColumns; col++) {

            if (legendChildren == null || legendChildren.item(col) == null) {
                continue;
            }

            final String columnMetadata = getXMLNodeValue(legendChildren.item(col));

            if (columnMetadata == null) {
                continue;
            }

            final String[] columnMetadataList = columnMetadata.split(":");

            if (columnMetadataList.length != 4) {
                continue;
            }

            final String type = columnMetadataList[1];
            final String uuid = columnMetadataList[2];
            final String param = columnMetadataList[3];

            if (type.equals("vm") && vmResponseMap.keySet().contains(uuid)) {
                final VmStatsEntry vmStatsAnswer = vmResponseMap.get(uuid);

                vmStatsAnswer.setEntityType("vm");

                if (param.contains("cpu")) {
                    vmStatsAnswer.setNumCPUs(vmStatsAnswer.getNumCPUs() + 1);
                    vmStatsAnswer.setCPUUtilization(vmStatsAnswer.getCPUUtilization() + getDataAverage(dataNode, col, numRows));
                } else if (param.matches("vif_\\d*_rx")) {
                    vmStatsAnswer.setNetworkReadKBs(
                            vmStatsAnswer.getNetworkReadKBs() + getDataAverage(dataNode, col, numRows) / 1000);
                } else if (param.matches("vif_\\d*_tx")) {
                    vmStatsAnswer.setNetworkWriteKBs(
                            vmStatsAnswer.getNetworkWriteKBs() + getDataAverage(dataNode, col, numRows) / 1000);
                } else if (param.matches("vbd_.*_read")) {
                    vmStatsAnswer.setDiskReadKBs(vmStatsAnswer.getDiskReadKBs() + getDataAverage(dataNode, col, numRows) / 1000);
                } else if (param.matches("vbd_.*_write")) {
                    vmStatsAnswer.setDiskWriteKBs(
                            vmStatsAnswer.getDiskWriteKBs() + getDataAverage(dataNode, col, numRows) / 1000);
                }
            }
        }

        for (final Map.Entry<String, VmStatsEntry> entry : vmResponseMap.entrySet()) {
            final VmStatsEntry vmStatsAnswer = entry.getValue();

            if (vmStatsAnswer.getNumCPUs() != 0) {
                vmStatsAnswer.setCPUUtilization(vmStatsAnswer.getCPUUtilization() / vmStatsAnswer.getNumCPUs());
            }

            vmStatsAnswer.setCPUUtilization(vmStatsAnswer.getCPUUtilization() * 100);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Vm cpu utilization " + vmStatsAnswer.getCPUUtilization());
            }
        }
        return vmResponseMap;
    }

    public String getVncUrl(final Connection conn, final VM vm) {
        final VM.Record record;
        Console c;
        try {
            record = vm.getRecord(conn);
            final Set<Console> consoles = record.consoles;

            if (consoles.isEmpty()) {
                s_logger.warn("There are no Consoles available to the vm : " + record.nameDescription);
                return null;
            }
            final Iterator<Console> i = consoles.iterator();
            while (i.hasNext()) {
                c = i.next();
                if (c.getProtocol(conn) == Types.ConsoleProtocol.RFB) {
                    return c.getLocation(conn);
                }
            }
        } catch (final XenAPIException e) {
            final String msg = "Unable to get console url due to " + e.toString();
            s_logger.warn(msg, e);
            return null;
        } catch (final XmlRpcException e) {
            final String msg = "Unable to get console url due to " + e.getMessage();
            s_logger.warn(msg, e);
            return null;
        }
        return null;
    }

    public void handleSrAndVdiDetach(final String iqn, final Connection conn) throws Exception {
        final SR sr = getStorageRepository(conn, iqn);

        removeSR(conn, sr);
    }

    public SR getStorageRepository(final Connection conn, final String srNameLabel) {
        final Set<SR> srs;
        try {
            srs = SR.getByNameLabel(conn, srNameLabel);
        } catch (final XenAPIException e) {
            throw new CloudRuntimeException("Unable to get SR " + srNameLabel + " due to " + e.toString(), e);
        } catch (final Exception e) {
            throw new CloudRuntimeException("Unable to get SR " + srNameLabel + " due to " + e.getMessage(), e);
        }

        if (srs.size() > 1) {
            throw new CloudRuntimeException("More than one storage repository was found for pool with uuid: " + srNameLabel);
        } else if (srs.size() == 1) {
            final SR sr = srs.iterator().next();
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("SR retrieved for " + srNameLabel);
            }

            if (checkSR(conn, sr)) {
                return sr;
            }
            throw new CloudRuntimeException(
                    "SR check failed for storage pool: " + srNameLabel + "on host:" + this._host.getUuid());
        } else {
            throw new CloudRuntimeException("Can not see storage pool: " + srNameLabel + " from on host:" + this._host.getUuid());
        }
    }

    protected void destroyUnattachedVBD(final Connection conn, final VM vm) {
        try {
            for (final VBD vbd : vm.getVBDs(conn)) {
                if (Types.VbdType.DISK.equals(vbd.getType(conn)) && !vbd.getCurrentlyAttached(conn)) {
                    vbd.destroy(conn);
                }
            }
        } catch (final Exception e) {
            s_logger.debug("Failed to destroy unattached VBD due to ", e);
        }
    }

    public String handleVmStartFailure(final Connection conn, final String vmName, final VM vm, final String message,
                                       final Throwable th) {
        final String msg = "Unable to start " + vmName + " due to " + message;
        s_logger.warn(msg, th);

        if (vm == null) {
            return msg;
        }

        try {
            final VM.Record vmr = vm.getRecord(conn);
            final List<Network> networks = new ArrayList<>();
            for (final VIF vif : vmr.VIFs) {
                try {
                    final VIF.Record rec = vif.getRecord(conn);
                    if (rec != null) {
                        networks.add(rec.network);
                    } else {
                        s_logger.warn("Unable to cleanup VIF: " + vif.toWireString() + " As vif record is null");
                    }
                } catch (final Exception e) {
                    s_logger.warn("Unable to cleanup VIF", e);
                }
            }
            if (vmr.powerState == VmPowerState.RUNNING) {
                try {
                    vm.hardShutdown(conn);
                } catch (final Exception e) {
                    s_logger.warn("VM hardshutdown failed due to ", e);
                }
            }
            if (vm.getPowerState(conn) == VmPowerState.HALTED) {
                try {
                    vm.destroy(conn);
                } catch (final Exception e) {
                    s_logger.warn("VM destroy failed due to ", e);
                }
            }
            for (final VBD vbd : vmr.VBDs) {
                try {
                    vbd.unplug(conn);
                    vbd.destroy(conn);
                } catch (final Exception e) {
                    s_logger.warn("Unable to clean up VBD due to ", e);
                }
            }
            for (final VIF vif : vmr.VIFs) {
                try {
                    vif.unplug(conn);
                    vif.destroy(conn);
                } catch (final Exception e) {
                    s_logger.warn("Unable to cleanup VIF", e);
                }
            }
            for (final Network network : networks) {
                if (network.getNameLabel(conn).startsWith("VLAN")) {
                    disableVlanNetwork(conn, network);
                }
            }
        } catch (final Exception e) {
            s_logger.warn("VM getRecord failed due to ", e);
        }

        return msg;
    }

    public void disableVlanNetwork(final Connection conn, final Network network) {
    }

    public boolean IsISCSI(final String type) {
        return SRType.LVMOHBA.equals(type) || SRType.LVMOISCSI.equals(type) || SRType.LVM.equals(type);
    }

    public boolean isNetworkSetupByName(final String nameTag) throws XenAPIException, XmlRpcException {
        if (nameTag != null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Looking for network setup by name " + nameTag);
            }
            final Connection conn = getConnection();
            final XsLocalNetwork network = getNetworkByName(conn, nameTag);
            if (network == null) {
                return false;
            }
        }
        return true;
    }

    public boolean isXcp() {
        final Connection conn = getConnection();
        final String result = callHostPlugin(conn, "ovstunnel", "is_xcp");
        if (result.equals("XCP")) {
            return true;
        }
        return false;
    }

    public boolean launchHeartBeat(final Connection conn) {
        final String result = callHostPluginPremium(conn, "heartbeat", "host", this._host.getUuid(), "timeout",
                Integer.toString(this._heartbeatTimeout), "interval",
                Integer.toString(this._heartbeatInterval));
        if (result == null || !result.contains("> DONE <")) {
            s_logger.warn("Unable to launch the heartbeat process on " + this._host.getIp());
            return false;
        }
        return true;
    }

    public String callHostPluginPremium(final Connection conn, final String cmd, final String... params) {
        return callHostPlugin(conn, "vmopspremium", cmd, params);
    }

    public void migrateVM(final Connection conn, final Host destHost, final VM vm, final String vmName) throws Exception {
        Task task = null;
        try {
            final Map<String, String> other = new HashMap<>();
            other.put("live", "true");
            task = vm.poolMigrateAsync(conn, destHost, other);
            try {
                // poll every 1 seconds
                final long timeout = this._migratewait * 1000L;
                waitForTask(conn, task, 1000, timeout);
                checkForSuccess(conn, task);
            } catch (final Types.HandleInvalid e) {
                if (vm.getResidentOn(conn).equals(destHost)) {
                    task = null;
                    return;
                }
                throw new CloudRuntimeException("migrate VM catch HandleInvalid and VM is not running on dest host");
            }
        } catch (final XenAPIException e) {
            final String msg = "Unable to migrate VM(" + vmName + ") from host(" + this._host.getUuid() + ")";
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg);
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e1) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e1.toString());
                }
            }
        }
    }

    protected VDI mount(final Connection conn, final StoragePoolType poolType, final String volumeFolder,
                        final String volumePath) {
        return getVDIbyUuid(conn, volumePath);
    }

    public VDI getVDIbyUuid(final Connection conn, final String uuid) {
        return getVDIbyUuid(conn, uuid, true);
    }

    public VDI getVDIbyUuid(final Connection conn, final String uuid, final boolean throwExceptionIfNotFound) {
        try {
            return VDI.getByUuid(conn, uuid);
        } catch (final Exception e) {
            if (throwExceptionIfNotFound) {
                final String msg = "Catch Exception " + e.getClass().getName() + " :VDI getByUuid for uuid: " + uuid
                        + " failed due to " + e.toString();

                s_logger.debug(msg);

                throw new CloudRuntimeException(msg, e);
            }

            return null;
        }
    }

    public boolean pingXAPI() {
        final Connection conn = getConnection();
        try {
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            if (!host.getEnabled(conn)) {
                s_logger.debug("Host " + this._host.getIp() + " is not enabled!");
                return false;
            }
        } catch (final Exception e) {
            s_logger.debug("cannot get host enabled status, host " + this._host.getIp() + " due to " + e.toString(), e);
            return false;
        }
        try {
            callHostPlugin(conn, "echo", "main");
        } catch (final Exception e) {
            s_logger.debug("cannot ping host " + this._host.getIp() + " due to " + e.toString(), e);
            return false;
        }
        return true;
    }

    protected void plugDom0Vif(final Connection conn, final VIF dom0Vif) throws XmlRpcException, XenAPIException {
        if (dom0Vif != null) {
            dom0Vif.plug(conn);
        }
    }

    protected boolean postCreatePrivateTemplate(final Connection conn, final String templatePath,
                                                final String tmpltFilename, final String templateName,
                                                String templateDescription, String checksum, final long size, final long virtualSize, final long templateId) {

        if (templateDescription == null) {
            templateDescription = "";
        }

        if (checksum == null) {
            checksum = "";
        }

        final String result = callHostPlugin(conn, "vmopsSnapshot", "post_create_private_template", "templatePath",
                templatePath, "templateFilename", tmpltFilename,
                "templateName", templateName, "templateDescription", templateDescription, "checksum", checksum, "size",
                String.valueOf(size), "virtualSize",
                String.valueOf(virtualSize), "templateId", String.valueOf(templateId));

        boolean success = false;
        if (result != null && !result.isEmpty()) {
            // Else, command threw an exception which has already been logged.

            if (result.equalsIgnoreCase("1")) {
                s_logger.debug("Successfully created template.properties file on secondary storage for " + tmpltFilename);
                success = true;
            } else {
                s_logger.warn("Could not create template.properties file on secondary storage for " + tmpltFilename
                        + " for templateId: " + templateId);
            }
        }

        return success;
    }

    public void prepareISO(final Connection conn, final String vmName, final List<String[]> vmDataList, final String configDriveLabel)
            throws XmlRpcException, XenAPIException {

        final Set<VM> vms = VM.getByNameLabel(conn, vmName);
        if (vms == null || vms.size() != 1) {
            throw new CloudRuntimeException("There are " + (vms == null ? "0" : vms.size()) + " VMs named " + vmName);
        }
        final VM vm = vms.iterator().next();

        if (vmDataList != null) {
            // create SR
            final SR sr = createLocalIsoSR(conn, this._configDriveSRName + getHost().getIp());

            // 1. create vm data files
            createVmdataFiles(vmName, vmDataList, configDriveLabel);

            // 2. copy config drive iso to host
            copyConfigDriveIsoToHost(conn, sr, vmName);
        }

        final Set<VBD> vbds = vm.getVBDs(conn);
        for (final VBD vbd : vbds) {
            final VBD.Record vbdr = vbd.getRecord(conn);
            if (vbdr.type == Types.VbdType.CD && vbdr.empty == false && vbdr.userdevice.equals(this._attachIsoDeviceNum)) {
                final VDI vdi = vbdr.VDI;
                final SR sr = vdi.getSR(conn);
                final Set<PBD> pbds = sr.getPBDs(conn);
                if (pbds == null) {
                    throw new CloudRuntimeException("There is no pbd for sr " + sr);
                }
                for (final PBD pbd : pbds) {
                    final PBD.Record pbdr = pbd.getRecord(conn);
                    if (pbdr.host.getUuid(conn).equals(this._host.getUuid())) {
                        return;
                    }
                }
                sr.setShared(conn, true);
                final Host host = Host.getByUuid(conn, this._host.getUuid());
                final PBD.Record pbdr = pbds.iterator().next().getRecord(conn);
                pbdr.host = host;
                pbdr.uuid = "";
                final PBD pbd = PBD.create(conn, pbdr);
                pbdPlug(conn, pbd, pbd.getUuid(conn));
                break;
            }
        }
    }

    public SR createLocalIsoSR(final Connection conn, final String srName) throws XenAPIException, XmlRpcException {

        // if config drive sr already exists then return
        SR sr = getSRByNameLabelandHost(conn, this._configDriveSRName + this._host.getIp());

        if (sr != null) {
            s_logger.debug("Config drive SR already exist, returing it");
            return sr;
        }

        try {
            final Map<String, String> deviceConfig = new HashMap<>();

            final com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(this._host.getIp(), 22);
            try {
                sshConnection.connect(null, 60000, 60000);
                if (!sshConnection.authenticateWithPassword(this._username, this._password.peek())) {
                    throw new CloudRuntimeException("Unable to authenticate");
                }

                final String cmd = "mkdir -p " + this._configDriveIsopath;
                if (!SSHCmdHelper.sshExecuteCmd(sshConnection, cmd)) {
                    throw new CloudRuntimeException("Cannot create directory configdrive_iso on XenServer hosts");
                }
            } catch (final IOException e) {
                throw new CloudRuntimeException("Unable to create iso folder", e);
            } finally {
                sshConnection.close();
            }
            s_logger.debug("Created the config drive SR " + srName + " folder path " + this._configDriveIsopath);

            deviceConfig.put("location", this._configDriveIsopath);
            deviceConfig.put("legacy_mode", "true");
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            final String type = SRType.ISO.toString();
            sr = SR.create(conn, host, deviceConfig, new Long(0), this._configDriveIsopath, "iso", type, "iso", false,
                    new HashMap<>());

            sr.setNameLabel(conn, srName);
            sr.setNameDescription(conn, deviceConfig.get("location"));

            sr.scan(conn);
            s_logger.debug("Config drive ISO SR at the path " + this._configDriveIsopath + " got created in host " + this._host);
            return sr;
        } catch (final XenAPIException e) {
            final String msg = "createLocalIsoSR failed! mountpoint " + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        } catch (final Exception e) {
            final String msg = "createLocalIsoSR failed! mountpoint:  due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg, e);
        }
    }

    public XsHost getHost() {
        return this._host;
    }

    public boolean createVmdataFiles(final String vmName, final List<String[]> vmDataList,
                                     final String configDriveLabel) {

        // add vm iso to the isolibrary
        final String isoPath = "/tmp/" + vmName + "/configDrive/";
        final String configDriveName = "cloudstack/";

        // create folder for the VM
        // Remove the folder before creating it.

        try {
            deleteLocalFolder("/tmp/" + isoPath);
        } catch (final IOException e) {
            s_logger.debug("Failed to delete the exiting config drive for vm " + vmName + " " + e.getMessage());
        } catch (final Exception e) {
            s_logger.debug("Failed to delete the exiting config drive for vm " + vmName + " " + e.getMessage());
        }

        if (vmDataList != null) {
            for (final String[] item : vmDataList) {
                final String dataType = item[0];
                final String fileName = item[1];
                final String content = item[2];

                // create file with content in folder
                if (dataType != null && !dataType.isEmpty()) {
                    // create folder
                    final String folder = isoPath + configDriveName + dataType;
                    if (folder != null && !folder.isEmpty()) {
                        final File dir = new File(folder);
                        final boolean result = true;

                        try {
                            if (!dir.exists()) {
                                dir.mkdirs();
                            }
                        } catch (final SecurityException ex) {
                            s_logger.debug("Failed to create dir " + ex.getMessage());
                            return false;
                        }

                        if (result && content != null && !content.isEmpty()) {
                            final File file = new File(folder + "/" + fileName + ".txt");
                            try (
                                    final OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(file.getAbsoluteFile()), "UTF-8");
                                    final BufferedWriter bw = new BufferedWriter(fw)) {
                                bw.write(content);
                                s_logger.debug("created file: " + file + " in folder:" + folder);
                            } catch (final IOException ex) {
                                s_logger.debug("Failed to create file " + ex.getMessage());
                                return false;
                            }
                        }
                    }
                }
            }
            s_logger.debug("Created the vm data in " + isoPath);
        }

        String s = null;
        try {

            final String cmd = "mkisofs -iso-level 3 -V " + configDriveLabel + " -o " + isoPath + vmName + ".iso " + isoPath;
            final Process p = Runtime.getRuntime().exec(cmd);

            final BufferedReader stdInput = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), Charset.defaultCharset()));

            final BufferedReader stdError = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), Charset.defaultCharset()));

            // read the output from the command
            while ((s = stdInput.readLine()) != null) {
                s_logger.debug(s);
            }

            // read any errors from the attempted command
            while ((s = stdError.readLine()) != null) {
                s_logger.debug(s);
            }
            s_logger.debug(" Created config drive ISO using the command " + cmd + " in the host " + this._host.getIp());
        } catch (final IOException e) {
            s_logger.debug(e.getMessage());
            return false;
        }

        return true;
    }

    public boolean copyConfigDriveIsoToHost(final Connection conn, final SR sr, final String vmName) {

        final String vmIso = "/tmp/" + vmName + "/configDrive/" + vmName + ".iso";
        // scp file into the host
        final com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(this._host.getIp(), 22);

        try {
            sshConnection.connect(null, 60000, 60000);
            if (!sshConnection.authenticateWithPassword(this._username, this._password.peek())) {
                throw new CloudRuntimeException("Unable to authenticate");
            }

            s_logger.debug(
                    "scp config drive iso file " + vmIso + " to host " + this._host.getIp() + " path " + this._configDriveIsopath);
            final SCPClient scp = new SCPClient(sshConnection);
            final String p = "0755";

            scp.put(vmIso, this._configDriveIsopath, p);
            sr.scan(conn);
            s_logger.debug("copied config drive iso to host " + this._host);
        } catch (final IOException e) {
            s_logger.debug("failed to copy configdrive iso " + vmIso + " to host " + this._host, e);
            return false;
        } catch (final XmlRpcException e) {
            s_logger.debug("Failed to scan config drive iso SR " + this._configDriveSRName + this._host.getIp() + " in host " + this._host, e);
            return false;
        } finally {
            sshConnection.close();
            // clean up the config drive files

            final String configDir = "/tmp/" + vmName;
            try {
                deleteLocalFolder(configDir);
                s_logger.debug("Successfully cleaned up config drive directory " + configDir
                        + " after copying it to host ");
            } catch (final Exception e) {
                s_logger.debug("Failed to delete config drive folder :" + configDir + " for VM " + vmName + " "
                        + e.getMessage());
            }
        }

        return true;
    }

    protected SR getSRByNameLabelandHost(final Connection conn, final String name)
            throws BadServerResponse, XenAPIException, XmlRpcException {
        final Set<SR> srs = SR.getByNameLabel(conn, name);
        SR ressr = null;
        for (final SR sr : srs) {
            final Set<PBD> pbds;
            pbds = sr.getPBDs(conn);
            for (final PBD pbd : pbds) {
                final PBD.Record pbdr = pbd.getRecord(conn);
                if (pbdr.host != null && pbdr.host.getUuid(conn).equals(this._host.getUuid())) {
                    if (!pbdr.currentlyAttached) {
                        pbd.plug(conn);
                    }
                    ressr = sr;
                    break;
                }
            }
        }
        return ressr;
    }

    public void deleteLocalFolder(final String directory) throws Exception {
        if (directory == null || directory.isEmpty()) {
            final String msg = "Invalid directory path (null/empty) detected. Cannot delete specified directory.";
            s_logger.debug(msg);
            throw new Exception(msg);
        }

        try {
            FileUtils.deleteDirectory(new File(directory));
        } catch (final IOException e) {
            // IOException here means failure to delete. Not swallowing it here to
            // let the caller handle with appropriate contextual log message.
            throw e;
        }
    }

    // the idea here is to see if the DiskTO in question is from managed storage
    // and
    // does not yet have an SR
    // if no SR, create it and create a VDI in it
    public VDI prepareManagedDisk(final Connection conn, final DiskTO disk, final String vmName) throws Exception {
        final Map<String, String> details = disk.getDetails();

        if (details == null) {
            return null;
        }

        final boolean isManaged = new Boolean(details.get(DiskTO.MANAGED)).booleanValue();

        if (!isManaged) {
            return null;
        }

        final String iqn = details.get(DiskTO.IQN);

        final Set<SR> srNameLabels = SR.getByNameLabel(conn, iqn);

        if (srNameLabels.size() != 0) {
            return null;
        }

        final String vdiNameLabel = vmName + "-DATA";

        return prepareManagedStorage(conn, details, null, vdiNameLabel);
    }

    protected VDI prepareManagedStorage(final Connection conn, final Map<String, String> details, final String path,
                                        final String vdiNameLabel) throws Exception {
        final SR sr = prepareManagedSr(conn, details);

        VDI vdi = getVDIbyUuid(conn, path, false);
        final Long volumeSize = Long.parseLong(details.get(DiskTO.VOLUME_SIZE));

        if (vdi == null) {
            vdi = createVdi(sr, vdiNameLabel, volumeSize);
        } else {
            // if VDI is not null, it must have already been created, so check
            // whether a resize of the volume was performed
            // if true, resize the VDI to the volume size

            s_logger.info("checking for the resize of the datadisk");

            final long vdiVirtualSize = vdi.getVirtualSize(conn);

            if (vdiVirtualSize != volumeSize) {
                s_logger.info(
                        "resizing the data disk (vdi) from vdiVirtualsize: " + vdiVirtualSize + " to volumeSize: " + volumeSize);

                try {
                    vdi.resize(conn, volumeSize);
                } catch (final Exception e) {
                    s_logger.warn("Unable to resize volume", e);
                }
            }
        }

        return vdi;
    }

    protected SR prepareManagedSr(final Connection conn, final Map<String, String> details) {
        final String iScsiName = details.get(DiskTO.IQN);
        final String storageHost = details.get(DiskTO.STORAGE_HOST);
        final String chapInitiatorUsername = details.get(DiskTO.CHAP_INITIATOR_USERNAME);
        final String chapInitiatorSecret = details.get(DiskTO.CHAP_INITIATOR_SECRET);
        final String mountpoint = details.get(DiskTO.MOUNT_POINT);
        final String protocoltype = details.get(DiskTO.PROTOCOL_TYPE);

        if (StoragePoolType.NetworkFilesystem.toString().equalsIgnoreCase(protocoltype)) {
            final String poolid = storageHost + ":" + mountpoint;
            final String namelable = mountpoint;
            final String volumedesc = storageHost + ":" + mountpoint;

            return getNfsSR(conn, poolid, namelable, storageHost, mountpoint, volumedesc);
        } else {
            return getIscsiSR(conn, iScsiName, storageHost, iScsiName, chapInitiatorUsername, chapInitiatorSecret, true);
        }
    }

    public VDI createVdi(final SR sr, final String vdiNameLabel, final Long volumeSize)
            throws Types.XenAPIException, XmlRpcException {
        final Connection conn = getConnection();

        final VDI.Record vdir = new VDI.Record();

        vdir.nameLabel = vdiNameLabel;
        vdir.SR = sr;
        vdir.type = Types.VdiType.USER;

        final long totalSrSpace = sr.getPhysicalSize(conn);
        final long unavailableSrSpace = sr.getPhysicalUtilisation(conn);
        final long availableSrSpace = totalSrSpace - unavailableSrSpace;

        if (availableSrSpace < volumeSize) {
            throw new CloudRuntimeException("Available space for SR cannot be less than " + volumeSize + ".");
        }

        vdir.virtualSize = volumeSize;

        return VDI.create(conn, vdir);
    }

    public SR getNfsSR(final Connection conn, final String poolid, final String uuid, final String server,
                       String serverpath, final String pooldesc) {
        final Map<String, String> deviceConfig = new HashMap<>();
        try {
            serverpath = serverpath.replace("//", "/");
            final Set<SR> srs = SR.getAll(conn);
            if (srs != null && !srs.isEmpty()) {
                for (final SR sr : srs) {
                    if (!SRType.NFS.equals(sr.getType(conn))) {
                        continue;
                    }

                    final Set<PBD> pbds = sr.getPBDs(conn);
                    if (pbds.isEmpty()) {
                        continue;
                    }

                    final PBD pbd = pbds.iterator().next();

                    final Map<String, String> dc = pbd.getDeviceConfig(conn);

                    if (dc == null) {
                        continue;
                    }

                    if (dc.get("server") == null) {
                        continue;
                    }

                    if (dc.get("serverpath") == null) {
                        continue;
                    }

                    if (server.equals(dc.get("server")) && serverpath.equals(dc.get("serverpath"))) {
                        throw new CloudRuntimeException("There is a SR using the same configuration server:" + dc.get("server")
                                + ", serverpath:" + dc.get("serverpath")
                                + " for pool " + uuid + " on host:" + this._host.getUuid());
                    }
                }
            }
            deviceConfig.put("server", server);
            deviceConfig.put("serverpath", serverpath);
            final Host host = Host.getByUuid(conn, this._host.getUuid());
            final Map<String, String> smConfig = new HashMap<>();
            smConfig.put("nosubdir", "true");
            final SR sr = SR.create(conn, host, deviceConfig, new Long(0), uuid, poolid, SRType.NFS.toString(), "user", true,
                    smConfig);
            sr.scan(conn);
            return sr;
        } catch (final XenAPIException e) {
            throw new CloudRuntimeException("Unable to create NFS SR " + pooldesc, e);
        } catch (final XmlRpcException e) {
            throw new CloudRuntimeException("Unable to create NFS SR " + pooldesc, e);
        }
    }

    public SR getIscsiSR(final Connection conn, final String srNameLabel, final String target, String path,
                         final String chapInitiatorUsername,
                         final String chapInitiatorPassword, final boolean ignoreIntroduceException) {
        synchronized (srNameLabel.intern()) {
            final Map<String, String> deviceConfig = new HashMap<>();
            try {
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }

                final String tmp[] = path.split("/");
                if (tmp.length != 3) {
                    final String msg = "Wrong iscsi path " + path + " it should be /targetIQN/LUN";
                    s_logger.warn(msg);
                    throw new CloudRuntimeException(msg);
                }
                final String targetiqn = tmp[1].trim();
                final String lunid = tmp[2].trim();
                String scsiid = "";

                final Set<SR> srs = SR.getByNameLabel(conn, srNameLabel);
                for (final SR sr : srs) {
                    if (!SRType.LVMOISCSI.equals(sr.getType(conn))) {
                        continue;
                    }
                    final Set<PBD> pbds = sr.getPBDs(conn);
                    if (pbds.isEmpty()) {
                        continue;
                    }
                    final PBD pbd = pbds.iterator().next();
                    final Map<String, String> dc = pbd.getDeviceConfig(conn);
                    if (dc == null) {
                        continue;
                    }
                    if (dc.get("target") == null) {
                        continue;
                    }
                    if (dc.get("targetIQN") == null) {
                        continue;
                    }
                    if (dc.get("lunid") == null) {
                        continue;
                    }
                    if (target.equals(dc.get("target")) && targetiqn.equals(dc.get("targetIQN"))
                            && lunid.equals(dc.get("lunid"))) {
                        throw new CloudRuntimeException("There is a SR using the same configuration target:" + dc.get("target")
                                + ",  targetIQN:" + dc.get("targetIQN")
                                + ", lunid:" + dc.get("lunid") + " for pool " + srNameLabel + "on host:" + this._host.getUuid());
                    }
                }
                deviceConfig.put("target", target);
                deviceConfig.put("targetIQN", targetiqn);

                if (StringUtils.isNotBlank(chapInitiatorUsername) && StringUtils.isNotBlank(chapInitiatorPassword)) {
                    deviceConfig.put("chapuser", chapInitiatorUsername);
                    deviceConfig.put("chappassword", chapInitiatorPassword);
                }

                final Host host = Host.getByUuid(conn, this._host.getUuid());
                final Map<String, String> smConfig = new HashMap<>();
                final String type = SRType.LVMOISCSI.toString();
                SR sr = null;
                try {
                    sr = SR.create(conn, host, deviceConfig, new Long(0), srNameLabel, srNameLabel, type, "user", true, smConfig);
                } catch (final XenAPIException e) {
                    final String errmsg = e.toString();
                    if (errmsg.contains("SR_BACKEND_FAILURE_107")) {
                        final String lun[] = errmsg.split("<LUN>");
                        boolean found = false;
                        for (int i = 1; i < lun.length; i++) {
                            final int blunindex = lun[i].indexOf("<LUNid>") + 7;
                            final int elunindex = lun[i].indexOf("</LUNid>");
                            String ilun = lun[i].substring(blunindex, elunindex);
                            ilun = ilun.trim();
                            if (ilun.equals(lunid)) {
                                final int bscsiindex = lun[i].indexOf("<SCSIid>") + 8;
                                final int escsiindex = lun[i].indexOf("</SCSIid>");
                                scsiid = lun[i].substring(bscsiindex, escsiindex);
                                scsiid = scsiid.trim();
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            final String msg = "can not find LUN " + lunid + " in " + errmsg;
                            s_logger.warn(msg);
                            throw new CloudRuntimeException(msg);
                        }
                    } else {
                        final String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.toString();
                        s_logger.warn(msg, e);
                        throw new CloudRuntimeException(msg, e);
                    }
                }
                deviceConfig.put("SCSIid", scsiid);

                final String result = SR.probe(conn, host, deviceConfig, type, smConfig);
                String pooluuid = null;
                if (result.indexOf("<UUID>") != -1) {
                    pooluuid = result.substring(result.indexOf("<UUID>") + 6, result.indexOf("</UUID>")).trim();
                }

                if (pooluuid == null || pooluuid.length() != 36) {
                    sr = SR.create(conn, host, deviceConfig, new Long(0), srNameLabel, srNameLabel, type, "user", true, smConfig);
                } else {
                    try {
                        sr = SR.introduce(conn, pooluuid, srNameLabel, srNameLabel, type, "user", true, smConfig);
                    } catch (final XenAPIException ex) {
                        if (ignoreIntroduceException) {
                            return sr;
                        }

                        throw ex;
                    }

                    final Set<Host> setHosts = Host.getAll(conn);
                    if (setHosts == null) {
                        final String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to hosts not available.";
                        s_logger.warn(msg);
                        throw new CloudRuntimeException(msg);
                    }
                    for (final Host currentHost : setHosts) {
                        final PBD.Record rec = new PBD.Record();

                        rec.deviceConfig = deviceConfig;
                        rec.host = currentHost;
                        rec.SR = sr;

                        final PBD pbd = PBD.create(conn, rec);

                        pbd.plug(conn);
                    }
                }
                sr.scan(conn);
                return sr;
            } catch (final XenAPIException e) {
                final String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.toString();
                s_logger.warn(msg, e);
                throw new CloudRuntimeException(msg, e);
            } catch (final Exception e) {
                final String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.getMessage();
                s_logger.warn(msg, e);
                throw new CloudRuntimeException(msg, e);
            }
        }
    }

    protected ExecutionResult prepareNetworkElementCommand(final SetNetworkACLCommand cmd) {
        final Connection conn = getConnection();
        final String routerName = cmd.getAccessDetail(NetworkElementCommand.ROUTER_NAME);

        try {
            final VM router = getVM(conn, routerName);

            final NicTO nic = cmd.getNic();
            if (nic != null) {
                final VIF vif = getVifByMac(conn, router, nic.getMac());
                if (vif == null) {
                    final String msg = "Prepare SetNetworkACL failed due to VIF is null for : " + nic.getMac()
                            + " with routername: " + routerName;
                    s_logger.error(msg);
                    return new ExecutionResult(false, msg);
                }
            } else {
                final String msg = "Prepare SetNetworkACL failed due to nic is null for : " + routerName;
                s_logger.error(msg);
                return new ExecutionResult(false, msg);
            }
        } catch (final Exception e) {
            final String msg = "Prepare SetNetworkACL failed due to " + e.toString();
            s_logger.error(msg, e);
            return new ExecutionResult(false, msg);
        }
        return new ExecutionResult(true, null);
    }

    public void rebootVM(final Connection conn, final VM vm, final String vmName) throws Exception {
        Task task = null;
        try {
            task = vm.cleanRebootAsync(conn);
            try {
                // poll every 1 seconds , timeout after 10 minutes
                waitForTask(conn, task, 1000, 10 * 60 * 1000);
                checkForSuccess(conn, task);
            } catch (final Types.HandleInvalid e) {
                if (vm.getPowerState(conn) == VmPowerState.RUNNING) {
                    task = null;
                    return;
                }
                throw new CloudRuntimeException("Reboot VM catch HandleInvalid and VM is not in RUNNING state");
            }
        } catch (final XenAPIException e) {
            s_logger.debug("Unable to Clean Reboot VM(" + vmName + ") on host(" + this._host.getUuid() + ") due to " + e.toString()
                    + ", try hard reboot");
            try {
                vm.hardReboot(conn);
            } catch (final Exception e1) {
                final String msg = "Unable to hard Reboot VM(" + vmName + ") on host(" + this._host.getUuid() + ") due to "
                        + e.toString();
                s_logger.warn(msg, e1);
                throw new CloudRuntimeException(msg);
            }
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e1) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e1.toString());
                }
            }
        }
    }

    public String revertToSnapshot(final Connection conn, final VM vmSnapshot, final String vmName,
                                   final String oldVmUuid, final Boolean snapshotMemory, final String hostUUID)
            throws XenAPIException, XmlRpcException {

        final String results = callHostPluginAsync(conn, "vmopsSnapshot", "revert_memory_snapshot", 10 * 60 * 1000,
                "snapshotUUID", vmSnapshot.getUuid(conn), "vmName", vmName,
                "oldVmUuid", oldVmUuid, "snapshotMemory", snapshotMemory.toString(), "hostUUID", hostUUID);
        String errMsg = null;
        if (results == null || results.isEmpty()) {
            errMsg = "revert_memory_snapshot return null";
        } else {
            if (results.equals("0")) {
                return results;
            } else {
                errMsg = "revert_memory_snapshot exception";
            }
        }
        s_logger.warn(errMsg);
        throw new CloudRuntimeException(errMsg);
    }

    public void scaleVM(final Connection conn, final VM vm, final VirtualMachineTO vmSpec, final Host host)
            throws XenAPIException, XmlRpcException {

        final Long staticMemoryMax = vm.getMemoryStaticMax(conn);
        final Long staticMemoryMin = vm.getMemoryStaticMin(conn);
        final Long newDynamicMemoryMin = vmSpec.getMinRam();
        final Long newDynamicMemoryMax = vmSpec.getMaxRam();
        if (staticMemoryMin > newDynamicMemoryMin || newDynamicMemoryMax > staticMemoryMax) {
            throw new CloudRuntimeException("Cannot scale up the vm because of memory constraint violation: "
                    + "0 <= memory-static-min(" + staticMemoryMin
                    + ") <= memory-dynamic-min(" + newDynamicMemoryMin + ") <= memory-dynamic-max(" + newDynamicMemoryMax
                    + ") <= memory-static-max(" + staticMemoryMax + ")");
        }

        vm.setMemoryDynamicRange(conn, newDynamicMemoryMin, newDynamicMemoryMax);
        vm.setVCPUsNumberLive(conn, (long) vmSpec.getCpus());
    }

    public void setCanBridgeFirewall(final boolean canBridgeFirewall) {
        this._canBridgeFirewall = canBridgeFirewall;
    }

    public boolean setIptables(final Connection conn) {
        final String result = callHostPlugin(conn, "vmops", "setIptables");
        if (result == null || result.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * WARN: static-min <= dynamic-min <= dynamic-max <= static-max
     *
     * @param conn
     * @param vm
     * @param minMemsize
     * @param maxMemsize
     * @throws XmlRpcException
     * @throws XenAPIException
     * @see XcpServerResource#setMemory(com.xensource.xenapi.Connection, com.xensource.xenapi.VM, long, long)
     */
    protected void setMemory(final Connection conn, final VM vm, final long minMemsize, final long maxMemsize)
            throws XmlRpcException, XenAPIException {
        vm.setMemoryLimits(conn, mem_128m, maxMemsize, minMemsize, maxMemsize);
    }

    public String setupHeartbeatSr(final Connection conn, final SR sr, final boolean force)
            throws XenAPIException, XmlRpcException {
        final SR.Record srRec = sr.getRecord(conn);
        final String srUuid = srRec.uuid;
        if (!srRec.shared || !SRType.LVMOHBA.equals(srRec.type) && !SRType.LVMOISCSI.equals(srRec.type)
                && !SRType.NFS.equals(srRec.type)) {
            return srUuid;
        }
        String result = null;
        final Host host = Host.getByUuid(conn, this._host.getUuid());
        final Set<String> tags = host.getTags(conn);
        if (force || !tags.contains("cloud-heartbeat-" + srUuid)) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Setting up the heartbeat sr for host " + this._host.getIp() + " and sr " + srUuid);
            }
            final Set<PBD> pbds = sr.getPBDs(conn);
            for (final PBD pbd : pbds) {
                final PBD.Record pbdr = pbd.getRecord(conn);
                if (!pbdr.currentlyAttached && pbdr.host.getUuid(conn).equals(this._host.getUuid())) {
                    pbd.plug(conn);
                    break;
                }
            }
            result = callHostPluginThroughMaster(conn, "vmopspremium", "setup_heartbeat_sr", "host", this._host.getUuid(), "sr",
                    srUuid);
            if (result == null || !result.split("#")[1].equals("0")) {
                throw new CloudRuntimeException("Unable to setup heartbeat sr on SR " + srUuid + " due to " + result);
            }

            if (!tags.contains("cloud-heartbeat-" + srUuid)) {
                tags.add("cloud-heartbeat-" + srUuid);
                host.setTags(conn, tags);
            }
        }
        result = callHostPluginPremium(conn, "setup_heartbeat_file", "host", this._host.getUuid(), "sr", srUuid, "add", "true");
        if (result == null || !result.split("#")[1].equals("0")) {
            throw new CloudRuntimeException("Unable to setup heartbeat file entry on SR " + srUuid + " due to " + result);
        }
        return srUuid;
    }

    protected String callHostPluginThroughMaster(final Connection conn, final String plugin, final String cmd,
                                                 final String... params) {
        final Map<String, String> args = new HashMap<>();

        try {
            final Map<Pool, Pool.Record> poolRecs = Pool.getAllRecords(conn);
            if (poolRecs.size() != 1) {
                throw new CloudRuntimeException("There are " + poolRecs.size() + " pool for host :" + this._host.getUuid());
            }
            final Host master = poolRecs.values().iterator().next().master;
            for (int i = 0; i < params.length; i += 2) {
                args.put(params[i], params[i + 1]);
            }

            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin executing for command " + cmd + " with " + getArgsString(args));
            }
            final String result = master.callPlugin(conn, plugin, cmd, args);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("callHostPlugin Result: " + result);
            }
            return result.replace("\n", "");
        } catch (final Types.HandleInvalid e) {
            s_logger.warn("callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args)
                    + " due to HandleInvalid clazz:" + e.clazz + ", handle:" + e.handle);
        } catch (final XenAPIException e) {
            s_logger.warn(
                    "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.toString(), e);
        } catch (final XmlRpcException e) {
            s_logger.warn(
                    "callHostPlugin failed for cmd: " + cmd + " with args " + getArgsString(args) + " due to " + e.getMessage(),
                    e);
        }
        return null;
    }

    @Override
    public void setConfigParams(final Map<String, Object> params) {
    }

    public void setupLinkLocalNetwork(final Connection conn) {
        try {
            final Network.Record rec = new Network.Record();
            final Set<Network> networks = Network.getByNameLabel(conn, this._linkLocalPrivateNetworkName);
            Network linkLocal = null;

            if (networks.size() == 0) {
                rec.nameDescription = "link local network used by system vms";
                rec.nameLabel = this._linkLocalPrivateNetworkName;
                final Map<String, String> configs = new HashMap<>();
                configs.put("ip_begin", NetUtils.getLinkLocalGateway());
                configs.put("ip_end", NetUtils.getLinkLocalIpEnd());
                configs.put("netmask", NetUtils.getLinkLocalNetMask());
                configs.put("vswitch-disable-in-band", "true");
                rec.otherConfig = configs;
                linkLocal = Network.create(conn, rec);
            } else {
                linkLocal = networks.iterator().next();
                if (!linkLocal.getOtherConfig(conn).containsKey("vswitch-disable-in-band")) {
                    linkLocal.addToOtherConfig(conn, "vswitch-disable-in-band", "true");
                }
            }

            /* Make sure there is a physical bridge on this network */
            VIF dom0vif = null;
            final Pair<VM, VM.Record> vm = getControlDomain(conn);
            final VM dom0 = vm.first();
            final Set<VIF> vifs = dom0.getVIFs(conn);
            if (vifs.size() != 0) {
                for (final VIF vif : vifs) {
                    final Map<String, String> otherConfig = vif.getOtherConfig(conn);
                    if (otherConfig != null) {
                        final String nameLabel = otherConfig.get("nameLabel");
                        if (nameLabel != null && nameLabel.equalsIgnoreCase("link_local_network_vif")) {
                            dom0vif = vif;
                        }
                    }
                }
            }

            /* create temp VIF0 */
            if (dom0vif == null) {
                s_logger.debug("Can't find a vif on dom0 for link local, creating a new one");
                final VIF.Record vifr = new VIF.Record();
                vifr.VM = dom0;
                vifr.device = getLowestAvailableVIFDeviceNum(conn, dom0);
                if (vifr.device == null) {
                    s_logger.debug("Failed to create link local network, no vif available");
                    return;
                }
                final Map<String, String> config = new HashMap<>();
                config.put("nameLabel", "link_local_network_vif");
                vifr.otherConfig = config;
                vifr.MAC = "FE:FF:FF:FF:FF:FF";
                vifr.network = linkLocal;
                vifr.lockingMode = Types.VifLockingMode.NETWORK_DEFAULT;
                dom0vif = VIF.create(conn, vifr);
                plugDom0Vif(conn, dom0vif);
            } else {
                s_logger.debug("already have a vif on dom0 for link local network");
                if (!dom0vif.getCurrentlyAttached(conn)) {
                    plugDom0Vif(conn, dom0vif);
                }
            }

            final String brName = linkLocal.getBridge(conn);
            callHostPlugin(conn, "vmops", "setLinkLocalIP", "brName", brName);
            this._host.setLinkLocalNetwork(linkLocal.getUuid(conn));
        } catch (final XenAPIException e) {
            s_logger.warn("Unable to create local link network", e);
            throw new CloudRuntimeException("Unable to create local link network due to " + e.toString(), e);
        } catch (final XmlRpcException e) {
            s_logger.warn("Unable to create local link network", e);
            throw new CloudRuntimeException("Unable to create local link network due to " + e.toString(), e);
        }
    }

    /* return : if setup is needed */
    public boolean setupServer(final Connection conn, final Host host) {
        final String packageVersion = CitrixResourceBase.class.getPackage().getImplementationVersion();
        final String version = this.getClass().getName() + "-"
                + (packageVersion == null ? Long.toString(System.currentTimeMillis()) : packageVersion);

        try {
            /* push patches to XenServer */
            final Host.Record hr = host.getRecord(conn);

            final Iterator<String> it = hr.tags.iterator();

            while (it.hasNext()) {
                final String tag = it.next();
                if (tag.startsWith("vmops-version-")) {
                    if (tag.contains(version)) {
                        s_logger.info(logX(host, "Host " + hr.address + " is already setup."));
                        return false;
                    } else {
                        it.remove();
                    }
                }
            }

            final com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(hr.address, 22);
            try {
                sshConnection.connect(null, 60000, 60000);
                if (!sshConnection.authenticateWithPassword(this._username, this._password.peek())) {
                    throw new CloudRuntimeException("Unable to authenticate");
                }

                final String cmd = "mkdir -p /opt/cloud/bin /var/log/cloud";
                if (!SSHCmdHelper.sshExecuteCmd(sshConnection, cmd)) {
                    throw new CloudRuntimeException("Cannot create directory /opt/cloud/bin on XenServer hosts");
                }

                final SCPClient scp = new SCPClient(sshConnection);

                final List<File> files = getPatchFiles();
                if (files == null || files.isEmpty()) {
                    throw new CloudRuntimeException("Can not find patch file");
                }
                for (final File file : files) {
                    final String path = file.getParentFile().getAbsolutePath() + "/";
                    final Properties props = PropertiesUtil.loadFromFile(file);

                    for (final Map.Entry<Object, Object> entry : props.entrySet()) {
                        final String k = (String) entry.getKey();
                        final String v = (String) entry.getValue();

                        assert k != null && k.length() > 0 && v != null && v.length() > 0 : "Problems with " + k + "=" + v;

                        final String[] tokens = v.split(",");
                        String f = null;
                        if (tokens.length == 3 && tokens[0].length() > 0) {
                            if (tokens[0].startsWith("/")) {
                                f = tokens[0];
                            } else if (tokens[0].startsWith("~")) {
                                final String homedir = System.getenv("HOME");
                                f = homedir + tokens[0].substring(1) + k;
                            } else {
                                f = path + tokens[0] + '/' + k;
                            }
                        } else {
                            f = path + k;
                        }
                        final String directoryPath = tokens[tokens.length - 1];

                        f = f.replace('/', File.separatorChar);

                        String permissions = "0755";
                        if (tokens.length == 3) {
                            permissions = tokens[1];
                        } else if (tokens.length == 2) {
                            permissions = tokens[0];
                        }

                        if (!new File(f).exists()) {
                            s_logger.warn("We cannot locate " + f);
                            continue;
                        }
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug(
                                    "Copying " + f + " to " + directoryPath + " on " + hr.address + " with permission " + permissions);
                        }

                        if (!SSHCmdHelper.sshExecuteCmd(sshConnection, "mkdir -m 700 -p " + directoryPath)) {
                            s_logger.debug("Unable to create destination path: " + directoryPath + " on " + hr.address + ".");
                        }

                        try {
                            scp.put(f, directoryPath, permissions);
                        } catch (final IOException e) {
                            final String msg = "Unable to copy file " + f + " to path " + directoryPath + " with permissions  "
                                    + permissions;
                            s_logger.debug(msg);
                            throw new CloudRuntimeException("Unable to setup the server: " + msg, e);
                        }
                    }
                }
            } catch (final IOException e) {
                throw new CloudRuntimeException("Unable to setup the server correctly", e);
            } finally {
                sshConnection.close();
            }
            hr.tags.add("vmops-version-" + version);
            host.setTags(conn, hr.tags);
            return true;
        } catch (final XenAPIException e) {
            final String msg = "XenServer setup failed due to " + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException("Unable to get host information " + e.toString(), e);
        } catch (final XmlRpcException e) {
            final String msg = "XenServer setup failed due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException("Unable to get host information ", e);
        }
    }

    protected List<File> getPatchFiles() {
        final String patch = getPatchFilePath();
        final String patchfilePath = Script.findScript("", patch);
        if (patchfilePath == null) {
            throw new CloudRuntimeException("Unable to find patch file " + patch);
        }
        final List<File> files = new ArrayList<>();
        files.add(new File(patchfilePath));
        return files;
    }

    protected abstract String getPatchFilePath();

    public void shutdownVM(final Connection conn, final VM vm, final String vmName, final boolean forcedStop) throws XmlRpcException {
        Task task = null;
        try {
            if (forcedStop) {
                task = vm.hardShutdownAsync(conn);
            } else {
                task = vm.cleanShutdownAsync(conn);
            }

            try {
                // poll every 1 seconds , timeout after 10 minutes
                waitForTask(conn, task, 1000, 10 * 60 * 1000);
                checkForSuccess(conn, task);
            } catch (final TimeoutException e) {
                if (vm.getPowerState(conn) == VmPowerState.HALTED) {
                    task = null;
                    return;
                }
                throw new CloudRuntimeException("Shutdown VM catch HandleInvalid and VM is not in HALTED state");
            }
        } catch (final XenAPIException e) {
            s_logger.debug(
                    "Unable to cleanShutdown VM(" + vmName + ") on host(" + this._host.getUuid() + ") due to " + e.toString());
            try {
                VmPowerState state = vm.getPowerState(conn);
                if (state == VmPowerState.RUNNING) {
                    try {
                        vm.hardShutdown(conn);
                    } catch (final Exception e1) {
                        s_logger.debug(
                                "Unable to hardShutdown VM(" + vmName + ") on host(" + this._host.getUuid() + ") due to " + e.toString());
                        state = vm.getPowerState(conn);
                        if (state == VmPowerState.RUNNING) {
                            forceShutdownVM(conn, vm);
                        }
                        return;
                    }
                } else if (state == VmPowerState.HALTED) {
                    return;
                } else {
                    final String msg = "After cleanShutdown the VM status is " + state.toString() + ", that is not expected";
                    s_logger.warn(msg);
                    throw new CloudRuntimeException(msg);
                }
            } catch (final Exception e1) {
                final String msg = "Unable to hardShutdown VM(" + vmName + ") on host(" + this._host.getUuid() + ") due to "
                        + e.toString();
                s_logger.warn(msg, e1);
                throw new CloudRuntimeException(msg);
            }
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e1) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e1.toString());
                }
            }
        }
    }

    @Override
    public void setRunLevel(final int level) {
    }

    void forceShutdownVM(final Connection conn, final VM vm) {
        try {
            final Long domId = vm.getDomid(conn);
            callHostPlugin(conn, "vmopspremium", "forceShutdownVM", "domId", domId.toString());
            vm.powerStateReset(conn);
            vm.destroy(conn);
        } catch (final Exception e) {
            final String msg = "forceShutdown failed due to " + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg);
        }
    }

    public void startVM(final Connection conn, final Host host, final VM vm, final String vmName) throws Exception {
        Task task = null;
        try {
            task = vm.startOnAsync(conn, host, false, true);
            try {
                // poll every 1 seconds , timeout after 10 minutes
                waitForTask(conn, task, 1000, 10 * 60 * 1000);
                checkForSuccess(conn, task);
            } catch (final Types.HandleInvalid e) {
                if (vm.getPowerState(conn) == VmPowerState.RUNNING) {
                    s_logger.debug("VM " + vmName + " is in Running status");
                    task = null;
                    return;
                }
                throw new CloudRuntimeException("Start VM " + vmName + " catch HandleInvalid and VM is not in RUNNING state");
            } catch (final TimeoutException e) {
                if (vm.getPowerState(conn) == VmPowerState.RUNNING) {
                    s_logger.debug("VM " + vmName + " is in Running status");
                    task = null;
                    return;
                }
                throw new CloudRuntimeException("Start VM " + vmName + " catch BadAsyncResult and VM is not in RUNNING state");
            }
        } catch (final XenAPIException e) {
            final String msg = "Unable to start VM(" + vmName + ") on host(" + this._host.getUuid() + ") due to " + e.toString();
            s_logger.warn(msg, e);
            throw new CloudRuntimeException(msg);
        } finally {
            if (task != null) {
                try {
                    task.destroy(conn);
                } catch (final Exception e1) {
                    s_logger.debug("unable to destroy task(" + task.toString() + ") on host(" + this._host.getUuid() + ") due to "
                            + e1.toString());
                }
            }
        }
    }

    private HashMap<String, Pair<Long, Long>> syncNetworkGroups(final Connection conn, final long id) {
        final HashMap<String, Pair<Long, Long>> states = new HashMap<>();

        final String result = callHostPlugin(conn, "vmops", "get_rule_logs_for_vms", "host_uuid", this._host.getUuid());
        s_logger.trace("syncNetworkGroups: id=" + id + " got: " + result);
        final String[] rulelogs = result != null ? result.split(";") : new String[0];
        for (final String rulesforvm : rulelogs) {
            final String[] log = rulesforvm.split(",");
            if (log.length != 6) {
                continue;
            }
            // output = ','.join([vmName, vmID, vmIP, domID, signature, seqno])
            try {
                states.put(log[0], new Pair<>(Long.parseLong(log[1]), Long.parseLong(log[5])));
            } catch (final NumberFormatException nfe) {
                states.put(log[0], new Pair<>(-1L, -1L));
            }
        }
        return states;
    }

    public boolean transferManagementNetwork(final Connection conn, final Host host, final PIF src, final PIF.Record spr,
                                             final PIF dest) throws XmlRpcException, XenAPIException {
        dest.reconfigureIp(conn, spr.ipConfigurationMode, spr.IP, spr.netmask, spr.gateway, spr.DNS);
        Host.managementReconfigure(conn, dest);
        String hostUuid = null;
        int count = 0;
        while (count < 10) {
            try {
                Thread.sleep(10000);
                hostUuid = host.getUuid(conn);
                if (hostUuid != null) {
                    break;
                }
                ++count;
            } catch (final XmlRpcException e) {
                s_logger.debug("Waiting for host to come back: " + e.getMessage());
            } catch (final XenAPIException e) {
                s_logger.debug("Waiting for host to come back: " + e.getMessage());
            } catch (final InterruptedException e) {
                s_logger.debug("Gotta run");
                return false;
            }
        }
        if (hostUuid == null) {
            s_logger.warn("Unable to transfer the management network from " + spr.uuid);
            return false;
        }

        src.reconfigureIp(conn, Types.IpConfigurationMode.NONE, null, null, null, null);
        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    protected void umount(final Connection conn, final VDI vdi) {

    }

    public void umountSnapshotDir(final Connection conn, final Long dcId) {
        try {
            callHostPlugin(conn, "vmopsSnapshot", "unmountSnapshotsDir", "dcId", dcId.toString());
        } catch (final Exception e) {
            s_logger.debug("Failed to umount snapshot dir", e);
        }
    }

    public String upgradeSnapshot(final Connection conn, final String templatePath, final String snapshotPath) {
        final String results = callHostPluginAsync(conn, "vmopspremium", "upgrade_snapshot", 2 * 60 * 60, "templatePath",
                templatePath, "snapshotPath", snapshotPath);

        if (results == null || results.isEmpty()) {
            final String msg = "upgrade_snapshot return null";
            s_logger.warn(msg);
            throw new CloudRuntimeException(msg);
        }
        final String[] tmp = results.split("#");
        final String status = tmp[0];
        if (status.equals("0")) {
            return results;
        } else {
            s_logger.warn(results);
            throw new CloudRuntimeException(results);
        }
    }

    @Override
    public boolean stop() {
        disconnected();
        return true;
    }

    public boolean createAndAttachConfigDriveIsoForVM(final Connection conn, final VM vm, final List<String[]> vmDataList,
                                                      final String configDriveLabel) throws XenAPIException, XmlRpcException {

        final String vmName = vm.getNameLabel(conn);

        // create SR
        final SR sr = createLocalIsoSR(conn, this._configDriveSRName + this._host.getIp());
        if (sr == null) {
            s_logger.debug("Failed to create local SR for the config drive");
            return false;
        }

        s_logger.debug("Creating vm data files in config drive for vm " + vmName);
        // 1. create vm data files
        if (!createVmdataFiles(vmName, vmDataList, configDriveLabel)) {
            s_logger.debug("Failed to create vm data files in config drive for vm " + vmName);
            return false;
        }

        // 2. copy config drive iso to host
        if (!copyConfigDriveIsoToHost(conn, sr, vmName)) {
            return false;
        }

        // 3. attachIsoToVM
        if (!attachConfigDriveIsoToVm(conn, vm)) {
            return false;
        }

        return true;
    }

    public boolean attachConfigDriveIsoToVm(final Connection conn, final VM vm) throws XenAPIException, XmlRpcException {

        final String vmName = vm.getNameLabel(conn);
        final String isoURL = this._configDriveIsopath + vmName + ".iso";
        final VDI srVdi;

        // 1. find the vdi of the iso
        // 2. find the vbd for the vdi
        // 3. attach iso to vm

        try {
            final Set<VDI> vdis = VDI.getByNameLabel(conn, vmName + ".iso");
            if (vdis.isEmpty()) {
                throw new CloudRuntimeException("Could not find ISO with URL: " + isoURL);
            }
            srVdi = vdis.iterator().next();
        } catch (final XenAPIException e) {
            s_logger.debug("Unable to get config drive iso: " + isoURL + " due to " + e.toString());
            return false;
        } catch (final Exception e) {
            s_logger.debug("Unable to get config drive iso: " + isoURL + " due to " + e.toString());
            return false;
        }

        VBD isoVBD = null;

        // Find the VM's CD-ROM VBD
        final Set<VBD> vbds = vm.getVBDs(conn);
        for (final VBD vbd : vbds) {
            final Types.VbdType type = vbd.getType(conn);

            final VBD.Record vbdr = vbd.getRecord(conn);

            // if the device exists then attach it
            if (!vbdr.userdevice.equals(this._attachIsoDeviceNum) && type == Types.VbdType.CD) {
                isoVBD = vbd;
                break;
            }
        }

        if (isoVBD == null) {
            // create vbd
            final VBD.Record cfgDriveVbdr = new VBD.Record();
            cfgDriveVbdr.VM = vm;
            cfgDriveVbdr.empty = true;
            cfgDriveVbdr.bootable = false;
            cfgDriveVbdr.userdevice = "autodetect";
            cfgDriveVbdr.mode = Types.VbdMode.RO;
            cfgDriveVbdr.type = Types.VbdType.CD;
            final VBD cfgDriveVBD = VBD.create(conn, cfgDriveVbdr);
            isoVBD = cfgDriveVBD;

            s_logger.debug("Created CD-ROM VBD for VM: " + vm);
        }

        if (isoVBD != null) {
            // If an ISO is already inserted, eject it
            if (isoVBD.getEmpty(conn) == false) {
                isoVBD.eject(conn);
            }

            try {
                // Insert the new ISO
                isoVBD.insert(conn, srVdi);
                s_logger.debug("Attached config drive iso to vm " + vmName);
            } catch (final XmlRpcException ex) {
                s_logger.debug("Failed to attach config drive iso to vm " + vmName);
                return false;
            }
        }

        return true;
    }

    public boolean attachConfigDriveToMigratedVm(final Connection conn, final String vmName, final String ipAddr) {

        // attach the config drive in destination host

        try {
            s_logger.debug("Attaching config drive iso device for the VM " + vmName + " In host " + ipAddr);
            final Set<VM> vms = VM.getByNameLabel(conn, vmName);

            final SR sr = getSRByNameLabel(conn, this._configDriveSRName + ipAddr);
            // Here you will find only two vdis with the <vmname>.iso.
            // one is from source host and second from dest host
            final Set<VDI> vdis = VDI.getByNameLabel(conn, vmName + ".iso");
            if (vdis.isEmpty()) {
                s_logger.debug("Could not find config drive ISO: " + vmName);
                return false;
            }

            VDI configdriveVdi = null;
            for (final VDI vdi : vdis) {
                final SR vdiSr = vdi.getSR(conn);
                if (vdiSr.getUuid(conn).equals(sr.getUuid(conn))) {
                    // get this vdi to attach to vbd
                    configdriveVdi = vdi;
                    s_logger.debug("VDI for the config drive ISO  " + vdi);
                } else {
                    // delete the vdi in source host so that the <vmname>.iso file is get removed
                    s_logger.debug("Removing the source host VDI for the config drive ISO  " + vdi);
                    vdi.destroy(conn);
                }
            }

            if (configdriveVdi == null) {
                s_logger.debug("Config drive ISO VDI is not found ");
                return false;
            }

            for (final VM vm : vms) {

                // create vbd
                final VBD.Record cfgDriveVbdr = new VBD.Record();
                cfgDriveVbdr.VM = vm;
                cfgDriveVbdr.empty = true;
                cfgDriveVbdr.bootable = false;
                cfgDriveVbdr.userdevice = "autodetect";
                cfgDriveVbdr.mode = Types.VbdMode.RO;
                cfgDriveVbdr.type = Types.VbdType.CD;

                final VBD cfgDriveVBD = VBD.create(conn, cfgDriveVbdr);

                s_logger.debug("Inserting vbd " + configdriveVdi);
                cfgDriveVBD.insert(conn, configdriveVdi);
                break;
            }

            return true;
        } catch (final BadServerResponse e) {
            s_logger.warn("Failed to attach config drive ISO to the VM  " + vmName + " In host " + ipAddr
                    + " due to a bad server response.", e);
            return false;
        } catch (final XenAPIException e) {
            s_logger.warn(
                    "Failed to attach config drive ISO to the VM  " + vmName + " In host " + ipAddr + " due to a xapi problem.",
                    e);
            return false;
        } catch (final XmlRpcException e) {
            s_logger.warn("Failed to attach config drive ISO to the VM  " + vmName + " In host " + ipAddr
                    + " due to a problem in a remote call.", e);
            return false;
        }
    }

    protected SR getSRByNameLabel(final Connection conn, final String name)
            throws BadServerResponse, XenAPIException, XmlRpcException {
        final Set<SR> srs = SR.getByNameLabel(conn, name);
        SR ressr = null;
        for (final SR sr : srs) {
            final Set<PBD> pbds;
            pbds = sr.getPBDs(conn);
            for (final PBD pbd : pbds) {
                final PBD.Record pbdr = pbd.getRecord(conn);
                if (pbdr.host != null) {
                    ressr = sr;
                    break;
                }
            }
        }
        return ressr;
    }

    public enum SRType {
        EXT, FILE, ISCSI, ISO, LVM, LVMOHBA, LVMOISCSI, NFS;

        String _str;

        private SRType() {
            this._str = super.toString().toLowerCase();
        }

        public boolean equals(final String type) {
            return this._str.equalsIgnoreCase(type);
        }

        @Override
        public String toString() {
            return this._str;
        }
    }
}
