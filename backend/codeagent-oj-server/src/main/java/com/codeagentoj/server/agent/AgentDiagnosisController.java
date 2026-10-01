package com.codeagentoj.server.agent;

import com.codeagentoj.server.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 前端诊断卡片的数据来源：只能查自己的提交（归属校验在 SQL 里完成）。 */
@RestController
@RequestMapping("/api/agent")
public class AgentDiagnosisController {
    private final AutoDiagnosisService diagnoses;

    public AgentDiagnosisController(AutoDiagnosisService diagnoses) {
        this.diagnoses = diagnoses;
    }

    @GetMapping("/submissions/{id}/diagnosis")
    public ApiResponse<AutoDiagnosisService.DiagnosisView> diagnosis(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return ApiResponse.ok(diagnoses.view(id, Long.parseLong(jwt.getSubject())));
    }
}
