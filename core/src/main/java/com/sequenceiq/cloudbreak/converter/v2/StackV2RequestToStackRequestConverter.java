package com.sequenceiq.cloudbreak.converter.v2;

import java.util.ArrayList;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;
import com.sequenceiq.cloudbreak.api.model.ClusterRequest;
import com.sequenceiq.cloudbreak.api.model.HostGroupRequest;
import com.sequenceiq.cloudbreak.api.model.InstanceGroupRequest;
import com.sequenceiq.cloudbreak.api.model.NetworkRequest;
import com.sequenceiq.cloudbreak.api.model.StackRequest;
import com.sequenceiq.cloudbreak.api.model.v2.InstanceGroupV2Request;
import com.sequenceiq.cloudbreak.api.model.v2.StackV2Request;
import com.sequenceiq.cloudbreak.controller.AuthenticatedUserService;
import com.sequenceiq.cloudbreak.converter.AbstractConversionServiceAwareConverter;
import com.sequenceiq.cloudbreak.service.credential.CredentialService;

@Component
public class StackV2RequestToStackRequestConverter extends AbstractConversionServiceAwareConverter<StackV2Request, StackRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackV2RequestToStackRequestConverter.class);

    @Inject
    @Qualifier("conversionService")
    private ConversionService conversionService;

    @Inject
    private CredentialService credentialService;

    @Inject
    private AuthenticatedUserService authenticatedUserService;

    @Override
    public StackRequest convert(StackV2Request source) {
        StackRequest stackRequest = new StackRequest();

        stackRequest.setName(source.getName());
        stackRequest.setAvailabilityZone(source.getAvailabilityZone());
        stackRequest.setRegion(source.getRegion());
        stackRequest.setPlatformVariant(source.getPlatformVariant());
        stackRequest.setOnFailureAction(source.getOnFailureAction());
        stackRequest.setAmbariVersion(source.getAmbariVersion());
        stackRequest.setHdpVersion(source.getHdpVersion());
        stackRequest.setParameters(source.getParameters());
        stackRequest.setCustomDomain(source.getCustomDomain());
        stackRequest.setCustomHostname(source.getCustomHostname());
        stackRequest.setClusterNameAsSubdomain(source.isClusterNameAsSubdomain());
        stackRequest.setHostgroupNameAsHostname(source.isHostgroupNameAsHostname());
        stackRequest.setApplicationTags(source.getApplicationTags());
        stackRequest.setDefaultTags(source.getDefaultTags());
        stackRequest.setUserDefinedTags(source.getUserDefinedTags());
        stackRequest.setOrchestrator(source.getOrchestrator());
        stackRequest.setInstanceGroups(new ArrayList<>());
        for (InstanceGroupV2Request instanceGroupV2Request : source.getInstanceGroups()) {
            InstanceGroupRequest convert = conversionService.convert(instanceGroupV2Request, InstanceGroupRequest.class);
            stackRequest.getInstanceGroups().add(convert);
        }
        stackRequest.setFailurePolicy(source.getFailurePolicy());
        stackRequest.setStackAuthentication(source.getStackAuthentication());

        stackRequest.setNetwork(conversionService.convert(source.getNetwork(), NetworkRequest.class));

        stackRequest.setImageCatalog(source.getImageCatalog());
        stackRequest.setImageCatalog(source.getImageCatalog());
        stackRequest.setImageId(source.getImageId());
        stackRequest.setFlexId(source.getFlexId());
        stackRequest.setCredentialName(source.getCredentialName());
        stackRequest.setOwner(Strings.isNullOrEmpty(source.getOwner()) ? authenticatedUserService.getCbUser().getUserId() : source.getOwner());
        stackRequest.setAccount(Strings.isNullOrEmpty(source.getAccount()) ? authenticatedUserService.getCbUser().getAccount() : source.getAccount());
        stackRequest.setOwnerEmail(Strings.isNullOrEmpty(source.getOwnerEmail()) ? authenticatedUserService.getCbUser().getUsername() : source.getOwnerEmail());
        if (source.getClusterRequest() != null) {
            stackRequest.setClusterRequest(conversionService.convert(source.getClusterRequest(), ClusterRequest.class));
            for (InstanceGroupV2Request instanceGroupV2Request : source.getInstanceGroups()) {
                HostGroupRequest convert = conversionService.convert(instanceGroupV2Request, HostGroupRequest.class);
                stackRequest.getClusterRequest().getHostGroups().add(convert);
            }
            stackRequest.getClusterRequest().setName(source.getName());
        }
        stackRequest.setCloudPlatform(credentialService.get(stackRequest.getCredentialName(), stackRequest.getAccount()).cloudPlatform());
        return stackRequest;
    }
}
