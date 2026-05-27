package com.prod.secureestatebackend.controller;

import com.prod.secureestatebackend.dto.AgentRequest;
import com.prod.secureestatebackend.dto.AgentResponse;
import com.prod.secureestatebackend.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        return ResponseEntity.ok(agentService.chat(request));
    }
}