package com.github.sgov.server.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.sgov.server.controller.util.ValidationReportSerializer;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;
import org.topbraid.shacl.validation.ValidationReport;

@Configuration
@SuppressWarnings("checkstyle:MissingJavadocType")
public class JacksonConfig {

    /**
     * Generates an object mapper. It is made RequestScope in order to use Accept-language inside
     * the serializer.
     *
     * @param request injected request to get Accept-language header.
     * @return Object mapper for serializing ValidationReport
     */
    @Bean
    @RequestScope
    public ObjectMapper objectMapper(@Autowired HttpServletRequest request) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        final SimpleModule module = new SimpleModule();
        module.addSerializer(ValidationReport.class, new ValidationReportSerializer(
            request.getLocale().toLanguageTag()));
        mapper.registerModule(module);
        return mapper;
    }
}
