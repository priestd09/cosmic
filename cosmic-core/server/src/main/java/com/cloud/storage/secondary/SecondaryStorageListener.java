package com.cloud.storage.secondary;

import com.cloud.agent.Listener;
import com.cloud.legacymodel.communication.answer.AgentControlAnswer;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.command.AgentControlCommand;
import com.cloud.legacymodel.communication.command.Command;
import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.legacymodel.communication.command.StartupSecondaryStorageCommand;
import com.cloud.legacymodel.communication.command.StartupStorageCommand;
import com.cloud.legacymodel.dc.Host;
import com.cloud.legacymodel.dc.HostStatus;
import com.cloud.model.enumeration.StorageResourceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecondaryStorageListener implements Listener {
    private final static Logger s_logger = LoggerFactory.getLogger(SecondaryStorageListener.class);

    SecondaryStorageVmManager _ssVmMgr = null;

    public SecondaryStorageListener(final SecondaryStorageVmManager ssVmMgr) {
        _ssVmMgr = ssVmMgr;
    }

    @Override
    public boolean processAnswers(final long agentId, final long seq, final Answer[] answers) {
        final boolean processed = false;
        if (answers != null) {
            for (int i = 0; i < answers.length; i++) {
            }
        }

        return processed;
    }

    @Override
    public boolean processCommands(final long agentId, final long seq, final Command[] commands) {
        return false;
    }

    @Override
    public AgentControlAnswer processControlCommand(final long agentId, final AgentControlCommand cmd) {
        return null;
    }

    @Override
    public void processConnect(final Host agent, final StartupCommand cmd, final boolean forRebalance) {
        if ((cmd instanceof StartupStorageCommand)) {
            final StartupStorageCommand scmd = (StartupStorageCommand) cmd;
            if (scmd.getResourceType() == StorageResourceType.SECONDARY_STORAGE) {
                _ssVmMgr.generateSetupCommand(agent.getId());
                return;
            }
        } else if (cmd instanceof StartupSecondaryStorageCommand) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("Received a host startup notification " + cmd);
            }
            _ssVmMgr.onAgentConnect(agent.getDataCenterId(), cmd);
            _ssVmMgr.generateSetupCommand(agent.getId());
            _ssVmMgr.generateFirewallConfiguration(agent.getId());
            _ssVmMgr.generateVMSetupCommand(agent.getId());
            return;
        }
        return;
    }

    @Override
    public boolean processDisconnect(final long agentId, final HostStatus state) {
        return true;
    }

    @Override
    public boolean isRecurring() {
        return true;
    }

    @Override
    public int getTimeout() {
        return -1;
    }

    @Override
    public boolean processTimeout(final long agentId, final long seq) {
        return true;
    }
}
