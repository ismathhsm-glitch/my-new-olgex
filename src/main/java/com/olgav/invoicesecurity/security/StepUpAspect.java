package com.olgav.invoicesecurity.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;


@Aspect
@Component
public class StepUpAspect {
    private final StepUpTokenConsumer tokenConsumer;

    public StepUpAspect(StepUpTokenConsumer tokenConsumer) {
        this.tokenConsumer = tokenConsumer;
    }

    @Around("@annotation(requiresStepUp)")
    public Object enforce(ProceedingJoinPoint point, RequiresStepUp requiresStepUp) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        String rawToken = org.springframework.web.context.request.RequestContextHolder
                .getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attributes
                ? attributes.getRequest().getHeader("X-StepUp-Token") : null;
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "X-StepUp-Token header is required");
        }
        tokenConsumer.consume(rawToken, userId, requiresStepUp.action());
        return point.proceed();
    }
}
