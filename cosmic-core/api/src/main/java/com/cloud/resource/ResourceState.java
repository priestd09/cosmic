package com.cloud.resource;

import com.cloud.legacymodel.statemachine.StateMachine;

import java.util.List;
import java.util.Set;

public enum ResourceState {
    Creating, Enabled, Disabled, PrepareForMaintenance, ErrorInMaintenance, Maintenance, Error;

    protected static final StateMachine<ResourceState, Event> s_fsm = new StateMachine<>();

    static {
        s_fsm.addTransition(null, Event.InternalCreated, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Creating, Event.InternalCreated, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Creating, Event.Error, ResourceState.Error);
        s_fsm.addTransition(ResourceState.Enabled, Event.Enable, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Enabled, Event.InternalCreated, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Enabled, Event.Disable, ResourceState.Disabled);
        s_fsm.addTransition(ResourceState.Enabled, Event.AdminAskMaintenace, ResourceState.PrepareForMaintenance);
        s_fsm.addTransition(ResourceState.Disabled, Event.Enable, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Disabled, Event.Disable, ResourceState.Disabled);
        s_fsm.addTransition(ResourceState.Disabled, Event.InternalCreated, ResourceState.Disabled);
        s_fsm.addTransition(ResourceState.PrepareForMaintenance, Event.InternalEnterMaintenance, ResourceState.Maintenance);
        s_fsm.addTransition(ResourceState.PrepareForMaintenance, Event.AdminCancelMaintenance, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.PrepareForMaintenance, Event.UnableToMigrate, ResourceState.ErrorInMaintenance);
        s_fsm.addTransition(ResourceState.PrepareForMaintenance, Event.InternalCreated, ResourceState.PrepareForMaintenance);
        s_fsm.addTransition(ResourceState.Maintenance, Event.AdminCancelMaintenance, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Maintenance, Event.InternalCreated, ResourceState.Maintenance);
        s_fsm.addTransition(ResourceState.Maintenance, Event.DeleteHost, ResourceState.Disabled);
        s_fsm.addTransition(ResourceState.ErrorInMaintenance, Event.InternalCreated, ResourceState.ErrorInMaintenance);
        s_fsm.addTransition(ResourceState.ErrorInMaintenance, Event.Disable, ResourceState.Disabled);
        s_fsm.addTransition(ResourceState.ErrorInMaintenance, Event.DeleteHost, ResourceState.Disabled);
        s_fsm.addTransition(ResourceState.ErrorInMaintenance, Event.InternalEnterMaintenance, ResourceState.Maintenance);
        s_fsm.addTransition(ResourceState.ErrorInMaintenance, Event.AdminCancelMaintenance, ResourceState.Enabled);
        s_fsm.addTransition(ResourceState.Error, Event.InternalCreated, ResourceState.Error);
    }

    public static String[] toString(final ResourceState... states) {
        final String[] strs = new String[states.length];
        for (int i = 0; i < states.length; i++) {
            strs[i] = states[i].toString();
        }
        return strs;
    }

    public ResourceState getNextState(final Event a) {
        return s_fsm.getNextState(this, a);
    }

    public ResourceState[] getFromStates(final Event a) {
        final List<ResourceState> from = s_fsm.getFromStates(this, a);
        return from.toArray(new ResourceState[from.size()]);
    }

    public Set<Event> getPossibleEvents() {
        return s_fsm.getPossibleEvents(this);
    }

    public enum Event {
        InternalCreated("Resource is created"),
        Enable("Admin enables"),
        Disable("Admin disables"),
        AdminAskMaintenace("Admin asks to enter maintenance"),
        AdminCancelMaintenance("Admin asks to cancel maintenance"),
        InternalEnterMaintenance("Resource enters maintenance"),
        UpdatePassword("Admin updates password of host"),
        UnableToMigrate("Management server migrates VM failed"),
        Error("An internal error happened"),
        DeleteHost("Admin delete a host"),

        /*
         * Below events don't cause resource state to change, they are merely
         * for ClusterManager propagating event from one mgmt server to another
         */
        Unmanaged("Umanage a cluster");

        private final String comment;

        private Event(final String comment) {
            this.comment = comment;
        }

        public static Event toEvent(final String e) {
            if (Enable.toString().equals(e)) {
                return Enable;
            } else if (Disable.toString().equals(e)) {
                return Disable;
            }

            return null;
        }

        public String getDescription() {
            return this.comment;
        }
    }
}
