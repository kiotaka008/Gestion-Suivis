package com.gestionsuivis.backend.controller;

import com.gestionsuivis.backend.config.PaginationConfig;
import com.gestionsuivis.backend.dto.CompteurResponse;
import com.gestionsuivis.backend.dto.PageResponse;
import com.gestionsuivis.backend.dto.notification.NotificationResponse;
import com.gestionsuivis.backend.entity.TypeNotification;
import com.gestionsuivis.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Notifications de l'utilisateur connecté, filtres alignés sur
 * {@code NotificationsFilters} du frontend ({@code type}, {@code status=all|read|unread}).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationResponse> lister(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String type, @RequestParam(required = false) String status) {
        return service.lister(ParametresRequete.enumeration(type, TypeNotification::depuisJson, "type"),
                ParametresRequete.etatLecture(status),
                PaginationConfig.creer(page, perPage, null, null, Map.of(), Sort.by(Sort.Direction.DESC, "dateCreation")));
    }

    @GetMapping("/unread-count")
    public CompteurResponse nonLues() {
        return service.compterNonLues();
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> marquerCommeLue(@PathVariable UUID id) {
        service.marquerCommeLue(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> toutMarquerCommeLu() {
        service.marquerToutesCommeLues();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
