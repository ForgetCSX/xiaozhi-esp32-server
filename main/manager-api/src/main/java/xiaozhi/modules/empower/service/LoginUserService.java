package xiaozhi.modules.empower.service;

import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.sys.dto.SysUserDTO;

import java.util.Map;


public interface LoginUserService {

    public String createAgent(AgentCreateDTO dto,Long userId);

    public Map<String ,Object> acceptCustomer(String userName, String deviceCode,String agentId);

    Map<String ,Object> getAcceptCustomerInfo(String userName, String deviceCode,String agentId);

    SysUserDTO getSysUserDTO(String username);
}
