package com.sequenceiq.it.cloudbreak.newway;

import com.sequenceiq.cloudbreak.api.model.StackAuthenticationRequest;
import com.sequenceiq.cloudbreak.api.model.v2.NetworkV2Request;
import com.sequenceiq.cloudbreak.api.model.v2.TemplateV2Request;

import java.util.HashMap;
import java.util.Map;

public class GcpCloudProvider extends CloudProviderHelper {
    public static final String GCP = "gcp";

    public static final String GCP_CAPITAL = "GCP";

    static final String CREDNAME = "testgcpcred";

    static final String CREDDESC = "test credential";

    public GcpCloudProvider() {
    }

    @Override
    public CredentialEntity aValidCredential() {
        return Credential.isCreated()
                .withName(CREDNAME)
                .withDescription(CREDDESC)
                .withCloudPlatform(GCP_CAPITAL)
                .withParameters(azureCredentialDetails());
    }

    @Override
    String availabilityZone() {
        String availabilityZone = "europe-west1-b";
        String availabilityZoneParam = TestParameter.get("gcpAvailabilityZone");

        return availabilityZoneParam == null ? availabilityZone : availabilityZoneParam;
    }

    @Override
    String region() {
        String region = "europe-west1";
        String regionParam = TestParameter.get("gcpRegion");

        return regionParam == null ? region : regionParam;
    }

    @Override
    StackAuthenticationRequest stackauth() {
        StackAuthenticationRequest stackauth = new StackAuthenticationRequest();
        String defaultValue = "seq-master";
        String param = TestParameter.get("gcpPublicKeyId");
        stackauth.setPublicKeyId(param == null ? defaultValue : param);
        return stackauth;
    }

    @Override
    TemplateV2Request template() {
        TemplateV2Request t = new TemplateV2Request();
        String instanceTypeDefaultValue = "n1-highcpu-4";
        String instanceTypeParam = TestParameter.get("gcpInstanceType");
        t.setInstanceType(instanceTypeParam == null ? instanceTypeDefaultValue : instanceTypeParam);

        int volumeCountDefault = 1;
        String volumeCountParam = TestParameter.get("gcpInstanceVolumeCount");
        t.setVolumeCount(volumeCountParam == null ? volumeCountDefault : Integer.parseInt(volumeCountParam));

        int volumeSizeDefault = 10;
        String volumeSizeParam = TestParameter.get("gcpInstanceVolumeSize");
        t.setVolumeSize(volumeSizeParam == null ? volumeSizeDefault : Integer.parseInt(volumeSizeParam));

        String volumeTypeDefault = "n1-standard-4";
        String volumeTypeParam = TestParameter.get("gcpInstanceVolumeType");
        t.setVolumeType(volumeTypeParam == null ? volumeTypeDefault : volumeTypeParam);

        return t;
    }

    @Override
    public String getProviderName() {
        return GCP;
    }

    Map<String, Object> azureCredentialDetails() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("projectId", TestParameter.get("integrationtest.gcpcredential.projectId"));
        map.put("serviceAccountId", TestParameter.get("integrationtest.gcpcredential.serviceAccountId"));
        map.put("serviceAccountPrivateKey", TestParameter.get("integrationtest.gcpcredential.p12File"));
        return map;
    }

    @Override
    NetworkV2Request network() {
        NetworkV2Request network = new NetworkV2Request();
        network.setSubnetCIDR("10.0.0.0/16");
        return network;
    }
}