package com.lespinel.camel.routes;


import com.lespinel.camel.commons.GatewayException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RestGatewayRoutes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel("direct:errorHandler").logHandled(true));

        rest("/{service}/{*path}")
                .post().to("direct:processRequest")
                .get().to("direct:processRequest")
                .put().to("direct:processRequest")
                .delete().to("direct:processRequest");

        from("direct:processRequest")
                .routeId("request-processor")
                .log("================= Processing New Request =================")
                .log("Service '${header.service}', path '${header.path}'")
                .to("direct:loadServiceConfig")
                .to("direct:executeRequest");

        from("direct:loadServiceConfig")
                .routeId("service-config-loader")
                .setHeader("ServiceConfig", method("gatewayConfig", "getServiceConfigByName(${header.service})"))
                .filter(header("ServiceConfig").isNull())
                    .setHeader("GatewayError", simple("ServiceConfig Not Found for Service name ${header.service}"))
                    .throwException(new GatewayException("ServiceConfig Not Found"))
                .end();

        from("direct:executeRequest")
                .routeId("request-executor")
                .setHeader(Exchange.HTTP_PATH, simple("${header.CamelHttpPath.replace({{camel.rest.context-path}}/${header.Service}, '')}"))
                .log("Executing http request to ${header.CamelHttpMethod} ${header.CamelHttpPath}")
                .toD("${header.ServiceConfig.baseUrl}?bridgeEndpoint=true")
                .log("Http Response Code From Service: ${header.CamelHttpResponseCode}");

        from("direct:errorHandler")
                .routeId("error-handler")
                .log(LoggingLevel.ERROR, "Exception: ${exception}")
                .process(exchange -> {
                    var exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    var gatewayError = exchange.getMessage().getHeader("GatewayError", "", String.class);
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                    exchange.getMessage().setBody(Map.of("message", exception.getMessage(), "details", gatewayError));
                })
                .marshal().json();

    }
}
