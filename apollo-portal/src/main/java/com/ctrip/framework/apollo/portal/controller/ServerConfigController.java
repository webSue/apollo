/*
 * Copyright 2022 Apollo Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.ctrip.framework.apollo.portal.controller;


import com.ctrip.framework.apollo.common.dto.ServerConfigDTO;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.PortalSettings;
import com.ctrip.framework.apollo.portal.entity.po.ServerConfig;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.repository.ServerConfigRepository;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心本身需要一些配置,这些配置放在数据库里面
 */
@RestController
public class ServerConfigController {

  private final ServerConfigRepository serverConfigRepository;
  private final UserInfoHolder userInfoHolder;
  private final AdminServiceAPI.ConfigServiceAPI configServiceAPI;
  private PortalSettings portalSettings;

  public ServerConfigController(final ServerConfigRepository serverConfigRepository
      , final AdminServiceAPI.ConfigServiceAPI configServiceAPI
      , final UserInfoHolder userInfoHolder
      , final PortalSettings portalSettings) {
    this.serverConfigRepository = serverConfigRepository;
    this.userInfoHolder = userInfoHolder;
    this.portalSettings = portalSettings;
    this.configServiceAPI = configServiceAPI;
  }

  @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
  @PostMapping("/server/config")
  public ServerConfig createOrUpdate(@Valid @RequestBody ServerConfig serverConfig) {
    String modifiedBy = userInfoHolder.getUser().getUserId();

    ServerConfig storedConfig = serverConfigRepository.findByKey(serverConfig.getKey());

    if (Objects.isNull(storedConfig)) {//create
      serverConfig.setDataChangeCreatedBy(modifiedBy);
      serverConfig.setDataChangeLastModifiedBy(modifiedBy);
      serverConfig.setId(0L);//为空，设置ID 为0，jpa执行新增操作
      return serverConfigRepository.save(serverConfig);
    }
    //update
    BeanUtils.copyEntityProperties(serverConfig, storedConfig);
    storedConfig.setDataChangeLastModifiedBy(modifiedBy);
    return serverConfigRepository.save(storedConfig);
  }

  @PostMapping("/server/config/addConfigService")
  public void addConfigService(@Valid @RequestBody ServerConfig serverConfig) throws SQLException {
    String modifiedBy = userInfoHolder.getUser().getUserId();

    List<Env> activeEnvs = portalSettings.getActiveEnvs();
    List<ServerConfigDTO> serverConfigDTOS = configServiceAPI.findAllConfigService(
        activeEnvs.get(0));

    boolean isExist = false;

    for (ServerConfigDTO item : serverConfigDTOS) {
      if (item.getKey().equals(serverConfig.getKey())) {
        isExist = true;
        serverConfig.setId(item.getId());
        break;
      }
    }

    serverConfig.setDataChangeCreatedBy(modifiedBy);
    serverConfig.setDataChangeLastModifiedBy(modifiedBy);
    if (isExist) {
      // 修改
      configServiceAPI.update(activeEnvs.get(0), serverConfig);
    } else {
      // 新增
      configServiceAPI.create(activeEnvs.get(0), serverConfig);
    }
  }

  /**
   * 获取所有PortalDB的配置信息
   *
   * @return
   */
  @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
  @GetMapping("/server/config/findAll")
  public List<ServerConfig> findAllServerConfig(
      @RequestParam(value = "offset", defaultValue = "0") int offset,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {

    Iterable<ServerConfig> all = serverConfigRepository.findAll();

    List<ServerConfig> serverConfigs = new ArrayList<>();

    for (ServerConfig item : all) {
      serverConfigs.add(item);
    }

    try {
      return serverConfigs.subList((offset - 1) * limit,
          offset * limit > serverConfigs.size() ? serverConfigs.size() : offset * limit);
    } catch (Exception ex) {
      return new ArrayList<>();
    }
  }

  @GetMapping("/server/config/findAllConfigService")
  public List<ServerConfigDTO> findAllConfigService(
      @RequestParam(value = "offset", defaultValue = "0") int offset,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {

    List<Env> activeEnvs = portalSettings.getActiveEnvs();
    List<ServerConfigDTO> serverConfigDTOS = configServiceAPI.findAllConfigService(
        activeEnvs.get(0));

    try {
      return serverConfigDTOS.subList((offset - 1) * limit,
          offset * limit > serverConfigDTOS.size() ? serverConfigDTOS.size() : offset * limit);
    } catch (Exception ex) {
      return new ArrayList<>();
    }
  }

  @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
  @GetMapping("/server/config/{key:.+}")
  public ServerConfig loadServerConfig(@PathVariable String key) {
    return serverConfigRepository.findByKey(key);
  }

}
