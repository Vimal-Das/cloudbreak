package com.sequenceiq.cloudbreak.controller;

import static com.sequenceiq.cloudbreak.cloud.model.component.StackRepoDetails.CUSTOM_VDF_REPO_KEY;
import static com.sequenceiq.cloudbreak.cloud.model.component.StackRepoDetails.VDF_REPO_KEY_PREFIX;
import static com.sequenceiq.cloudbreak.service.cluster.flow.AmbariRepositoryVersionService.AMBARI_VERSION_2_6_0_0;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.sequenceiq.cloudbreak.api.model.AmbariDatabaseDetailsJson;
import com.sequenceiq.cloudbreak.api.model.AmbariRepoDetailsJson;
import com.sequenceiq.cloudbreak.api.model.AmbariStackDetailsJson;
import com.sequenceiq.cloudbreak.api.model.ClusterRequest;
import com.sequenceiq.cloudbreak.cloud.model.AmbariDatabase;
import com.sequenceiq.cloudbreak.cloud.model.AmbariRepo;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDFEntries;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDFInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDPEntries;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDPInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.StackInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.StackRepoDetails;
import com.sequenceiq.cloudbreak.common.model.user.IdentityUser;
import com.sequenceiq.cloudbreak.common.type.ComponentType;
import com.sequenceiq.cloudbreak.controller.validation.filesystem.FileSystemValidator;
import com.sequenceiq.cloudbreak.converter.spi.CredentialToCloudCredentialConverter;
import com.sequenceiq.cloudbreak.core.CloudbreakImageNotFoundException;
import com.sequenceiq.cloudbreak.domain.Blueprint;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.ClusterComponent;
import com.sequenceiq.cloudbreak.domain.Component;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.json.Json;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.service.ComponentConfigProvider;
import com.sequenceiq.cloudbreak.service.blueprint.BlueprintService;
import com.sequenceiq.cloudbreak.service.blueprint.BlueprintUtils;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.cluster.flow.AmbariRepositoryVersionService;
import com.sequenceiq.cloudbreak.service.decorator.ClusterDecorator;
import com.sequenceiq.cloudbreak.util.JsonUtil;

@org.springframework.stereotype.Component
public class ClusterCreationSetupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCreationSetupService.class);

    @Inject
    private FileSystemValidator fileSystemValidator;

    @Inject
    private CredentialToCloudCredentialConverter credentialToCloudCredentialConverter;

    @Inject
    @Named("conversionService")
    private ConversionService conversionService;

    @Inject
    private ClusterDecorator clusterDecorator;

    @Inject
    private ClusterService clusterService;

    @Inject
    private ComponentConfigProvider componentConfigProvider;

    @Inject
    private BlueprintUtils blueprintUtils;

    @Inject
    private DefaultHDPEntries defaultHDPEntries;

    @Inject
    private DefaultHDFEntries defaultHDFEntries;

    @Inject
    private BlueprintService blueprintService;

    @Inject
    private AmbariRepositoryVersionService ambariRepositoryVersionService;

    public void validate(ClusterRequest request, Stack stack, IdentityUser user) {
        validate(request, null, stack, user);
    }

    public void validate(ClusterRequest request, CloudCredential cloudCredential, Stack stack, IdentityUser user) {
        if (request.getEnableSecurity() && request.getKerberos() == null) {
            throw new BadRequestException("If the security is enabled the kerberos parameters cannot be empty");
        }
        MDCBuilder.buildUserMdcContext(user);
        CloudCredential credential = cloudCredential;
        if (credential == null) {
            credential = credentialToCloudCredentialConverter.convert(stack.getCredential());
        }
        fileSystemValidator.validateFileSystem(stack.cloudPlatform(), credential, request.getFileSystem());
    }

    public Cluster prepare(ClusterRequest request, Stack stack, IdentityUser user) throws Exception {
        return prepare(request, stack, null, user);
    }

    public Cluster prepare(ClusterRequest request, Stack stack, Blueprint blueprint, IdentityUser user) throws Exception {
        String stackName = stack.getName();

        long start = System.currentTimeMillis();
        Cluster cluster = conversionService.convert(request, Cluster.class);
        LOGGER.info("Cluster conversion took {} ms for stack {}", System.currentTimeMillis() - start, stackName);

        start = System.currentTimeMillis();
        cluster = clusterDecorator.decorate(cluster, request, blueprint, user, stack);
        LOGGER.info("Cluster object decorated in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

        start = System.currentTimeMillis();
        List<ClusterComponent> components = new ArrayList<>();
        Set<Component> allComponent = componentConfigProvider.getAllComponentsByStackIdAndType(stack.getId(),
                Sets.newHashSet(ComponentType.AMBARI_REPO_DETAILS, ComponentType.HDP_REPO_DETAILS, ComponentType.IMAGE));
        Optional<Component> stackAmbariRepoConfig = allComponent.stream().filter(c -> c.getComponentType().equals(ComponentType.AMBARI_REPO_DETAILS)
                && c.getName().equalsIgnoreCase(ComponentType.AMBARI_REPO_DETAILS.name())).findAny();
        Optional<Component> stackHdpRepoConfig = allComponent.stream().filter(c -> c.getComponentType().equals(ComponentType.HDP_REPO_DETAILS)
                && c.getName().equalsIgnoreCase(ComponentType.HDP_REPO_DETAILS.name())).findAny();
        Optional<Component> stackImageComponent = allComponent.stream().filter(c -> c.getComponentType().equals(ComponentType.IMAGE)
                && c.getName().equalsIgnoreCase(ComponentType.IMAGE.name())).findAny();
        ClusterComponent ambariRepoConfig = determineAmbariRepoConfig(stackAmbariRepoConfig, request.getAmbariRepoDetailsJson(), cluster);
        components.add(ambariRepoConfig);
        ClusterComponent hdpRepoConfig = determineHDPRepoConfig(blueprint, stack.getId(), stackHdpRepoConfig, request, cluster, user, stackImageComponent);
        components.add(hdpRepoConfig);

        checkVDFFile(ambariRepoConfig, hdpRepoConfig, stackName);

        components.add(addAmbariDatabaseConfig(request.getAmbariDatabaseDetails(), cluster));
        LOGGER.info("Cluster components saved in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

        start = System.currentTimeMillis();
        Cluster savedCluster = clusterService.create(user, stack, cluster, components);
        LOGGER.info("Cluster object creation took {} ms for stack {}", System.currentTimeMillis() - start, stackName);

        return savedCluster;
    }

    private void checkVDFFile(ClusterComponent ambariRepoConfig, ClusterComponent hdpRepoConfig, String stackName) throws IOException {
        AmbariRepo ambariRepo = ambariRepoConfig.getAttributes().get(AmbariRepo.class);

        if (ambariRepositoryVersionService.isVersionNewerOrEqualThanLimited(ambariRepo::getVersion, AMBARI_VERSION_2_6_0_0)
                && !containsVDFUrl(hdpRepoConfig.getAttributes())) {
            throw new BadRequestException(String.format("Couldn't determine any VDF file for the stack: %s", stackName));
        }
    }

    private ClusterComponent determineAmbariRepoConfig(Optional<Component> stackAmbariRepoConfig,
            AmbariRepoDetailsJson ambariRepoDetailsJson, Cluster cluster) throws JsonProcessingException {
        // If it is not predefined in image catalog
        ClusterComponent component;
        if (!stackAmbariRepoConfig.isPresent()) {
            if (ambariRepoDetailsJson == null) {
                ambariRepoDetailsJson = new AmbariRepoDetailsJson();
            }
            AmbariRepo ambariRepo = conversionService.convert(ambariRepoDetailsJson, AmbariRepo.class);
            component = new ClusterComponent(ComponentType.AMBARI_REPO_DETAILS, new Json(ambariRepo), cluster);
        } else {
            component = new ClusterComponent(ComponentType.AMBARI_REPO_DETAILS, stackAmbariRepoConfig.get().getAttributes(), cluster);
        }
        return component;
    }

    private ClusterComponent determineHDPRepoConfig(Blueprint blueprint, long stackId, Optional<Component> stackHdpRepoConfig,
            ClusterRequest request, Cluster cluster, IdentityUser user, Optional<Component> stackImageComponent)
            throws JsonProcessingException {

        Json stackRepoDetailsJson;
        if (!stackHdpRepoConfig.isPresent()) {
            AmbariStackDetailsJson ambariStackDetails = request.getAmbariStackDetails();
            if (ambariStackDetails != null) {
                ambariStackDetails = setOsTypeFromImageIfMissing(cluster, stackImageComponent, ambariStackDetails);
                StackRepoDetails stackRepoDetails = conversionService.convert(ambariStackDetails, StackRepoDetails.class);
                stackRepoDetailsJson = new Json(stackRepoDetails);
            } else {
                StackRepoDetails stackRepoDetails = SerializationUtils.clone(defaultHDPInfo(blueprint, request, user).getRepo());
                Optional<String> vdfUrl = getVDFUrlByOsType(stackId, stackRepoDetails);
                vdfUrl.ifPresent(s -> stackRepoDetails.getStack().put(CUSTOM_VDF_REPO_KEY, s));
                stackRepoDetailsJson = new Json(stackRepoDetails);
            }
        } else {
            stackRepoDetailsJson = stackHdpRepoConfig.get().getAttributes();
        }

        return new ClusterComponent(ComponentType.HDP_REPO_DETAILS, stackRepoDetailsJson, cluster);
    }

    private AmbariStackDetailsJson setOsTypeFromImageIfMissing(Cluster cluster, Optional<Component> stackImageComponent,
            AmbariStackDetailsJson ambariStackDetails) {
        if (org.apache.commons.lang3.StringUtils.isBlank(ambariStackDetails.getOs()) && stackImageComponent.isPresent()) {
            try {
                Image image = stackImageComponent.get().getAttributes().get(Image.class);
                if (org.apache.commons.lang3.StringUtils.isNotBlank(image.getOsType())) {
                    ambariStackDetails.setOs(image.getOsType());
                }
            } catch (IOException e) {
                LOGGER.error("Couldn't convert image component for stack: {} {}", cluster.getStack().getId(), cluster.getStack().getName(), e);
            }
        }
        return ambariStackDetails;
    }

    private boolean containsVDFUrl(Json stackRepoDetailsJson) {
        try {
            return stackRepoDetailsJson.get(StackRepoDetails.class).getStack().containsKey(CUSTOM_VDF_REPO_KEY);
        } catch (IOException e) {
            LOGGER.error("JSON parse error.", e);
        }
        return false;
    }

    private StackInfo defaultHDPInfo(Blueprint blueprint, ClusterRequest request, IdentityUser user) {
        try {
            JsonNode root = getBlueprintJsonNode(blueprint, request, user);
            if (root != null) {
                String stackVersion = blueprintUtils.getBlueprintHdpVersion(root);
                String stackName = blueprintUtils.getBlueprintStackName(root);
                if ("HDF".equalsIgnoreCase(stackName)) {
                    LOGGER.info("Stack name is HDF, use the default HDF repo for version: " + stackVersion);
                    for (Entry<String, DefaultHDFInfo> entry : defaultHDFEntries.getEntries().entrySet()) {
                        if (entry.getKey().equals(stackVersion)) {
                            return entry.getValue();
                        }
                    }
                } else {
                    LOGGER.info("Stack name is HDP, use the default HDP repo for version: " + stackVersion);
                    for (Entry<String, DefaultHDPInfo> entry : defaultHDPEntries.getEntries().entrySet()) {
                        if (entry.getKey().equals(stackVersion)) {
                            return entry.getValue();
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Can not initiate default hdp info: ", ex);
        }
        return defaultHDPEntries.getEntries().values().iterator().next();
    }

    private JsonNode getBlueprintJsonNode(Blueprint blueprint, ClusterRequest request, IdentityUser user) throws IOException {
        JsonNode root;
        if (blueprint != null) {
            root = JsonUtil.readTree(blueprint.getBlueprintText());
        } else {
            // Backward compatibility to V1 cluster API
            if (request.getBlueprintId() != null) {
                root = JsonUtil.readTree(blueprintService.get(request.getBlueprintId()).getBlueprintText());
            } else if (request.getBlueprintName() != null) {
                root = JsonUtil.readTree(blueprintService.get(request.getBlueprintName(), user.getAccount()).getBlueprintText());
            } else {
                root = JsonUtil.readTree(request.getBlueprint().getAmbariBlueprint());
            }
        }
        return root;
    }

    private ClusterComponent addAmbariDatabaseConfig(AmbariDatabaseDetailsJson ambariRepoDetailsJson, Cluster cluster)
            throws JsonProcessingException {
        if (ambariRepoDetailsJson == null) {
            ambariRepoDetailsJson = new AmbariDatabaseDetailsJson();
        }
        AmbariDatabase ambariDatabase = conversionService.convert(ambariRepoDetailsJson, AmbariDatabase.class);
        return new ClusterComponent(ComponentType.AMBARI_DATABASE_DETAILS, new Json(ambariDatabase), cluster);
    }

    private Optional<String> getVDFUrlByOsType(Long stackId, StackRepoDetails stackRepoDetails) {
        String vdfStackRepoKeyFilter = VDF_REPO_KEY_PREFIX;
        try {
            Image image = componentConfigProvider.getImage(stackId);
            if (!StringUtils.isEmpty(image.getOsType())) {
                vdfStackRepoKeyFilter += image.getOsType();
            }
        } catch (CloudbreakImageNotFoundException e) {
            LOGGER.error(String.format("Could not get Image Component for stack: '%s'.", stackId), e);
        }

        final String filter = vdfStackRepoKeyFilter;
        return stackRepoDetails.getStack().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(filter))
                .map(Map.Entry::getValue)
                .findFirst();
    }

}