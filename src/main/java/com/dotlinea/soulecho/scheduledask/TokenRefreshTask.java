package com.dotlinea.soulecho.scheduledask;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.dotlinea.soulecho.config.AliyunConfig;
import com.dotlinea.soulecho.entity.Smartvoice;
import com.dotlinea.soulecho.service.SmartvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class TokenRefreshTask {

    // 地域ID
    private static final String REGIONID = "cn-shanghai";
    // 获取Token服务域名
    private static final String DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
    // API版本
    private static final String API_VERSION = "2019-02-28";
    // API名称
    private static final String REQUEST_ACTION = "CreateToken";

    // 响应参数
    private static final String KEY_TOKEN = "Token";
    private static final String KEY_ID = "Id";
    private static final String KEY_EXPIRETIME = "ExpireTime";

    private static final String CONFIG_PATH = "src/main/resources/application.properties";
    private static final String TOKEN_KEY = "custom.token";

    @Autowired
    private AliyunConfig aliyunConfig;

    @Autowired
    private SmartvoiceService smartvoiceService;

    @Scheduled(cron = "0 0 0,12 * * ?")
//    @Scheduled(fixedRate = 60000)
    public void GetToken() throws IOException, ClientException {

        String accessKeyId = aliyunConfig.getAccessKeyId();
        String accessKeySecret = aliyunConfig.getAccessKeySecret();

        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile(
                REGIONID,
                accessKeyId,
                accessKeySecret);

        IAcsClient client = new DefaultAcsClient(profile);
        CommonRequest request = new CommonRequest();
        request.setDomain(DOMAIN);
        request.setVersion(API_VERSION);
        request.setAction(REQUEST_ACTION);
        request.setMethod(MethodType.POST);
        request.setProtocol(ProtocolType.HTTPS);

        CommonResponse response = client.getCommonResponse(request);
        System.out.println(response.getData());
        if (response.getHttpStatus() == 200) {
            JSONObject result = JSON.parseObject(response.getData());
            String token = result.getJSONObject(KEY_TOKEN).getString(KEY_ID);
            long expireTime = result.getJSONObject(KEY_TOKEN).getLongValue(KEY_EXPIRETIME);
            System.out.println("获取到的Token： " + token + "，有效期时间戳(单位：秒): " + expireTime);
            // 将10位数的时间戳转换为北京时间
            String expireDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(expireTime * 1000));
            System.out.println("Token有效期的北京时间：" + expireDate);
            Smartvoice byId = smartvoiceService.getById(1);
            if (byId == null) {
                throw new RuntimeException();
            }
            byId.setToken(token);
            smartvoiceService.updateById(byId);
        } else {
            System.out.println("获取Token失败！");
        }
    }
}

