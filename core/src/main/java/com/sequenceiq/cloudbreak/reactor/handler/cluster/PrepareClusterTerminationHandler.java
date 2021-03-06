package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.reactor.api.event.EventSelectorUtil;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.PrepareClusterTerminationRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.PrepareClusterTerminationResult;
import com.sequenceiq.cloudbreak.reactor.handler.ReactorEventHandler;
import com.sequenceiq.cloudbreak.service.cluster.flow.AmbariClusterConnector;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class PrepareClusterTerminationHandler implements ReactorEventHandler<PrepareClusterTerminationRequest> {

    @Inject
    private EventBus eventBus;

    @Inject
    private AmbariClusterConnector ambariClusterConnector;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(PrepareClusterTerminationRequest.class);
    }

    @Override
    public void accept(Event<PrepareClusterTerminationRequest> event) {
        PrepareClusterTerminationResult result;
        try {
            ambariClusterConnector.prepareClusterToDekerberizing(event.getData().getStackId());
            result = new PrepareClusterTerminationResult(event.getData());
        } catch (Exception e) {
            result = new PrepareClusterTerminationResult(e.getMessage(), e, event.getData());
        }
        eventBus.notify(result.selector(), new Event(event.getHeaders(), result));
    }
}
