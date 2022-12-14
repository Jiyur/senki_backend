package com.abc.senki.repositories;

import com.abc.senki.model.entity.RatingCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.UUID;

@EnableJpaRepositories
public interface RatingCommentRepository extends JpaRepository<RatingCommentEntity, UUID> {
}
