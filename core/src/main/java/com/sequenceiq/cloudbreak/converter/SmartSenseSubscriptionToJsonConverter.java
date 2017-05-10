package com.sequenceiq.cloudbreak.converter;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.model.SmartSenseSubscriptionJson;
import com.sequenceiq.cloudbreak.domain.SmartSenseSubscription;

@Component
public class SmartSenseSubscriptionToJsonConverter extends AbstractConversionServiceAwareConverter<SmartSenseSubscription, SmartSenseSubscriptionJson> {

    @Override
    public SmartSenseSubscriptionJson convert(SmartSenseSubscription source) {
        SmartSenseSubscriptionJson json = new SmartSenseSubscriptionJson();
        json.setId(source.getId());
        json.setSubscriptionId(source.getSubscriptionId());
        json.setOwner(source.getOwner());
        json.setAccount(source.getAccount());
        json.setPublicInAccount(source.isPublicInAccount());
        return json;
    }
}