package xiaozhi.modules.empower.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.empower.service.LoginUserService;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.service.SysUserService;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "用户注册管理")
@RestController
@AllArgsConstructor
@RequestMapping("/admin")
public class LoginUserController {


    private static final Logger logger = LoggerFactory.getLogger(LoginUserController.class);
    @Autowired
    private LoginUserService loginUserService;
    @Autowired
    private  SysUserService sysUserService;

    @PostMapping("/accept")
    @Operation(summary = "提供用户注册绑定设备方案")
    public Result<Map<String ,Object>> acceptCustomer(@RequestBody AcceptRequest request) {
        try {

            if(StringUtils.isBlank(request.getUserName())){
                throw new RenException(ErrorCode.USER_REGISTER_DISABLED);
            }
            if(StringUtils.isBlank(request.getDeviceCode())){
                throw new RenException(ErrorCode.USER_REGISTER_DISABLED);
            }
            //检查当前用户是否已经注册
            SysUserDTO userDTO = sysUserService.getByUsername(request.getUserName());
            if (userDTO != null) {
                logger.info("已经注册用户:{},获取分配的智能体，绑定设备开始,设备ID:{}",request.getUserName(),request.getDeviceCode());
                Map<String ,Object> acceptCustomer=loginUserService.getAcceptCustomerInfo(request.getUserName(), request.getDeviceCode());
                logger.info("已经注册用户信息");
                return new Result<Map<String ,Object>>().ok(acceptCustomer);
            }else{
                logger.info("配置注册用户:{},分配智能体，绑定设备开始,设备ID:{}",request.getUserName(),request.getDeviceCode());
                Map<String ,Object> acceptCustomer=loginUserService.acceptCustomer(request.getUserName(), request.getDeviceCode());
                logger.info("注册用户完成，分配完成，绑定完成");
                return new Result<Map<String ,Object>>().ok(acceptCustomer);
            }
        }catch (Exception e){
            logger.error("客户注册异常，无法注册，检查注册信息{}",e.getMessage());
        }
        return new Result<>();
    }

    @Data
    static class AcceptRequest {

        @NotNull
        private String userName;
        @NotNull
        private String deviceCode;
    }
}