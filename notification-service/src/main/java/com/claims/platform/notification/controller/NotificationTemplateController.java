package com.claims.platform.notification.controller;

import com.claims.platform.notification.dto.request.UpdateTemplateRequest;
import com.claims.platform.notification.dto.response.ItemListResponse;
import com.claims.platform.notification.dto.response.TemplateResponse;
import com.claims.platform.notification.mapper.NotificationMapper;
import com.claims.platform.notification.service.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** N6: template administration, managers only. */
@RestController
@Tag(name = "Notification templates", description = "Wording of the messages, editable by managers")
public class NotificationTemplateController {
    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/admin/v1/templates")
    @Operation(summary = "List all templates", description = "N6.")
    @ApiResponse(responseCode = "200", description = "All templates")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a manager")
    public ItemListResponse<TemplateResponse> listTemplates() {
        return new ItemListResponse<>(templateService.listTemplates().stream()
                .map(NotificationMapper::toTemplateResponse).toList());
    }

    @PutMapping("/admin/v1/templates/{id}")
    @Operation(summary = "Change the wording of a template", description = "N6.")
    @ApiResponse(responseCode = "200", description = "The updated template")
    @ApiResponse(responseCode = "400", description = "Subject or body missing or too long (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a manager")
    @ApiResponse(responseCode = "404", description = "Unknown template (NOT_FOUND)")
    public TemplateResponse updateTemplate(@Parameter(description = "Template id") @PathVariable String id,
                                           @Valid @RequestBody UpdateTemplateRequest request) {
        return NotificationMapper.toTemplateResponse(templateService.updateTemplate(id, request.subject(), request.body()));
    }
}
