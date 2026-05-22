package dev.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.web.api.bm_a020.ApiExecutionHistoryInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiExecutionHistoryInterceptor apiExecutionHistoryInterceptor;

    public WebMvcConfig(ApiExecutionHistoryInterceptor apiExecutionHistoryInterceptor) {
        this.apiExecutionHistoryInterceptor = apiExecutionHistoryInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiExecutionHistoryInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/execution-history/**");
    }
}
