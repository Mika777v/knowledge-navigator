package com.example.knavi.navigation.api;

import com.example.knavi.navigation.application.RecommendationService;
import com.example.knavi.navigation.application.RecommendationService.RecommendedProgram;
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

@Tag(name = "recommendations", description = "制度の推薦（この制度を見た人はこちらも）")
@RestController
@RequestMapping("/api/v1/programs")
class RecommendationController {

    private final RecommendationService service;

    RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @Operation(summary = "あわせて見られている制度・同じきっかけの制度（最大5件。見えない制度なら空）")
    @GetMapping("/{id}/recommendations")
    List<RecommendedProgram> recommendations(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.recommend(id, user);
    }
}
