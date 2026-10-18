package com.guilhermetonelli.carteiradigital.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Formato estável de resposta paginada (não expõe diretamente o Page do Spring Data). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
