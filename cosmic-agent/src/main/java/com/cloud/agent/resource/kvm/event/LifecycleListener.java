package com.cloud.agent.resource.kvm.event;

import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.libvirt.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifecycleListener implements org.libvirt.event.LifecycleListener {
    private static final Logger logger = LoggerFactory.getLogger(LifecycleListener.class);

    @Override
    public int onLifecycleChange(final Domain domain, final DomainEvent event) {

        switch (event.getType()) {
            case DEFINED:
            case UNDEFINED:
            case STARTED:
            case SUSPENDED:
            case RESUMED:
            case STOPPED:
            case SHUTDOWN:
            case PMSUSPENDED:
            case CRASHED:
            case UNKNOWN:
            default:
                logger.debug("Domain event " + event.getType());
                logger.debug("Domain event string" + event.toString());
                try {
                    logger.debug("domain.getname " + domain.getName());
                } catch (final LibvirtException e) {
                    logger.error("no domain name :( ", e);
                }
        }

        return 0;
    }
}