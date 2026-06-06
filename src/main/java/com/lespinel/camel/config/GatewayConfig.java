package com.lespinel.camel.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayConfig {
    private Map<String, ServiceConfig> services;

    public ServiceConfig getServiceConfigByName(String serviceName){
        return services.get(serviceName);
    }
}
