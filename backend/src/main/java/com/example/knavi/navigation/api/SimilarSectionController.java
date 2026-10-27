package com.example.knavi.navigation.api;

import com.example.knavi.navigation.application.SimilarSectionService;
import com.example.knavi.navigation.application.SimilarSectionService.SimilarSection;
import com.example.knavi.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "similar-sections", description = "条に似た、ほかの規程の条（意味検索）")
@RestController
@RequestMapping("/api/v1/sections")
class SimilarSectionController {

    private final SimilarSectionService service;

    SimilarSectionController(SimilarSectionService service) {
        this.service = service;
    }

    @Operation(summary = "この条に似た、ほかの規程の条（最大5件。意味検索がオフ・見えない条なら空）")
    @GetMapping("/{id}/similar")
    List<SimilarSection> similar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.similar(id, user);
    }
}
