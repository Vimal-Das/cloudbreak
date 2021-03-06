package com.sequenceiq.cloudbreak.converter;

import static com.gs.collections.impl.utility.StringIterate.isEmpty;
import static com.sequenceiq.cloudbreak.cloud.model.Platform.platform;
import static org.apache.commons.lang3.StringUtils.isNoneEmpty;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.sequenceiq.cloudbreak.api.model.DetailedStackStatus;
import com.sequenceiq.cloudbreak.api.model.InstanceGroupRequest;
import com.sequenceiq.cloudbreak.api.model.InstanceGroupType;
import com.sequenceiq.cloudbreak.api.model.StackRequest;
import com.sequenceiq.cloudbreak.cloud.model.Platform;
import com.sequenceiq.cloudbreak.cloud.model.Region;
import com.sequenceiq.cloudbreak.cloud.model.StackTags;
import com.sequenceiq.cloudbreak.common.type.OrchestratorConstants;
import com.sequenceiq.cloudbreak.controller.BadRequestException;
import com.sequenceiq.cloudbreak.core.CloudbreakException;
import com.sequenceiq.cloudbreak.core.bootstrap.service.OrchestratorTypeResolver;
import com.sequenceiq.cloudbreak.domain.AccountPreferences;
import com.sequenceiq.cloudbreak.domain.Credential;
import com.sequenceiq.cloudbreak.domain.FailurePolicy;
import com.sequenceiq.cloudbreak.domain.InstanceGroup;
import com.sequenceiq.cloudbreak.domain.Network;
import com.sequenceiq.cloudbreak.domain.Orchestrator;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.StackAuthentication;
import com.sequenceiq.cloudbreak.domain.StackStatus;
import com.sequenceiq.cloudbreak.domain.json.Json;
import com.sequenceiq.cloudbreak.service.account.AccountPreferencesService;
import com.sequenceiq.cloudbreak.service.stack.CloudParameterService;

@Component
public class StackRequestToStackConverter extends AbstractConversionServiceAwareConverter<StackRequest, Stack> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackRequestToStackConverter.class);

    @Inject
    @Qualifier("conversionService")
    private ConversionService conversionService;

    @Inject
    private CloudParameterService cloudParameterService;

    @Inject
    private OrchestratorTypeResolver orchestratorTypeResolver;

    @Inject
    private AccountPreferencesService accountPreferencesService;

    @Value("${cb.platform.default.regions:}")
    private String defaultRegions;

    @Override
    public Stack convert(StackRequest source) {
        Stack stack = new Stack();
        stack.setName(source.getName());
        stack.setDisplayName(source.getName());
        stack.setRegion(getRegion(source));
        setPlatform(source);
        stack.setCloudPlatform(source.getCloudPlatform());
        Map<String, String> sourceTags = source.getApplicationTags();
        stack.setTags(getTags(mergeTags(sourceTags, source.getUserDefinedTags(), getDefaultTags(source.getAccount()))));
        if (sourceTags != null && sourceTags.get("datalakeId") != null) {
            stack.setDatalakeId(Long.valueOf(String.valueOf(sourceTags.get("datalakeId"))));
        }
        StackAuthentication stackAuthentication = conversionService.convert(source.getStackAuthentication(), StackAuthentication.class);
        stack.setStackAuthentication(stackAuthentication);
        validateStackAuthentication(source);
        stack.setOwner(source.getOwner());
        stack.setAvailabilityZone(source.getAvailabilityZone());
        stack.setOnFailureActionAction(source.getOnFailureAction());
        stack.setStackStatus(new StackStatus(stack, DetailedStackStatus.PROVISION_REQUESTED.getStatus(), "", DetailedStackStatus.PROVISION_REQUESTED));
        Set<InstanceGroup> instanceGroups = convertInstanceGroups(source, stack);
        stack.setInstanceGroups(instanceGroups);
        stack.setFailurePolicy(getConversionService().convert(source.getFailurePolicy(), FailurePolicy.class));
        stack.setCreated(Calendar.getInstance().getTimeInMillis());
        stack.setPlatformVariant(source.getPlatformVariant());
        stack.setOrchestrator(getConversionService().convert(source.getOrchestrator(), Orchestrator.class));
        if (source.getCredential() != null) {
            stack.setCredential(getConversionService().convert(source.getCredential(), Credential.class));
        }
        if (source.getNetwork() != null) {
            stack.setNetwork(getConversionService().convert(source.getNetwork(), Network.class));
        }
        stack.setCustomDomain(source.getCustomDomain());
        stack.setCustomHostname(source.getCustomHostname());
        stack.setClusterNameAsSubdomain(source.isClusterNameAsSubdomain());
        stack.setHostgroupNameAsHostname(source.isHostgroupNameAsHostname());

        stack.setUuid(UUID.randomUUID().toString());
        return stack;
    }

    private void validateStackAuthentication(StackRequest source) {
        if (source.getStackAuthentication() == null) {
            throw new BadRequestException("You shoud define authentication for stack!");
        } else if (Strings.isNullOrEmpty(source.getStackAuthentication().getPublicKey())
                && Strings.isNullOrEmpty(source.getStackAuthentication().getPublicKeyId())) {
            throw new BadRequestException("You should define the publickey or publickeyid!");
        } else if (source.getStackAuthentication() != null && source.getStackAuthentication().getLoginUserName() != null) {
            throw new BadRequestException("You can not modify the default user!");
        }
    }

    private Json getTags(StackTags tags) {
        try {
            if (tags == null) {
                return new Json(new StackTags(new HashMap<>(), new HashMap<>(), new HashMap<>()));
            }
            return new Json(tags);
        } catch (Exception ignored) {
            throw new BadRequestException("Failed to convert dynamic tags.");
        }
    }

    private Map<String, String> getDefaultTags(String account) {
        Map<String, String> result = new HashMap<>();
        try {
            AccountPreferences pref = accountPreferencesService.getByAccount(account);
            if (pref != null && pref.getDefaultTags() != null && StringUtils.isNoneBlank(pref.getDefaultTags().getValue())) {
                result = pref.getDefaultTags().get(Map.class);
            }
        } catch (IOException e) {
            LOGGER.debug("Exception during reading default tags.", e);
        }
        return result;
    }

    private StackTags mergeTags(Map<String, String> applicationTags, Map<String, String> userDefinedTags, Map<String, String> defaultTags) {
        return new StackTags(userDefinedTags, applicationTags, defaultTags);
    }

    private String getRegion(StackRequest source) {
        boolean containerOrchestrator = false;
        try {
            containerOrchestrator = orchestratorTypeResolver.resolveType(source.getOrchestrator().getType()).containerOrchestrator();
        } catch (CloudbreakException ignored) {
            throw new BadRequestException("Orchestrator not supported.");
        }
        if (OrchestratorConstants.YARN.equals(source.getOrchestrator().getType())) {
            return OrchestratorConstants.YARN;
        }
        if (isEmpty(source.getRegion()) && !containerOrchestrator) {
            Map<Platform, Region> regions = Maps.newHashMap();
            if (isNoneEmpty(defaultRegions)) {
                for (String entry : defaultRegions.split(",")) {
                    String[] keyValue = entry.split(":");
                    regions.put(platform(keyValue[0]), Region.region(keyValue[1]));
                }
                Region platformRegion = regions.get(platform(source.getCloudPlatform()));
                if (platformRegion == null || isEmpty(platformRegion.value())) {
                    throw new BadRequestException(String.format("No default region specified for: %s. Region cannot be empty.", source.getCloudPlatform()));
                }
                return platformRegion.value();
            } else {
                throw new BadRequestException("No default region is specified. Region cannot be empty.");
            }
        }
        return source.getRegion();
    }

    private void setPlatform(StackRequest source) {
        if (isEmpty(source.getCloudPlatform())) {
            if (!isEmpty(source.getPlatformVariant())) {
                String platform = cloudParameterService.getPlatformByVariant(source.getPlatformVariant());
                source.setCloudPlatform(platform);
            }
        }
    }

    private Set<InstanceGroup> convertInstanceGroups(StackRequest source, Stack stack) {
        List<InstanceGroupRequest> instanceGroupRequests = source.getInstanceGroups();
        Set<InstanceGroup> convertedSet = new HashSet<>();
        for (InstanceGroupRequest instanceGroupRequest : instanceGroupRequests) {
            InstanceGroup instanceGroup = getConversionService().convert(instanceGroupRequest, InstanceGroup.class);
            if (instanceGroup != null) {
                convertedSet.add(getConversionService().convert(instanceGroupRequest, InstanceGroup.class));
            }
        }
        boolean gatewaySpecified = false;
        for (InstanceGroup instanceGroup : convertedSet) {
            instanceGroup.setStack(stack);
            if (!gatewaySpecified) {
                if (InstanceGroupType.GATEWAY.equals(instanceGroup.getInstanceGroupType())) {
                    gatewaySpecified = true;
                }
            }
        }
        boolean containerOrchestrator = false;
        try {
            containerOrchestrator = orchestratorTypeResolver.resolveType(source.getOrchestrator().getType()).containerOrchestrator();
        } catch (CloudbreakException ignored) {
            throw new BadRequestException("Orchestrator not supported.");
        }
        if (!gatewaySpecified && !containerOrchestrator) {
            throw new BadRequestException("Ambari server must be specified");
        }
        return convertedSet;
    }
}
