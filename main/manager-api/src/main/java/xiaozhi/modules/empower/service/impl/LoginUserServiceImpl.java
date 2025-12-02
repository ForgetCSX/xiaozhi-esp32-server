package xiaozhi.modules.empower.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dao.AgentDao;
import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.dto.AgentUpdateDTO;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.entity.AgentPluginMapping;
import xiaozhi.modules.agent.entity.AgentTemplateEntity;
import xiaozhi.modules.agent.service.AgentMcpAccessPointService;
import xiaozhi.modules.agent.service.AgentPluginMappingService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.AgentTemplateService;
import xiaozhi.modules.agent.vo.AgentInfoVO;
import xiaozhi.modules.device.dto.DeviceManualAddDTO;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.empower.service.LoginUserService;
import xiaozhi.modules.model.dto.ModelProviderDTO;
import xiaozhi.modules.model.service.ModelProviderService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dao.SysParamsDao;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.service.SysUserService;

import java.util.*;

@Service
public class LoginUserServiceImpl implements LoginUserService {


    private static final Logger logger = LoggerFactory.getLogger(LoginUserServiceImpl.class);

    @Autowired
    private AgentTemplateService agentTemplateService;

    @Autowired
    private ModelProviderService modelProviderService;
    @Autowired
    private AgentPluginMappingService agentPluginMappingService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private AgentService agentService;
    @Autowired
    private  AgentDao agentDao;

    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysUserTokenService sysUserTokenService;

    @Autowired
    private AgentMcpAccessPointService agentMcpAccessPointService;

    @Autowired
    private SysParamsDao sysParamsDao;
    //设置默认密码 admin123
    final String DEFAULT_PWD ="Admin123@";
    final String DEFAULT_NAME ="admin";
    final String agentName ="默认智能体";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createAgent(AgentCreateDTO dto,Long userId) {
        logger.info("开始创建智能体，用户ID: {}, 智能体名称: {}", userId, dto.getAgentName());

        try {
            // 转换为实体
            AgentEntity entity = ConvertUtils.sourceToTarget(dto, AgentEntity.class);
            logger.debug("已转换智能体DTO到实体对象");

            // 获取默认模板
            logger.info("正在获取默认智能体模板");
            AgentTemplateEntity template = agentTemplateService.getDefaultTemplate();
            if (template != null) {
                logger.info("成功获取默认模板，模板ID: {}", template.getId());
                // 设置模板中的默认值
                entity.setAsrModelId(template.getAsrModelId());
                entity.setVadModelId(template.getVadModelId());
                entity.setLlmModelId(template.getLlmModelId());
                entity.setVllmModelId(template.getVllmModelId());
                entity.setTtsModelId(template.getTtsModelId());
                entity.setTtsVoiceId(template.getTtsVoiceId());
                entity.setMemModelId(template.getMemModelId());
                entity.setIntentModelId(template.getIntentModelId());
                entity.setSystemPrompt(template.getSystemPrompt());
                entity.setSummaryMemory(template.getSummaryMemory());
                entity.setChatHistoryConf(template.getChatHistoryConf());
                entity.setLangCode(template.getLangCode());
                entity.setLanguage(template.getLanguage());
                logger.debug("已应用默认模板配置");
            } else {
                logger.warn("未找到默认智能体模板，将使用空配置");
            }

            // 设置用户ID和创建者信息
            entity.setUserId(userId);
            entity.setCreator(userId);
            entity.setCreatedAt(new Date());

            // 保存智能体
            logger.info("正在保存智能体信息");
            agentService.insert(entity);
            logger.info("智能体保存成功，智能体ID: {}", entity.getId());

            // 设置默认插件
            List<AgentPluginMapping> toInsert = new ArrayList<>();
            // 播放音乐、查天气、查新闻
            String[] pluginIds = new String[] { "SYSTEM_PLUGIN_MUSIC", "SYSTEM_PLUGIN_WEATHER",
                    "SYSTEM_PLUGIN_NEWSNOW" };
            logger.info("正在为智能体添加默认插件");
            for (String pluginId : pluginIds) {
                logger.debug("正在处理插件: {}", pluginId);
                ModelProviderDTO provider = modelProviderService.getById(pluginId);
                if (provider == null) {
                    logger.warn("未找到插件: {}", pluginId);
                    continue;
                }

                AgentPluginMapping mapping = new AgentPluginMapping();
                mapping.setPluginId(pluginId);

                Map<String, Object> paramInfo = new HashMap<>();
                List<Map<String, Object>> fields = JsonUtils.parseObject(provider.getFields(), List.class);
                if (fields != null) {
                    for (Map<String, Object> field : fields) {
                        paramInfo.put((String) field.get("key"), field.get("default"));
                    }
                }
                mapping.setParamInfo(JsonUtils.toJsonString(paramInfo));
                mapping.setAgentId(entity.getId());
                toInsert.add(mapping);
                logger.debug("已添加插件映射: {} 到智能体: {}", pluginId, entity.getId());
            }

            // 保存默认插件
            if (!toInsert.isEmpty()) {
                agentPluginMappingService.saveBatch(toInsert);
                logger.info("成功为智能体添加 {} 个默认插件", toInsert.size());
            } else {
                logger.warn("没有为智能体添加任何插件");
            }

            logger.info("智能体创建完成，ID: {}", entity.getId());
            return entity.getId();
        } catch (Exception e) {
            logger.error("创建智能体失败，用户ID: {}, 错误: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public Map<String ,Object> acceptCustomer(String userName, String deviceCode,String agentId) {
        Map<String ,Object> result = new HashMap<>();
        logger.info("开始处理客户注册请求，用户名: {}, 设备码: {}, 智能体ID: {}", userName, deviceCode, agentId);

        try {
            // 提供用户注册方案
            logger.info("检查用户是否已注册: {}", userName);

            SysUserDTO userDTO = new SysUserDTO();
            logger.info("开始注册新用户: {}, 设备码: {}", userName, deviceCode);
            userDTO.setUsername(userName);
            userDTO.setPassword(DEFAULT_PWD);
            sysUserService.save(userDTO);

            SysUserDTO sysUserDTO = sysUserService.getByUsername(userName);
            result.put("userId", sysUserDTO.getId());
            result.put("defaultPassWord", DEFAULT_PWD);
            logger.info("用户注册成功，用户ID: {}", sysUserDTO.getId());
            //客户没有定制智能体，使用默认的智能体
            if(StringUtils.isEmpty(agentId) || "null".equals(agentId)){
                logger.info("用户未指定智能体，将使用系统默认智能体");
                //获取系统初始智能体
                SysUserDTO systemDefaultUserDTO = this.getSysUserDTO(DEFAULT_NAME);
                logger.info("获取系统默认用户: {}", DEFAULT_NAME);

                // 如果系统默认用户不存在，创建默认用户和智能体
                if (systemDefaultUserDTO == null) {
                    logger.warn("系统默认用户不存在，正在创建默认用户: {}", DEFAULT_NAME);
                    SysUserDTO defaultUserDTO = new SysUserDTO();
                    defaultUserDTO.setUsername(DEFAULT_NAME);
                    defaultUserDTO.setPassword(DEFAULT_PWD);
                    sysUserService.save(defaultUserDTO);
                    systemDefaultUserDTO = this.getSysUserDTO(DEFAULT_NAME);
                    logger.info("默认用户创建成功，用户ID: {}", systemDefaultUserDTO.getId());

                    // 为默认用户创建默认智能体
                    logger.info("为默认用户创建默认智能体");
                    AgentCreateDTO agentCreateDTO = new AgentCreateDTO();
                    agentCreateDTO.setAgentName(agentName);
                    this.createAgent(agentCreateDTO, systemDefaultUserDTO.getId());
                    logger.info("默认智能体创建成功");
                }

                //绑定设备到用户对应默认智能体
                List<AgentDTO> defaultAgentDTOList = this.getUserAgents(systemDefaultUserDTO.getId());
                String defaultAgentId = "";
                for(AgentDTO agentDTO : defaultAgentDTOList){
                    defaultAgentId = agentDTO.getId();
                    break; // 只取第一个智能体
                }

                if(StringUtils.isEmpty(defaultAgentId)) {
                    logger.error("未能找到默认智能体");
                    throw new RenException(ErrorCode.DEFAULT_AGENT_NOT_FOUND);
                }

                logger.info("获取到默认智能体，ID: {}", defaultAgentId);
                logger.info("开始绑定设备到智能体，智能体ID: {}, 设备码: {}", defaultAgentId, deviceCode);

                DeviceManualAddDTO deviceManualAddDTO = new DeviceManualAddDTO();
                deviceManualAddDTO.setAgentId(defaultAgentId);
                deviceManualAddDTO.setBoard("bread-compact-wifi");
                deviceManualAddDTO.setAppVersion("v1.0");
                deviceManualAddDTO.setMacAddress(deviceCode);

                deviceService.manualAddDevice(sysUserDTO.getId(), deviceManualAddDTO);
                logger.info("设备绑定成功，用户ID: {}, 设备码: {}", sysUserDTO.getId(), deviceCode);

                result.put("macCode", deviceCode);

                // 获取系统配置信息
                logger.info("正在获取系统配置信息");
                String otaUrl = this.getUserSysParamsValue("server.ota");
                String websocketUrl = this.getUserSysParamsValue("server.websocket");
                String serverCcpEndpoint = this.getUserSysParamsValue("server.mcp_endpoint");

                result.put("otaUrl", otaUrl);
                result.put("websocketUrl", websocketUrl);
                result.put("serverCcpEndpoint", serverCcpEndpoint);

                // 获取MCP接入点
                logger.info("获取智能体MCP接入点信息");
                String agentMcpAccessAddress = agentMcpAccessPointService.getAgentMcpAccessAddress(defaultAgentId);
                result.put("serverMcpEndpoint", agentMcpAccessAddress);
                logger.info("MCP接入点获取成功: {}", agentMcpAccessAddress);
            }else {
                logger.info("用户指定了智能体，ID: {}", agentId);

                //获取用户定制的智能体
                AgentInfoVO agentInfoVO = agentService.getAgentById(agentId);
                if (agentInfoVO == null) {
                    logger.error("未找到指定的智能体: {}", agentId);
                    throw new RenException(ErrorCode.AGENT_NOT_FOUND);
                }

                logger.info("获取智能体成功，智能体名称: {}", agentInfoVO.getAgentName());

                logger.info("开始绑定设备到指定智能体，智能体ID: {}, 设备码: {}", agentInfoVO.getId(), deviceCode);

                DeviceManualAddDTO deviceManualAddDTO = new DeviceManualAddDTO();
                deviceManualAddDTO.setAgentId(agentInfoVO.getId());
                deviceManualAddDTO.setBoard("bread-compact-wifi");
                deviceManualAddDTO.setAppVersion("v1.0");
                deviceManualAddDTO.setMacAddress(deviceCode);

                deviceService.manualAddDevice(sysUserDTO.getId(), deviceManualAddDTO);
                logger.info("设备绑定成功，用户ID: {}, 设备码: {}", sysUserDTO.getId(), deviceCode);

                result.put("macCode", deviceCode);

                // 获取系统配置信息
                logger.info("正在获取系统配置信息");
                String otaUrl = this.getUserSysParamsValue("server.ota");
                String websocketUrl = this.getUserSysParamsValue("server.websocket");
                String serverCcpEndpoint = this.getUserSysParamsValue("server.mcp_endpoint");

                result.put("otaUrl", otaUrl);
                result.put("websocketUrl", websocketUrl);
                result.put("serverCcpEndpoint", serverCcpEndpoint);

                // 获取MCP接入点
                logger.info("获取智能体MCP接入点信息");
                String agentMcpAccessAddress = agentMcpAccessPointService.getAgentMcpAccessAddress(agentInfoVO.getId());
                result.put("serverMcpEndpoint", agentMcpAccessAddress);
                logger.info("MCP接入点获取成功: {}", agentMcpAccessAddress);
            }
        } catch (Exception e) {
            logger.error("客户注册异常，用户名: {}, 设备码: {}, 错误: {}", userName, deviceCode, e.getMessage(), e);
            // 重新抛出异常以保证事务回滚
            throw new RenException(ErrorCode.USER_REGISTER_DISABLED, e.getMessage());
        }

        logger.info("客户注册流程完成，用户ID: {}", result.get("userId"));
        return result;
    }

    @Override
    public Map<String, Object> getAcceptCustomerInfo(String userName, String deviceCode,String agentId) {
        Map<String ,Object> result = new HashMap<>();
        try {
            //检查当前用户是否已经注册
            logger.info("注册用户完成，获取用户信息");
            SysUserDTO sysUserDTO =sysUserService.getByUsername(userName);
            result.put("userId",sysUserDTO.getId());
            result.put("defaultPassWord","");
            result.put("macCode",deviceCode);
            logger.info("获取系统参数配置信息");
            String otaUrl =this.getUserSysParamsValue("server.ota");
            String websocketUrl =this.getUserSysParamsValue("server.websocket");
            String serverCcpEndpoint =this.getUserSysParamsValue("server.mcp_endpoint");
            result.put("otaUrl",otaUrl);
            result.put("websocketUrl",websocketUrl);
            result.put("serverCcpEndpoint",serverCcpEndpoint);
            //获取当前用户的智能体ID
            List<AgentDTO> agentDTOList = agentService.getUserAgents(sysUserDTO.getId());
            String havaAgentId ="";
            for(AgentDTO agentDTO : agentDTOList ){
                havaAgentId =agentDTO.getId();
            }
            //获取当前用户的MCP接入点配置
            String agentMcpAccessAddress = agentMcpAccessPointService.getAgentMcpAccessAddress(havaAgentId);
            result.put("serverMcpEndpoint",agentMcpAccessAddress);
        } catch (Exception e) {
            logger.error("获取客户信息异常，用户名: {}, 设备码: {}, 错误: {}", userName, deviceCode, e.getMessage(), e);
            throw new RenException(ErrorCode.USER_REGISTER_DISABLED, e.getMessage());
        }

        logger.info("获取已注册客户信息完成，用户ID: {}", result.get("userId"));
        return result;
    }

    /**
     * 获取用户的所有智能体
     */
    public List<AgentDTO> getUserAgents(Long userId) {
        logger.info("查询用户智能体列表，用户ID: {}", userId);

        try {
            QueryWrapper<AgentEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", userId);

            List<AgentEntity> agents = agentDao.selectList(wrapper);
            logger.info("查询到 {} 个智能体，用户ID: {}", agents.size(), userId);

            List<AgentDTO> agentDTOList = new ArrayList<>();
            for (AgentEntity agentEntity : agents) {
                AgentDTO agentDTO = new AgentDTO();
                agentDTO.setId(agentEntity.getId()); // 添加ID字段，之前漏掉了
                agentDTO.setAgentName(agentEntity.getAgentName());
                agentDTO.setLlmModelId(agentEntity.getLlmModelId());
                agentDTO.setMemModelId(agentEntity.getMemModelId());
                agentDTO.setIntentModelId(agentEntity.getIntentModelId()); // 修复重复设置intentModelId的问题
                agentDTO.setSystemPrompt(agentEntity.getSystemPrompt());
                agentDTO.setVllmModelId(agentEntity.getVllmModelId());
                agentDTO.setTtsModelId(agentEntity.getTtsModelId());
                agentDTO.setTtsVoiceId(agentEntity.getTtsVoiceId());
                agentDTO.setChatHistoryConf(agentEntity.getChatHistoryConf());
                agentDTOList.add(agentDTO);
            }

            return agentDTOList;
        } catch (Exception e) {
            logger.error("获取用户智能体列表失败，用户ID: {}, 错误: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    public String getUserSysParamsValue(String paramCode) {
        logger.info("获取系统参数值，参数编码: {}", paramCode);

        try {
            String sysParamsValue = sysParamsDao.getValueByCode(paramCode);
            logger.debug("系统参数获取成功，编码: {}, 值: {}", paramCode, sysParamsValue);
            return sysParamsValue;
        } catch (Exception e) {
            logger.error("获取系统参数失败，参数编码: {}, 错误: {}", paramCode, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public SysUserDTO getSysUserDTO(String username) {
        logger.info("开始查询用户信息，用户名: {}", username);

        try {
            QueryWrapper<SysUserEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            logger.debug("构建查询条件: {}", queryWrapper.getTargetSql());

            SysUserEntity entity = sysUserDao.selectOne(queryWrapper);

            if (entity == null) {
                logger.warn("未找到用户: {}", username);
                return null;
            }
            logger.info("查询到用户，ID: {}, 用户名: {}", entity.getId(), entity.getUsername());
            SysUserDTO dto = ConvertUtils.sourceToTarget(entity, SysUserDTO.class);
            logger.debug("用户实体转换为DTO完成");
            return dto;
        } catch (Exception e) {
            logger.error("查询用户信息异常，用户名: {}, 错误: {}", username, e.getMessage(), e);
            return null;
        }
    }
}
