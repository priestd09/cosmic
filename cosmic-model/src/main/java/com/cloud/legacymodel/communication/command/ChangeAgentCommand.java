package com.cloud.legacymodel.communication.command;

import com.cloud.model.enumeration.Event;

public class ChangeAgentCommand extends Command {
    long agentId;
    Event event;

    protected ChangeAgentCommand() {
    }

    public ChangeAgentCommand(final long agentId, final Event event) {
        this.agentId = agentId;
        this.event = event;
    }

    public long getAgentId() {
        return agentId;
    }

    public Event getEvent() {
        return event;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
