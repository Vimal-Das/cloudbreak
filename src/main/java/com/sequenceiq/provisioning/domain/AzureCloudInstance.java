package com.sequenceiq.provisioning.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

@Entity
public class AzureCloudInstance implements CloudInstance, ProvisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Integer clusterSize;

    @OneToOne
    private AzureInfra azureInfra;

    @ManyToOne
    private User user;

    public AzureCloudInstance() {

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getClusterSize() {
        return clusterSize;
    }

    public void setClusterSize(Integer clusterSize) {
        this.clusterSize = clusterSize;
    }

    public AzureInfra getAzureInfra() {
        return azureInfra;
    }

    public void setAzureInfra(AzureInfra azureInfra) {
        this.azureInfra = azureInfra;
    }

    @Override
    public CloudPlatform getPlatform() {
        return CloudPlatform.AZURE;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
