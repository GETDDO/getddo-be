package com.getddo.db.drawing.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawPublicationExecutionEntity;
import com.getddo.db.drawing.entity.DrawPublicationExecutionId;
/** 공개 실행 근거의 불변 연결 저장소. */
public interface DrawPublicationExecutionJpaRepository extends JpaRepository<DrawPublicationExecutionEntity, DrawPublicationExecutionId> { }
