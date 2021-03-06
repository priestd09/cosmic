package com.cloud.agent.manager.authn;

import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.utils.component.Adapter;

public interface AgentAuthorizer extends Adapter {
    boolean authorizeAgent(StartupCommand[] cmd);
}
