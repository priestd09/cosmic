package com.cloud.agent.resource.kvm.wrapper;

import com.cloud.agent.resource.kvm.LibvirtComputingResource;
import com.cloud.common.request.CommandWrapper;
import com.cloud.common.request.RequestWrapper;
import com.cloud.common.resource.ServerResource;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.command.Command;

import java.util.Hashtable;
import java.util.Set;

import org.reflections.Reflections;

public class LibvirtRequestWrapper extends RequestWrapper {

    private static final LibvirtRequestWrapper instance;

    static {
        instance = new LibvirtRequestWrapper();
    }

    Reflections baseWrappers = new Reflections("com.cloud.agent.resource.kvm.wrapper");
    Set<Class<? extends CommandWrapper>> baseSet = this.baseWrappers.getSubTypesOf(CommandWrapper.class);

    private LibvirtRequestWrapper() {
        init();
    }

    private void init() {
        // LibvirtComputingResource commands
        final Hashtable<Class<? extends Command>, CommandWrapper> libvirtCommands = processAnnotations(this.baseSet);

        this.resources.put(LibvirtComputingResource.class, libvirtCommands);
    }

    public static LibvirtRequestWrapper getInstance() {
        return instance;
    }

    @Override
    public Answer execute(final Command command, final ServerResource serverResource) {
        final Class<? extends ServerResource> resourceClass = serverResource.getClass();

        final Hashtable<Class<? extends Command>, CommandWrapper> resourceCommands = retrieveResource(command, resourceClass);

        CommandWrapper<Command, Answer, ServerResource> commandWrapper = retrieveCommands(command.getClass(), resourceCommands);

        while (commandWrapper == null) {
            // Could not find the command in the given resource, will traverse the family tree.
            commandWrapper = retryWhenAllFails(command, resourceClass, resourceCommands);
        }

        return commandWrapper.execute(command, serverResource);
    }
}
