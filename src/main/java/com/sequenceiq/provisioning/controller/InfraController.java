package com.sequenceiq.provisioning.controller;

import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sequenceiq.provisioning.controller.json.CloudInstanceResult;
import com.sequenceiq.provisioning.controller.json.InfraRequest;
import com.sequenceiq.provisioning.domain.CloudPlatform;
import com.sequenceiq.provisioning.domain.User;
import com.sequenceiq.provisioning.repository.UserRepository;
import com.sequenceiq.provisioning.security.CurrentUser;
import com.sequenceiq.provisioning.service.CommonInfraService;
import com.sequenceiq.provisioning.service.InfraService;

@Controller
public class InfraController {

    @Resource
    private Map<CloudPlatform, InfraService> infraServices;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommonInfraService commonInfraService;

    @RequestMapping(method = RequestMethod.POST, value = "/infra")
    @ResponseBody
    public ResponseEntity<CloudInstanceResult> provisionCluster(@CurrentUser User user, @RequestBody @Valid InfraRequest infraRequest) {
        InfraService infraService = infraServices.get(infraRequest.getCloudPlatform());
        return new ResponseEntity<>(infraService.createInfra(userRepository.findOneWithLists(user.getId()), infraRequest), HttpStatus.CREATED);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/infra")
    @ResponseBody
    public ResponseEntity<Set<InfraRequest>> getAllCloudInstance(@CurrentUser User user) {
        return new ResponseEntity<>(commonInfraService.getAll(userRepository.findOneWithLists(user.getId())), HttpStatus.CREATED);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/infra/{infraId}")
    @ResponseBody
    public ResponseEntity<InfraRequest> getCloudInstance(@CurrentUser User user, @PathVariable Long infraId) {
        InfraRequest infraRequest = commonInfraService.get(infraId, userRepository.findOneWithLists(user.getId()));
        if (infraRequest == null) {
            new ResponseEntity<>(infraRequest, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(infraRequest, HttpStatus.CREATED);
    }

}
