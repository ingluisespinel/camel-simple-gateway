# API Gateway con Apache Camel y Spring Boot: Una Puerta de Entrada Inteligente para tus Microservicios

## Introducción

Cuando trabajamos con microservicios, uno de los patrones más comunes y necesarios es el **API Gateway**. Este componente actúa como un punto único de entrada hacia nuestros servicios backend, proporcionando capacidades como enrutamiento dinámico, balanceo de carga, seguridad, y transformación de mensajes.

En este artículo exploraremos cómo construir un **API Gateway ligero y dinámico** utilizando **Apache Camel** y **Spring Boot**, dos tecnologías que se complementan de manera excelente para tareas de integración y enrutamiento.

## ¿Por qué Apache Camel para un API Gateway?

Apache Camel es un framework de integración maduro que implementa los patrones de Integración Empresarial (EIP). Su DSL (Domain Specific Language) permite definir rutas de enrutamiento de manera intuitiva. Combinado con Spring Boot, obtenemos:

- **Configuración automática** (auto-configuration)
- **Inyección de dependencias**
- **Propiedades externalizadas** via `application.properties`
- **Actuator** para monitoreo de rutas en tiempo real
- **Facilidad de prueba** con `CamelSpringBootTest`

## Casos Reales: Apache Camel como API Gateway en Producción

Apache Camel no es solo un juguete académico — está siendo utilizado como API Gateway en producción por gobiernos, empresas Fortune 500, y startups por igual. Aquí hay algunos ejemplos concretos:

### 🏛️ CAPI — Gobierno Europeo (100+ servicios, 10M+ peticiones/día)

Rodrigo Serra Coelho, Arquitecto de Software en la Comisión Europea, desarrolló **CAPI (Camel API Gateway)** para una institución gubernamental que operaba un entorno híbrido complejo (on-premises + cloud). Venían de un producto API Gateway comercial conocido, pero era "demasiado pesado — el footprint era grande, la gestión de configuración a escala era dolorosa, y los costos de licencia seguían creciendo".

**Métricas del mundo real:**
- Enruta **más de 100 servicios** en producción
- Maneja **más de 10 millones de peticiones API por día**
- Footprint de runtime: **~120 MB** (contra 1 GB+ de la solución anterior)
- Registro de servicios **zero-config** via Consul
- Seguridad: OAuth2/OIDC + Open Policy Agent
- Observabilidad: OpenTelemetry + Prometheus

> *"Elegí Apache Camel porque es uno de los frameworks de integración más battle-tested del ecosistema Java, con capacidades de enrutamiento dinámico que eran exactamente lo que necesitaba."* — Rodrigo Serra Coelho

Fuente: [Apache Camel Blog — Your REST APIs already speak MCP](https://camel.apache.org/blog/2026/03/CAPIGateway/)

### 🏪 Red Hat CoolStore Demo — Gateway con Enricher EIP

Red Hat publicó un tutorial oficial donde implementan el patrón **API Gateway** usando Apache Camel para un e-commerce. El gateway:
- Agrega datos del catálogo de productos con el inventario en una sola respuesta
- Usa `split()` + `enrich()` con el patrón **Content Enricher** EIP
- Implementa **circuit breaker** con Hystrix
- Se despliega en **OpenShift** con enrutamiento por paths

```java
// Fragmento real del gateway CoolStore
.hystrix()
.to("http://catalog-service:8080/api/products")
.onFallback().to("direct:productFallback")
.end()
.unmarshal()
.split(body()).parallelProcessing()
.enrich("direct:inventory", new InventoryEnricher())
```

Fuente: [Red Hat Developers — Microservices with Apache Camel](https://developers.redhat.com/blog/2016/11/07/microservices-comparing-diy-with-apache-camel)

### 💰 Migración MuleSoft → Apache Camel ($350K → $50K/año)

Un estudio de caso documenta la migración de una empresa D2C (consumer accessories, $100M-$300M ingresos anuales) desde MuleSoft hacia Apache Camel. El resultado:

| Concepto | Antes (MuleSoft) | Después (Camel + Spring Boot) |
|---|---|---|
| Costo anual | ~$350,000 | ~$50,000 |
| Runtime | CloudHub (12 apps) | Kubernetes (AKS) + GitOps |
| Flujos | 528 flows | 528 rutas Camel |
| Sistemas externos | 16 | 16 |
| Middleware | Anypoint MQ | Azure Service Bus |

**Ahorro anual: ~$300,000**.

Fuente: [Unmule — MuleSoft to Apache Camel Migration](https://unmule.com/case-studies/mulesoft-to-apache-camel-migration)

### 🔧 Otros Proyectos y Referencias

- **Assimbly Gateway** — Un message gateway open source basado en Apache Camel ([assimbly.nl](https://assimbly.nl))
- **Guidewire Integration Framework** — Framework de integración para la industria de seguros, basado en Apache Camel
- **3scale APIcast Camel Service** — Integración de Red Hat 3scale con Camel para proxy con OAuth2 y Keycloak
- **Piotr Minkowski — Advanced Microservices with Camel** — API Gateway con Rest DSL + Consul + ServiceCall EIP ([blog](https://piotrminkowski.com/2017/04/04/advanced-microservices-with-apache-camel/))

Como dijo Gaurang Malvankar en un artículo reciente de Medium: *"Apache Camel no resolverá todos los problemas. Pero si estás lidiando con múltiples sistemas, lógica de enrutamiento compleja, y vendors que quieren cobrarte por cada llamada API, Camel te da una forma gratuita, battle-tested y vendor-neutral de retomar el control."*

---

## Vista General del Proyecto

Nuestro proyecto, `camel-simple-gateway`, expone un único endpoint REST: `/{service}/{*path}`. A partir del nombre del servicio en la URL, busca la configuración correspondiente y redirige la petición al backend adecuado.

### Dependencias Clave (pom.xml)

```xml
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-platform-http-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-http-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-jackson-starter</artifactId>
</dependency>
```

Usamos Camel `4.18.2` y Spring Boot `3.5.13`. Reemplazamos Tomcat por **Undertow** como servidor embebido.

---

## Arquitectura del Gateway

### 1. Punto de Entrada: RestGatewayRoutes

```java
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
                .log("Service '${header.service}', path '${header.path}'")
                .to("direct:loadServiceConfig")
                .to("direct:executeRequest");
    }
}
```

**¿Qué ocurre aquí?**

- Definimos un endpoint REST dinámico: `/{service}/{*path}` captura el nombre del servicio y el resto de la ruta.
- Soportamos los 4 métodos HTTP principales (GET, POST, PUT, DELETE).
- Todas las peticiones fluyen a través de `direct:processRequest`, que primero carga la configuración del servicio y luego ejecuta la petición.

### 2. Carga Dinámica de Configuración

```java
from("direct:loadServiceConfig")
        .routeId("service-config-loader")
        .setHeader("ServiceConfig", method("gatewayConfig", "getServiceConfigByName(${header.service})"))
        .filter(header("ServiceConfig").isNull())
            .setHeader("GatewayError", simple("ServiceConfig Not Found for Service name ${header.service}"))
            .throwException(new GatewayException("ServiceConfig Not Found"))
        .end();
```

Esta ruta es el **corazón del enrutamiento dinámico**. Usa `method()` para invocar el bean `gatewayConfig` y buscar la configuración del servicio por nombre. Si no existe, lanza una excepción personalizada.

### 3. Ejecución de la Petición

```java
from("direct:executeRequest")
        .routeId("request-executor")
        .setHeader(Exchange.HTTP_PATH, simple("${header.CamelHttpPath.replace({{camel.rest.context-path}}/${header.Service}, '')}"))
        .log("Executing http request to ${header.CamelHttpMethod} ${header.CamelHttpPath}")
        .toD("${header.ServiceConfig.baseUrl}?bridgeEndpoint=true")
        .log("Http Response Code From Service: ${header.CamelHttpResponseCode}");
```

**Aspecto clave:** Usamos `toD` (Dynamic To) para construir la URL de destino en tiempo de ejecución a partir del `baseUrl` almacenado en la configuración. El parámetro `bridgeEndpoint=true` le indica a Camel que **no sobrescriba** los headers de la URL original, preservando el path y los query parameters de la petición entrante.

También limpiamos el path eliminando el prefijo del context-path y el nombre del servicio, de modo que la petición al backend conserve solo la parte relevante.

---

## Configuración de Servicios

### GatewayConfig — El Registro de Servicios

```java
@Data
@Component
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayConfig {
    private Map<String, ServiceConfig> services;

    public ServiceConfig getServiceConfigByName(String serviceName){
        return services.get(serviceName);
    }
}
```

Usamos `@ConfigurationProperties` para mapear las propiedades de `application.properties` directamente a un `Map<String, ServiceConfig>`.

### ServiceConfig — POJO Simple

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfig {
    private String baseUrl;
}
```

### application.properties

```properties
app.gateway.services.objects-service.base-url=https://api.restful-api.dev
app.gateway.services.pokemons.base-url=https://pokeapi.co/api/v2

camel.rest.context-path=/camel-gateway
```

Cada servicio registrado tiene un nombre clave y una URL base. El gateway usa ese nombre clave para enrutar: una petición a `/camel-gateway/pokemons/pokemon/ditto` se traduce a `https://pokeapi.co/api/v2/pokemon/ditto`.

---

## Manejo de Errores

### Dead Letter Channel

```java
errorHandler(deadLetterChannel("direct:errorHandler").logHandled(true));
```

Usamos el patrón **Dead Letter Channel** de EIP. Cualquier excepción no capturada en las rutas principales se redirige a `direct:errorHandler`.

### Error Handler Route

```java
from("direct:errorHandler")
        .routeId("error-handler")
        .log(LoggingLevel.ERROR, "Exception: ${exception}")
        .process(exchange -> {
            var exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
            exchange.getMessage().setBody(Map.of("message", exception.getMessage()));
        })
        .marshal().json();
```

Capturamos la excepción, establecemos un código HTTP 500 y devolvemos un JSON descriptivo.

### Fallback con Spring

```java
@RestControllerAdvice
public class ErrorController {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("description", e.getMessage()));
    }
}
```

Como respaldo, un `@RestControllerAdvice` de Spring captura cualquier excepción que escape del contexto de Camel.

---

## Probando el Gateway

Camel ofrece un excelente soporte para testing con `CamelSpringBootTest` y `AdviceWith`:

```java
@SpringBootTest
@CamelSpringBootTest
public class CamelGatewayApplicationTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Test
    public void test() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:stream:out", MockEndpoint.class);

        AdviceWith.adviceWith(camelContext, "hello",
                r -> {
                    r.replaceFromWith("direct:start");
                    r.mockEndpoints("stream*");
                });

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        producerTemplate.sendBody("direct:start", null);

        mock.assertIsSatisfied();
    }
}
```

`AdviceWith` permite **interceptar y modificar rutas en tiempo de prueba** sin tocar el código de producción — una característica increíblemente poderosa.

---

## Probando el Gateway con Peticiones Reales

El proyecto incluye un archivo `example-requests.http` que puedes usar directamente desde IntelliJ IDEA:

### Obtener un objeto por ID
```
GET http://127.0.0.1:8080/camel-gateway/objects-service/objects?id=3
```

### Obtener un Pokémon por nombre
```
GET http://127.0.0.1:8080/camel-gateway/pokemons/pokemon/ditto
```

### Servicio inexistente (prueba de error)
```
GET http://127.0.0.1:8080/camel-gateway/pokemonssss/pokemon/ditto
```

### POST a un servicio backend
```
POST http://127.0.0.1:8080/camel-gateway/objects-service/objects
Content-Type: application/json

{
  "name": "Camel Gateway",
  "data": {
    "year": 2019,
    "price": 1849.99,
    "CPU model": "Intel Core i9"
  }
}
```

### Monitoreo con Actuator

El proyecto expone endpoints de Actuator que permiten monitorear las rutas de Camel en tiempo real:

```
GET http://127.0.0.1:8080/actuator/camelroutes
```

Esto devuelve información sobre cada ruta: su identificador, estado (started/stopped), y estadísticas de intercambio.

---

## Conclusión

Hemos construido un **API Gateway dinámico** con Apache Camel y Spring Boot en menos de 100 líneas de código. Las capacidades que destacan son:

1. **Enrutamiento dinámico**: El servicio destino se resuelve en tiempo de ejecución desde la configuración, permitiendo agregar nuevos servicios sin recompilar.
2. **Bridge endpoint**: Camel preserva los detalles de la petición original al proxy al backend.
3. **Dead Letter Channel**: Manejo robusto de errores siguiendo patrones EIP.
4. **Configuración externalizada**: Los servicios backend se configuran via `application.properties`, facilitando el despliegue en diferentes entornos.
5. **Monitoreo integrado**: Actuator permite inspeccionar el estado de las rutas en producción.
6. **Testeable**: `CamelSpringBootTest` y `AdviceWith` permiten pruebas unitarias y de integración sin infraestructura externa.

Este enfoque es ideal cuando necesitas un gateway liviano sin la complejidad de soluciones como Kong, Zuul o Spring Cloud Gateway, especialmente si ya estás usando Camel en tu ecosistema de integración.

### Próximos Pasos

- Agregar **autenticación y autorización** con filtros Camel
- Implementar **rate limiting** con el patrón `Throttler`
- Añadir **transformación de mensajes** con Camel Expression Language
- Incorporar **circuit breaker** con Resilience4j
- Configurar **balanceo de carga** entre múltiples instancias de un servicio

---

**¿Has usado Apache Camel para construir gateways o sistemas de integración? ¡Comparte tu experiencia en los comentarios!**

*Código fuente completo disponible en: [GitHub](https://github.com/tu-usuario/camel-simple-gateway)*
