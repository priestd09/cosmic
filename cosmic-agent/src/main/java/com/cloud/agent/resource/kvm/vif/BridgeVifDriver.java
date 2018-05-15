package com.cloud.agent.resource.kvm.vif;

import com.cloud.agent.resource.kvm.LibvirtComputingResourceProperties;
import com.cloud.agent.resource.kvm.xml.LibvirtVmDef;
import com.cloud.legacymodel.exceptions.InternalErrorException;
import com.cloud.legacymodel.to.NicTO;
import com.cloud.model.enumeration.BroadcastDomainType;
import com.cloud.model.enumeration.TrafficType;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.net.NetUtils;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;

import javax.naming.ConfigurationException;
import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeVifDriver extends VifDriverBase {

    private final Logger logger = LoggerFactory.getLogger(BridgeVifDriver.class);
    private final Object vnetBridgeMonitor = new Object();
    private int timeout;
    private String modifyVlanPath;
    private String modifyVxlanPath;
    private String bridgeNameSchema;
    private Long libvirtVersion;

    @Override
    public void configure(final Map<String, Object> params) throws ConfigurationException {

        super.configure(params);

        // Set the domr scripts directory
        params.put("domr.scripts.dir", "scripts/network/domr/kvm");

        String networkScriptsDir = (String) params.get("network.scripts.dir");
        if (networkScriptsDir == null) {
            networkScriptsDir = "scripts/vm/network/vnet";
        }

        this.bridgeNameSchema = (String) params.get("network.bridge.name.schema");

        final String value = (String) params.get("scripts.timeout");
        this.timeout = NumbersUtil.parseInt(value, 30 * 60) * 1000;

        this.modifyVlanPath = Script.findScript(networkScriptsDir, LibvirtComputingResourceProperties.Constants.SCRIPT_MODIFY_VLAN);
        if (this.modifyVlanPath == null) {
            throw new ConfigurationException("Unable to find " + LibvirtComputingResourceProperties.Constants.SCRIPT_MODIFY_VLAN);
        }
        this.modifyVxlanPath = Script.findScript(networkScriptsDir, LibvirtComputingResourceProperties.Constants.SCRIPT_MODIFY_VXLAN);
        if (this.modifyVxlanPath == null) {
            throw new ConfigurationException("Unable to find " + LibvirtComputingResourceProperties.Constants.SCRIPT_MODIFY_VXLAN);
        }

        this.libvirtVersion = (Long) params.get("libvirtVersion");
        if (this.libvirtVersion == null) {
            this.libvirtVersion = 0L;
        }

        try {
            createControlNetwork();
        } catch (final LibvirtException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    @Override
    public LibvirtVmDef.InterfaceDef plug(final NicTO nic, final String guestOsType, final String nicAdapter)
            throws InternalErrorException, LibvirtException {

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("nic=" + nic);
            if (nicAdapter != null && !nicAdapter.isEmpty()) {
                this.logger.debug("custom nic adapter=" + nicAdapter);
            }
        }

        final LibvirtVmDef.InterfaceDef intf = new LibvirtVmDef.InterfaceDef();

        String netId = null;
        String protocol = null;
        if (nic.getBroadcastType() == BroadcastDomainType.Vlan
                || nic.getBroadcastType() == BroadcastDomainType.Vxlan) {
            netId = BroadcastDomainType.getValue(nic.getBroadcastUri());
            protocol = BroadcastDomainType.getSchemeValue(nic.getBroadcastUri()).scheme();
        } else if (nic.getBroadcastType() == BroadcastDomainType.Lswitch) {
            throw new InternalErrorException("Nicira NVP Logicalswitches are not supported by the BridgeVifDriver");
        }
        final String trafficLabel = nic.getName();
        Integer networkRateKBps = 0;
        if (this.libvirtVersion > 10 * 1000 + 10) {
            networkRateKBps = nic.getNetworkRateMbps() != null && nic.getNetworkRateMbps().intValue() != -1
                    ? nic.getNetworkRateMbps().intValue() * 128 : 0;
        }

        if (nic.getType() == TrafficType.Guest) {
            if (nic.getBroadcastType() == BroadcastDomainType.Vlan && netId != null && protocol != null
                    && !netId.equalsIgnoreCase("untagged")
                    || nic.getBroadcastType() == BroadcastDomainType.Vxlan) {
                if (trafficLabel != null && !trafficLabel.isEmpty()) {
                    this.logger.debug("creating a vNet dev and bridge for guest traffic per traffic label " + trafficLabel);
                    final String brName = createVnetBr(netId, trafficLabel, protocol);
                    intf.defBridgeNet(brName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
                } else {
                    final String brName = createVnetBr(netId, "private", protocol);
                    intf.defBridgeNet(brName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
                }
            } else {
                String brname = "";
                if (trafficLabel != null && !trafficLabel.isEmpty()) {
                    brname = trafficLabel;
                } else {
                    brname = this.bridges.get("guest");
                }
                intf.defBridgeNet(brname, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
            }
        } else if (nic.getType() == TrafficType.Control) {
            /* Make sure the network is still there */
            createControlNetwork();
            intf.defBridgeNet(this.bridges.get("linklocal"), null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter));
        } else if (nic.getType() == TrafficType.Public) {
            if (nic.getBroadcastType() == BroadcastDomainType.Vlan && netId != null && protocol != null
                    && !netId.equalsIgnoreCase("untagged")
                    || nic.getBroadcastType() == BroadcastDomainType.Vxlan) {
                if (trafficLabel != null && !trafficLabel.isEmpty()) {
                    this.logger.debug("creating a vNet dev and bridge for public traffic per traffic label " + trafficLabel);
                    final String brName = createVnetBr(netId, trafficLabel, protocol);
                    intf.defBridgeNet(brName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
                } else {
                    final String brName = createVnetBr(netId, "public", protocol);
                    intf.defBridgeNet(brName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter), networkRateKBps);
                }
            } else {
                intf.defBridgeNet(this.bridges.get("public"), null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter),
                        networkRateKBps);
            }
        } else if (nic.getType() == TrafficType.Management) {
            intf.defBridgeNet(this.bridges.get("private"), null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter));
        } else if (nic.getType() == TrafficType.Storage) {
            final String storageBrName = nic.getName() == null ? this.bridges.get("private") : nic.getName();
            intf.defBridgeNet(storageBrName, null, nic.getMac(), getGuestNicModel(guestOsType, nicAdapter));
        }
        return intf;
    }

    @Override
    public void unplug(final LibvirtVmDef.InterfaceDef iface) {
        deleteVnetBr(iface.getBrName());
    }

    private void deleteVnetBr(final String brName) {
        synchronized (this.vnetBridgeMonitor) {
            String cmdout = Script.runSimpleBashScript("ls /sys/class/net/" + brName);
            if (cmdout == null) {
                // Bridge does not exist
                return;
            }
            cmdout = Script.runSimpleBashScript("ls /sys/class/net/" + brName + "/brif | tr '\n' ' '");
            if (cmdout != null && cmdout.contains("vnet")) {
                // Active VM remains on that bridge
                return;
            }

            final Pattern oldStyleBrNameRegex = Pattern.compile("^cloudVirBr(\\d+)$");
            final Pattern brNameRegex = Pattern.compile("^br(\\S+)-(\\d+)$");
            final Matcher oldStyleBrNameMatcher = oldStyleBrNameRegex.matcher(brName);
            final Matcher brNameMatcher = brNameRegex.matcher(brName);

            String name = null;
            String netId = null;
            if (oldStyleBrNameMatcher.find()) {
                // Actually modifyvlan.sh doesn't require pif name when deleting its bridge so far.
                name = "undefined";
                netId = oldStyleBrNameMatcher.group(1);
            } else if (brNameMatcher.find()) {
                if (brNameMatcher.group(1) != null || !brNameMatcher.group(1).isEmpty()) {
                    name = brNameMatcher.group(1);
                } else {
                    name = "undefined";
                }
                netId = brNameMatcher.group(2);
            }

            if (netId == null || netId.isEmpty()) {
                this.logger.debug("unable to get a vNet ID from name " + brName);
                return;
            }

            String scriptPath = null;
            if (cmdout != null && cmdout.contains("vxlan")) {
                scriptPath = this.modifyVxlanPath;
            } else {
                scriptPath = this.modifyVlanPath;
            }

            final Script command = new Script(scriptPath, this.timeout, this.logger);
            command.add("-o", "delete");
            command.add("-v", netId);
            command.add("-p", name);
            command.add("-b", brName);

            final String result = command.execute();
            if (result != null) {
                this.logger.debug("Delete bridge " + brName + " failed: " + result);
            }
        }
    }

    private String createVnetBr(final String netId, final String pifKey, final String protocol) throws InternalErrorException {
        String nic = this.pifs.get(pifKey);
        if (nic == null) {
            // if not found in bridge map, maybe traffic label refers to pif already?
            final File pif = new File("/sys/class/net/" + pifKey);
            if (pif.isDirectory()) {
                nic = pifKey;
            }
        }
        String brName = "";
        if (protocol.equals(BroadcastDomainType.Vxlan.scheme())) {
            brName = setVxnetBrName(nic, netId);
        } else {
            brName = setVnetBrName(nic, netId);
        }
        createVnet(netId, nic, brName, protocol);
        return brName;
    }

    private String setVxnetBrName(final String pifName, final String vnetId) {
        return "brvx-" + vnetId;
    }

    private String setVnetBrName(final String pifName, final String vnetId) {
        return "br" + pifName + "-" + vnetId;
    }

    private void createVnet(final String vnetId, final String pif, final String brName, final String protocol) throws InternalErrorException {
        synchronized (this.vnetBridgeMonitor) {
            String script = this.modifyVlanPath;
            if (protocol.equals(BroadcastDomainType.Vxlan.scheme())) {
                script = this.modifyVxlanPath;
            }
            final Script command = new Script(script, this.timeout, this.logger);
            command.add("-v", vnetId);
            command.add("-p", pif);
            command.add("-b", brName);
            command.add("-o", "add");

            final String result = command.execute();
            if (result != null) {
                throw new InternalErrorException("Failed to create vnet " + vnetId + ": " + result);
            }
        }
    }

    private void createControlNetwork() throws LibvirtException {
        createControlNetwork(this.bridges.get("linklocal"));
    }

    private void createControlNetwork(final String privBrName) {
        deleteExistingLinkLocalRouteTable(privBrName);
        if (!isBridgeExists(privBrName)) {
            Script.runSimpleBashScript("brctl addbr " + privBrName + "; ip link set " + privBrName
                    + " up; ip address add 169.254.0.1/16 dev " + privBrName, this.timeout);
        }
    }

    private void deleteExistingLinkLocalRouteTable(final String linkLocalBr) {
        final Script command = new Script("/bin/bash", this.timeout);
        command.add("-c");
        command.add("ip route | grep " + NetUtils.getLinkLocalCIDR());
        final OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
        final String result = command.execute(parser);
        boolean foundLinkLocalBr = false;
        if (result == null && parser.getLines() != null) {
            final String[] lines = parser.getLines().split("\\n");
            for (final String line : lines) {
                final String[] tokens = line.split(" ");
                if (tokens != null && tokens.length < 2) {
                    continue;
                }
                final String device = tokens[2];
                if (!Strings.isNullOrEmpty(device) && !device.equalsIgnoreCase(linkLocalBr)) {
                    Script.runSimpleBashScript("ip route del " + NetUtils.getLinkLocalCIDR() + " dev " + tokens[2]);
                } else {
                    foundLinkLocalBr = true;
                }
            }
        }
        if (!foundLinkLocalBr) {
            Script.runSimpleBashScript("ip address add 169.254.0.1/16 dev " + linkLocalBr + ";" + "ip route add "
                    + NetUtils.getLinkLocalCIDR() + " dev " + linkLocalBr + " src "
                    + NetUtils.getLinkLocalGateway());
        }
    }

    private boolean isBridgeExists(final String bridgeName) {
        final File f = new File("/sys/devices/virtual/net/" + bridgeName);
        if (f.exists()) {
            return true;
        } else {
            return false;
        }
    }
}
